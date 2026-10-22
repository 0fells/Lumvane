/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"container/list"
	"errors"
	"net/netip"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/wireguard-go/conn"
)

type Peer struct {
	isRunning         atomic.Bool
	keypairs          Keypairs
	handshake         Handshake
	device            *Device
	stopping          sync.WaitGroup
	txBytes           atomic.Uint64
	rxBytes           atomic.Uint64
	lastHandshakeNano atomic.Int64

	sessionState struct {
		sync.Mutex
		current        PeerSessionState
		sessionExpires time.Time
	}

	queuedOutboundPackets atomic.Int32

	deleteOnIdle bool

	endpoint struct {
		sync.Mutex
		val            conn.Endpoint
		candidates     []conn.Endpoint
		clearSrcOnTx   bool
		disableRoaming bool
	}

	timers struct {
		retransmitHandshake     *Timer
		sendKeepalive           *Timer
		newHandshake            *Timer
		sessionExpired          *Timer
		zeroKeyMaterial         *Timer
		persistentKeepalive     *Timer
		handshakeAttempts       atomic.Uint32
		maxHandshakeAttempts    atomic.Uint32
		needAnotherKeepalive    atomic.Bool
		sentLastMinuteHandshake atomic.Bool
	}

	state struct {
		sync.Mutex

		allowedIPs []netip.Prefix

		testAllowedIP atomic.Pointer[func(netip.Addr) bool]
	}

	queue struct {
		staged   chan *QueueOutboundElementsContainer
		outbound *autodrainingOutboundQueue
		inbound  *autodrainingInboundQueue
	}

	cookieGenerator             CookieGenerator
	trieEntries                 list.List
	persistentKeepaliveInterval AtomicUintRange
}

func (device *Device) NewPeer(pk NoisePublicKey) (*Peer, error) {
	if device.isClosed() {
		return nil, errors.New("device closed")
	}

	device.staticIdentity.RLock()
	defer device.staticIdentity.RUnlock()

	device.peers.Lock()
	defer device.peers.Unlock()

	if len(device.peers.keyMap) >= MaxPeers {
		return nil, errors.New("too many peers")
	}

	peer := new(Peer)

	peer.cookieGenerator.Init(pk)
	peer.device = device
	peer.queue.outbound = newAutodrainingOutboundQueue(device)
	peer.queue.inbound = newAutodrainingInboundQueue(device)
	peer.queue.staged = make(chan *QueueOutboundElementsContainer, QueueStagedSize)

	_, ok := device.peers.keyMap[pk]
	if ok {
		return nil, errAddExistingPeer
	}

	handshake := &peer.handshake
	handshake.mutex.Lock()
	handshake.precomputedStaticStatic, _ = device.staticIdentity.privateKey.sharedSecret(pk)
	handshake.remoteStatic = pk
	handshake.mutex.Unlock()

	peer.endpoint.Lock()
	peer.endpoint.val = nil
	peer.endpoint.disableRoaming = false
	peer.endpoint.clearSrcOnTx = false
	peer.endpoint.Unlock()

	peer.timersInit()

	device.peers.keyMap[pk] = peer

	return peer, nil
}

func (p *Peer) SetAllowedIPs(allowedIPs []netip.Prefix) {
	p.state.Lock()
	defer p.state.Unlock()

	if slices.Equal(p.state.allowedIPs, allowedIPs) {
		return
	}
	p.device.allowedips.setPeerPrefixes(p, allowedIPs)

	allowedIPs = slices.Clone(allowedIPs)
	p.state.allowedIPs = allowedIPs

	f := mkIPInCIDRsTestFunc(allowedIPs)
	p.state.testAllowedIP.Store(&f)
}

func (peer *Peer) SendBuffers(buffers [][]byte) error {
	peer.device.net.RLock()
	defer peer.device.net.RUnlock()

	if peer.device.isClosed() {
		return nil
	}

	peer.endpoint.Lock()
	endpoint := peer.endpoint.val
	if endpoint == nil {
		peer.endpoint.Unlock()
		return errors.New("no known endpoint for peer")
	}
	if peer.endpoint.clearSrcOnTx {
		endpoint.ClearSrc()
		peer.endpoint.clearSrcOnTx = false
	}
	peer.endpoint.Unlock()

	err := peer.device.net.bind.Send(buffers, endpoint, MessageEncapsulatingTransportSize)
	if err == nil {
		var totalLen uint64
		for _, b := range buffers {
			totalLen += uint64(len(b))
		}
		peer.txBytes.Add(totalLen)
	} else if errors.Is(err, conn.ErrRebindRequired) {
		peer.device.scheduleBindUpdate()
	}
	return err
}

func (peer *Peer) String() string {
	src := peer.handshake.remoteStatic
	b64 := func(input byte) byte {
		return input + 'A' + byte(((25-int(input))>>8)&6) - byte(((51-int(input))>>8)&75) - byte(((61-int(input))>>8)&15) + byte(((62-int(input))>>8)&3)
	}
	b := []byte("peer(____…____)")
	const first = len("peer(")
	const second = len("peer(____…")
	b[first+0] = b64((src[0] >> 2) & 63)
	b[first+1] = b64(((src[0] << 4) | (src[1] >> 4)) & 63)
	b[first+2] = b64(((src[1] << 2) | (src[2] >> 6)) & 63)
	b[first+3] = b64(src[2] & 63)
	b[second+0] = b64(src[29] & 63)
	b[second+1] = b64((src[30] >> 2) & 63)
	b[second+2] = b64(((src[30] << 4) | (src[31] >> 4)) & 63)
	b[second+3] = b64((src[31] << 2) & 63)
	return string(b)
}

func (peer *Peer) Start() {
	if peer.device.isClosed() {
		return
	}

	peer.state.Lock()
	defer peer.state.Unlock()

	if peer.isRunning.Load() {
		return
	}

	device := peer.device
	device.log.Verbosef("%v - Starting", peer)

	peer.stopping.Wait()
	peer.stopping.Add(2)
	peer.queuedOutboundPackets.Store(0)

	peer.handshake.mutex.Lock()
	peer.handshake.lastSentHandshake = time.Now().Add(-(peer.device.rekeyMinTimeout() + time.Second))
	peer.handshake.mutex.Unlock()

	peer.device.queue.encryption.wg.Add(1)

	peer.timersStart()

	device.flushInboundQueue(peer.queue.inbound.c)
	device.flushOutboundQueue(peer.queue.outbound.c)

	batchSize := peer.device.BatchSize()
	go peer.RoutineSequentialSender(batchSize)
	go peer.RoutineSequentialReceiver(batchSize)

	peer.isRunning.Store(true)

	if peer.deleteOnIdle {
		peer.timers.zeroKeyMaterial.Mod(RejectAfterTime * 3)
	}
}

func (peer *Peer) ZeroAndFlushAll() {
	device := peer.device
	if peer.timers.sessionExpired != nil {
		peer.timers.sessionExpired.Del()
	}

	keypairs := &peer.keypairs
	keypairs.Lock()
	device.DeleteKeypair(keypairs.previous)
	device.DeleteKeypair(keypairs.current)
	device.DeleteKeypair(keypairs.next.Load())
	keypairs.previous = nil
	keypairs.current = nil
	keypairs.next.Store(nil)
	keypairs.Unlock()

	handshake := &peer.handshake
	handshake.mutex.Lock()
	device.indexTable.Delete(handshake.localIndex)
	handshake.Clear()
	handshake.mutex.Unlock()

	peer.FlushStagedPackets()

	peer.sessionState.Lock()
	peer.sessionState.sessionExpires = time.Time{}
	peer.noteSessionStateLocked(PeerSessionNone)
	peer.sessionState.Unlock()
}

func (peer *Peer) ExpireCurrentKeypairs() {
	handshake := &peer.handshake
	handshake.mutex.Lock()
	peer.device.indexTable.Delete(handshake.localIndex)
	handshake.Clear()
	peer.handshake.lastSentHandshake = time.Now().Add(-(peer.device.rekeyMinTimeout() + time.Second))
	handshake.mutex.Unlock()

	keypairs := &peer.keypairs
	keypairs.Lock()
	if keypairs.current != nil {
		keypairs.current.sendNonce.Store(RejectAfterMessages)
	}
	if next := keypairs.next.Load(); next != nil {
		next.sendNonce.Store(RejectAfterMessages)
	}
	keypairs.Unlock()

	peer.sessionState.Lock()
	peer.sessionState.sessionExpires = time.Time{}
	peer.noteSessionStateLocked(PeerSessionExpired)
	peer.sessionState.Unlock()
}

func (peer *Peer) Stop() {
	peer.state.Lock()
	defer peer.state.Unlock()

	if !peer.isRunning.Swap(false) {
		return
	}

	peer.device.log.Verbosef("%v - Stopping", peer)

	peer.timersStop()

	peer.queue.inbound.c <- nil
	peer.queue.outbound.c <- nil
	peer.stopping.Wait()
	peer.device.queue.encryption.wg.Done()

	peer.ZeroAndFlushAll()
}

func (peer *Peer) noteSessionState(state PeerSessionState) {
	peer.sessionState.Lock()
	defer peer.sessionState.Unlock()
	peer.noteSessionStateLocked(state)
}

func (peer *Peer) noteSessionStateLocked(state PeerSessionState) {
	if peer.sessionState.current == state {
		return
	}
	peer.sessionState.current = state
	if f := peer.device.peerStateFn.Load(); f != nil {
		(*f)(peer.handshake.remoteStatic, state)
	}
}

func (peer *Peer) noteSessionHandshakeStarted() {
	peer.sessionState.Lock()
	defer peer.sessionState.Unlock()
	if peer.sessionState.current == PeerSessionEstablished {
		return
	}
	peer.noteSessionStateLocked(PeerSessionHandshake)
}

func (peer *Peer) noteSessionHandshakeStopped() {
	peer.sessionState.Lock()
	defer peer.sessionState.Unlock()
	state := PeerSessionNone
	if peer.hasKeyMaterial() {
		state = PeerSessionExpired
	}
	peer.noteSessionStateLocked(state)
}

func (peer *Peer) hasKeyMaterial() bool {
	keypairs := &peer.keypairs
	keypairs.RLock()
	defer keypairs.RUnlock()
	return keypairs.previous != nil || keypairs.current != nil || keypairs.next.Load() != nil
}

func (peer *Peer) SetEndpointFromPacket(endpoint conn.Endpoint) {
	peer.endpoint.Lock()
	defer peer.endpoint.Unlock()
	if peer.endpoint.disableRoaming {
		return
	}
	peer.endpoint.clearSrcOnTx = false
	peer.endpoint.val = endpoint
}

func (peer *Peer) resolveEndpoints() []conn.Endpoint {
	resolver := peer.device.endpointResolverFn.Load()
	if resolver == nil {
		return nil
	}
	resolved, err := (*resolver)(peer.handshake.remoteStatic)
	peer.endpoint.Lock()
	defer peer.endpoint.Unlock()
	if err != nil {
		peer.device.log.Errorf("%v - Failed to resolve endpoints: %v", peer, err)
	} else if len(resolved) > 0 {
		peer.endpoint.candidates = resolved
	}
	return peer.endpoint.candidates
}

func (peer *Peer) sendHandshakeBuffers(buffers [][]byte, candidates []conn.Endpoint) error {
	peer.device.net.RLock()
	defer peer.device.net.RUnlock()

	if peer.device.isClosed() {
		return nil
	}

	peer.endpoint.Lock()
	current := peer.endpoint.val
	if current != nil && peer.endpoint.clearSrcOnTx {
		current.ClearSrc()
		peer.endpoint.clearSrcOnTx = false
	}
	peer.endpoint.Unlock()

	endpoints := make([]conn.Endpoint, 0, len(candidates)+1)
	if current != nil {
		endpoints = append(endpoints, current)
	}
	for _, candidate := range candidates {
		duplicate := slices.ContainsFunc(endpoints, func(endpoint conn.Endpoint) bool {
			return endpoint.DstToString() == candidate.DstToString()
		})
		if !duplicate {
			endpoints = append(endpoints, candidate)
		}
	}
	if len(endpoints) == 0 {
		return errors.New("no known endpoint for peer")
	}

	var totalLen uint64
	for _, buffer := range buffers {
		totalLen += uint64(len(buffer))
	}

	var firstErr error
	sent := false
	for _, endpoint := range endpoints {
		err := peer.device.net.bind.Send(buffers, endpoint, MessageEncapsulatingTransportSize)
		if err == nil {
			sent = true
			peer.txBytes.Add(totalLen)
		} else if firstErr == nil {
			firstErr = err
		}
	}
	if sent {
		return nil
	}
	return firstErr
}

func (peer *Peer) markEndpointSrcForClearing() {
	peer.endpoint.Lock()
	defer peer.endpoint.Unlock()
	if peer.endpoint.val == nil {
		return
	}
	peer.endpoint.clearSrcOnTx = true
}
