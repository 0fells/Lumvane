/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"context"
	"errors"
	"net/netip"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"
	"github.com/sagernet/wireguard-go/conn"
	"github.com/sagernet/wireguard-go/ratelimiter"
	"github.com/sagernet/wireguard-go/rwcancel"
	"github.com/sagernet/wireguard-go/tun"
)

type Device struct {
	state struct {
		state atomic.Uint32

		stopping sync.WaitGroup

		sync.Mutex
	}

	net struct {
		stopping sync.WaitGroup
		sync.RWMutex
		bind          conn.Bind
		netlinkCancel *rwcancel.RWCancel
		port          uint16
		fwmark        uint32
		brokenRoaming bool
		rebindPending atomic.Bool
	}

	staticIdentity struct {
		sync.RWMutex
		privateKey NoisePrivateKey
		publicKey  NoisePublicKey
	}

	peers struct {
		sync.RWMutex
		keyMap     map[NoisePublicKey]*Peer
		lookupFunc PeerLookupFunc
	}

	peerStateFn        atomic.Pointer[PeerSessionStateFunc]
	priorityMsgFn      atomic.Pointer[PeerPriorityMessageFunc]
	endpointResolverFn atomic.Pointer[PeerEndpointResolverFunc]

	rate struct {
		underLoadUntil atomic.Int64
		limiter        ratelimiter.Ratelimiter
	}

	allowedips    AllowedIPs
	indexTable    IndexTable
	cookieChecker CookieChecker

	pool struct {
		inboundElementsContainer  *sync.Pool
		outboundElementsContainer *sync.Pool
		messageBuffers            *WaitPool
		inboundElements           *sync.Pool
		outboundElements          *sync.Pool
	}

	queue struct {
		encryption *outboundQueue
		decryption *inboundQueue
		handshake  *handshakeQueue
	}

	tun struct {
		device tun.Device
		mtu    atomic.Int32
	}

	ipcMutex     sync.RWMutex
	closed       chan struct{}
	log          *Logger
	pauseManager pause.Manager

	junk struct {
		min   atomic.Uint32
		max   atomic.Uint32
		count atomic.Uint32
	}

	headers struct {
		init      AtomicUintRange
		cookie    AtomicUintRange
		response  AtomicUintRange
		transport AtomicUintRange
	}

	paddings struct {
		init      atomic.Uint32
		response  atomic.Uint32
		cookie    atomic.Uint32
		transport atomic.Uint32
	}

	ipackets [5]*obfChain

	headerProtection struct {
		sync.RWMutex

		enabled atomic.Bool
		key     HeaderCipherKey
	}

	contentPaddingAddition AtomicUintRange

	timings struct {
		rekeyAfterTimeSec    AtomicUintRange
		rekeyTimeoutSec      AtomicUintRange
		rejectAfterTimeSec   AtomicUintRange
		keepaliveTimeoutSec  AtomicUintRange
		maxHandshakeAttempts AtomicUintRange
	}
}

type deviceState uint32

//go:generate go run golang.org/x/tools/cmd/stringer -type deviceState -trimprefix=deviceState
const (
	deviceStateDown deviceState = iota
	deviceStateUp
	deviceStateClosed
)

func (device *Device) deviceState() deviceState {
	return deviceState(device.state.state.Load())
}

func (device *Device) isClosed() bool {
	return device.deviceState() == deviceStateClosed
}

func (device *Device) isUp() bool {
	return device.deviceState() == deviceStateUp
}

func removePeerLocked(device *Device, peer *Peer, key NoisePublicKey) {
	device.allowedips.RemoveByPeer(peer)
	peer.Stop()

	delete(device.peers.keyMap, key)
}

func (device *Device) changeState(want deviceState) (err error) {
	device.state.Lock()
	defer device.state.Unlock()
	old := device.deviceState()
	if old == deviceStateClosed {
		device.log.Verbosef("Interface closed, ignored requested state %s", want)
		return nil
	}
	switch want {
	case old:
		return nil
	case deviceStateUp:
		device.state.state.Store(uint32(deviceStateUp))
		err = device.upLocked()
		if err == nil {
			break
		}
		fallthrough
	case deviceStateDown:
		device.state.state.Store(uint32(deviceStateDown))
		errDown := device.downLocked()
		if err == nil {
			err = errDown
		}
	}
	device.log.Verbosef("Interface state was %s, requested %s, now %s", old, want, device.deviceState())
	return
}

func (device *Device) upLocked() error {
	if err := device.BindUpdate(); err != nil {
		device.log.Errorf("Unable to update bind: %v", err)
		return err
	}

	device.ipcMutex.Lock()
	defer device.ipcMutex.Unlock()

	device.peers.RLock()
	peers := make([]*Peer, 0, len(device.peers.keyMap))
	for _, peer := range device.peers.keyMap {
		peers = append(peers, peer)
	}
	device.peers.RUnlock()
	for _, peer := range peers {
		peer.Start()
		if !peer.persistentKeepaliveInterval.Load().IsZero() {
			peer.SendKeepalive()
		}
	}
	return nil
}

func (device *Device) downLocked() error {
	err := device.BindClose()
	if err != nil {
		device.log.Errorf("Bind close failed: %v", err)
	}

	device.peers.RLock()
	for _, peer := range device.peers.keyMap {
		peer.Stop()
	}
	device.peers.RUnlock()
	return err
}

func (device *Device) Up() error {
	return device.changeState(deviceStateUp)
}

func (device *Device) Down() error {
	return device.changeState(deviceStateDown)
}

func (device *Device) IsUnderLoad() bool {
	now := time.Now()
	underLoad := len(device.queue.handshake.c) >= QueueHandshakeSize/8
	if underLoad {
		device.rate.underLoadUntil.Store(now.Add(UnderLoadAfterTime).UnixNano())
		return true
	}

	return device.rate.underLoadUntil.Load() > now.UnixNano()
}

func (device *Device) SetPrivateKey(sk NoisePrivateKey) error {
	device.staticIdentity.Lock()
	defer device.staticIdentity.Unlock()

	if sk.Equals(device.staticIdentity.privateKey) {
		return nil
	}

	device.peers.Lock()
	defer device.peers.Unlock()

	lockedPeers := make([]*Peer, 0, len(device.peers.keyMap))
	for _, peer := range device.peers.keyMap {
		peer.handshake.mutex.RLock()
		lockedPeers = append(lockedPeers, peer)
	}

	publicKey := sk.publicKey()
	for key, peer := range device.peers.keyMap {
		if peer.handshake.remoteStatic.Equals(publicKey) {
			peer.handshake.mutex.RUnlock()
			removePeerLocked(device, peer, key)
			peer.handshake.mutex.RLock()
		}
	}

	device.staticIdentity.privateKey = sk
	device.staticIdentity.publicKey = publicKey
	device.cookieChecker.Init(publicKey)

	expiredPeers := make([]*Peer, 0, len(device.peers.keyMap))
	for _, peer := range device.peers.keyMap {
		handshake := &peer.handshake
		handshake.precomputedStaticStatic, _ = device.staticIdentity.privateKey.sharedSecret(handshake.remoteStatic)
		expiredPeers = append(expiredPeers, peer)
	}

	for _, peer := range lockedPeers {
		peer.handshake.mutex.RUnlock()
	}
	for _, peer := range expiredPeers {
		peer.ExpireCurrentKeypairs()
	}

	return nil
}

func NewDevice(ctx context.Context, tunDevice tun.Device, bind conn.Bind, logger *Logger, workers int) *Device {
	device := new(Device)
	device.pauseManager = service.FromContext[pause.Manager](ctx)
	device.state.state.Store(uint32(deviceStateDown))
	device.closed = make(chan struct{})
	device.log = logger
	device.net.bind = bind
	device.tun.device = tunDevice
	mtu, err := device.tun.device.MTU()
	if err != nil {
		device.log.Errorf("Trouble determining MTU, assuming default: %v", err)
		mtu = DefaultMTU
	}
	device.tun.mtu.Store(int32(mtu))
	device.peers.keyMap = make(map[NoisePublicKey]*Peer)
	device.rate.limiter.Init()
	device.indexTable.Init()

	var rang UintRange
	rang.FromUint32(MessageInitiationType, MessageInitiationType)
	device.headers.init.Store(rang)
	rang.FromUint32(MessageResponseType, MessageResponseType)
	device.headers.response.Store(rang)
	rang.FromUint32(MessageCookieReplyType, MessageCookieReplyType)
	device.headers.cookie.Store(rang)
	rang.FromUint32(MessageTransportType, MessageTransportType)
	device.headers.transport.Store(rang)

	device.PopulatePools()

	device.queue.handshake = newHandshakeQueue()
	device.queue.encryption = newOutboundQueue()
	device.queue.decryption = newInboundQueue()

	if workers == 0 {
		workers = runtime.NumCPU()
	}
	device.state.stopping.Wait()
	device.queue.encryption.wg.Add(workers)
	for i := 0; i < workers; i++ {
		go device.RoutineEncryption(i + 1)
		go device.RoutineDecryption(i + 1)
		go device.RoutineHandshake(i + 1)
	}

	device.state.stopping.Add(1)
	device.queue.encryption.wg.Add(1)
	go device.RoutineReadFromTUN()
	go device.RoutineTUNEventReader()

	return device
}

func (device *Device) BatchSize() int {
	size := device.net.bind.BatchSize()
	dSize := device.tun.device.BatchSize()
	if size < dSize {
		size = dSize
	}
	return size
}

func (device *Device) AllowedIPs() *AllowedIPs {
	return &device.allowedips
}

func (device *Device) LookupPeer(pk NoisePublicKey) *Peer {
	device.peers.RLock()
	p, ok := device.peers.keyMap[pk]
	lookupFunc := device.peers.lookupFunc
	device.peers.RUnlock()
	if ok || lookupFunc == nil {
		return p
	}

	conf, ok := lookupFunc(pk)
	if !ok || conf == nil {
		return nil
	}

	p, err := device.NewPeer(pk)
	if err != nil {
		if errors.Is(err, errAddExistingPeer) {
			device.peers.RLock()
			defer device.peers.RUnlock()
			return device.peers.keyMap[pk]
		}
		device.log.Errorf("Failed to create peer: %v", err)
		return nil
	}
	p.SetAllowedIPs(conf.AllowedIPs)
	p.deleteOnIdle = true
	if conf.Endpoint != nil {
		p.SetEndpointFromPacket(conf.Endpoint)
	}
	p.Start()
	return p
}

func (device *Device) LookupActivePeer(pk NoisePublicKey) (_ *Peer, ok bool) {
	device.peers.RLock()
	defer device.peers.RUnlock()
	p, ok := device.peers.keyMap[pk]
	return p, ok
}

var errAddExistingPeer = errors.New("adding existing peer")

func (device *Device) RemovePeer(key NoisePublicKey) {
	device.peers.Lock()
	defer device.peers.Unlock()

	peer, ok := device.peers.keyMap[key]
	if ok {
		removePeerLocked(device, peer, key)
	}
}

func (device *Device) RemoveAllPeers() {
	device.peers.Lock()
	defer device.peers.Unlock()

	for key, peer := range device.peers.keyMap {
		removePeerLocked(device, peer, key)
	}

	device.peers.keyMap = make(map[NoisePublicKey]*Peer)
}

func (device *Device) RemoveMatchingPeers(shouldRemove func(NoisePublicKey) bool) (numRemoved int) {
	device.peers.Lock()
	defer device.peers.Unlock()

	for key, peer := range device.peers.keyMap {
		if shouldRemove(key) {
			removePeerLocked(device, peer, key)
			numRemoved++
		}
	}
	return numRemoved
}

type NewPeerConfig struct {
	AllowedIPs []netip.Prefix

	Endpoint conn.Endpoint
}

type PeerLookupFunc func(NoisePublicKey) (_ *NewPeerConfig, ok bool)

type PeerByIPPacketFunc func(src, dst netip.Addr, ipPkt []byte) (_ NoisePublicKey, ok bool)

type PeerSessionState uint8

const (
	PeerSessionNone PeerSessionState = iota

	PeerSessionHandshake

	PeerSessionEstablished

	PeerSessionExpired
)

type PeerSessionStateFunc func(peer NoisePublicKey, state PeerSessionState)

func (device *Device) SetPeerLookupFunc(f PeerLookupFunc) {
	device.peers.Lock()
	defer device.peers.Unlock()
	device.peers.lookupFunc = f
}

func (device *Device) SetPeerByIPPacketFunc(f PeerByIPPacketFunc) {
	device.allowedips.mu.Lock()
	defer device.allowedips.mu.Unlock()
	device.allowedips.peerByIPPacketFunc = f
	device.allowedips.device = device
}

func (device *Device) SetSessionStateFunc(f PeerSessionStateFunc) {
	if f == nil {
		device.peerStateFn.Store(nil)
		return
	}
	device.peerStateFn.Store(&f)
}

const MaxPriorityMessageContentSize = 512

type PeerPriorityMessageFunc func(peer NoisePublicKey) (msg []byte)

func (device *Device) SetPriorityMessageOnEstablishmentFunc(f PeerPriorityMessageFunc) {
	if f == nil {
		device.priorityMsgFn.Store(nil)
		return
	}
	device.priorityMsgFn.Store(&f)
}

type PeerEndpointResolverFunc func(peer NoisePublicKey) ([]conn.Endpoint, error)

func (device *Device) SetEndpointResolverFunc(f PeerEndpointResolverFunc) {
	if f == nil {
		device.endpointResolverFn.Store(nil)
		return
	}
	device.endpointResolverFn.Store(&f)
}

func (device *Device) Close() {
	device.state.Lock()
	defer device.state.Unlock()
	device.ipcMutex.Lock()
	defer device.ipcMutex.Unlock()
	if device.isClosed() {
		return
	}
	device.state.state.Store(uint32(deviceStateClosed))
	device.log.Verbosef("Device closing")

	device.tun.device.Close()
	device.downLocked()

	device.RemoveAllPeers()

	device.queue.encryption.wg.Done()
	device.queue.decryption.wg.Done()
	device.queue.handshake.wg.Done()
	device.state.stopping.Wait()

	device.rate.limiter.Close()

	device.log.Verbosef("Device closed")
	close(device.closed)
}

func (device *Device) Wait() chan struct{} {
	return device.closed
}

func (device *Device) SendKeepalivesToPeersWithCurrentKeypair() {
	if !device.isUp() {
		return
	}

	var peers []*Peer

	timeout := device.keychainExpireTime()

	device.peers.RLock()
	for _, peer := range device.peers.keyMap {
		peer.keypairs.RLock()
		sendKeepalive := peer.keypairs.current != nil && !peer.keypairs.current.created.Add(timeout).Before(time.Now())
		peer.keypairs.RUnlock()
		if sendKeepalive {
			peers = append(peers, peer)
		}
	}
	device.peers.RUnlock()
	for _, peer := range peers {
		peer.SendKeepalive()
	}
}

func closeBindLocked(device *Device) error {
	var err error
	netc := &device.net
	if netc.netlinkCancel != nil {
		netc.netlinkCancel.Cancel()
	}
	if netc.bind != nil {
		err = netc.bind.Close()
	}
	netc.stopping.Wait()
	return err
}

func (device *Device) Bind() conn.Bind {
	device.net.Lock()
	defer device.net.Unlock()
	return device.net.bind
}

func (device *Device) BindSetMark(mark uint32) error {
	device.net.Lock()
	defer device.net.Unlock()

	if device.net.fwmark == mark {
		return nil
	}

	device.net.fwmark = mark
	if device.isUp() && device.net.bind != nil {
		if err := device.net.bind.SetMark(mark); err != nil {
			return err
		}
	}

	device.peers.RLock()
	for _, peer := range device.peers.keyMap {
		peer.markEndpointSrcForClearing()
	}
	device.peers.RUnlock()

	return nil
}

func (device *Device) scheduleBindUpdate() {
	if !device.net.rebindPending.CompareAndSwap(false, true) {
		return
	}
	go func() {
		defer device.net.rebindPending.Store(false)
		err := device.BindUpdate()
		if err != nil {
			device.log.Errorf("Failed to reopen bind: %v", err)
		}
	}()
}

func (device *Device) BindUpdate() error {
	device.net.Lock()
	defer device.net.Unlock()

	if err := closeBindLocked(device); err != nil {
		return err
	}

	if !device.isUp() {
		return nil
	}

	var err error
	var recvFns []conn.ReceiveFunc
	netc := &device.net

	recvFns, netc.port, err = netc.bind.Open(netc.port)
	if err != nil {
		netc.port = 0
		return err
	}

	netc.netlinkCancel, err = device.startRouteListener(netc.bind)
	if err != nil {
		netc.bind.Close()
		netc.port = 0
		return err
	}

	if netc.fwmark != 0 {
		err = netc.bind.SetMark(netc.fwmark)
		if err != nil {
			return err
		}
	}

	device.peers.RLock()
	for _, peer := range device.peers.keyMap {
		peer.markEndpointSrcForClearing()
	}
	device.peers.RUnlock()

	device.net.stopping.Add(len(recvFns))
	device.queue.decryption.wg.Add(len(recvFns))
	device.queue.handshake.wg.Add(len(recvFns))
	batchSize := netc.bind.BatchSize()
	for _, fn := range recvFns {
		go device.RoutineReceiveIncoming(batchSize, fn)
	}

	device.log.Verbosef("UDP bind has been updated")
	return nil
}

func (device *Device) BindClose() error {
	device.net.Lock()
	err := closeBindLocked(device)
	device.net.Unlock()
	return err
}
