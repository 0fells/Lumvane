/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"sync"
	"time"

	"github.com/sagernet/wireguard-go/conn"
	"github.com/sagernet/wireguard-go/tun"
	"golang.org/x/crypto/chacha20poly1305"
	"golang.org/x/crypto/poly1305"
	"golang.org/x/net/ipv4"
	"golang.org/x/net/ipv6"
)

type QueueOutboundElement struct {
	buffer []byte

	packet  []byte
	nonce   uint64
	keypair *Keypair
	peer    *Peer

	padding uint32
}

type QueueOutboundElementsContainer struct {
	filling sync.WaitGroup
	elems   []*QueueOutboundElement
}

func (device *Device) NewOutboundElement() *QueueOutboundElement {
	elem := device.GetOutboundElement()
	elem.buffer = device.GetOutboundBuffer(MaxMessageSize)
	elem.nonce = 0
	elem.padding = device.paddings.transport.Load()

	return elem
}

func (elem *QueueOutboundElement) clearPointers() {
	elem.buffer = nil
	elem.packet = nil
	elem.keypair = nil
	elem.peer = nil
}

func (peer *Peer) SendKeepalive() {
	if len(peer.queue.staged) == 0 && peer.isRunning.Load() {
		elem := peer.device.NewOutboundElement()
		elemsContainer := peer.device.GetOutboundElementsContainer()
		elemsContainer.elems = append(elemsContainer.elems, elem)
		select {
		case peer.queue.staged <- elemsContainer:
			peer.queuedOutboundPackets.Add(1)
			peer.device.log.Verbosef("%v - Sending keepalive packet", peer)
		default:
			peer.device.PutOutboundBuffer(elem.buffer)
			peer.device.PutOutboundElement(elem)
			peer.device.PutOutboundElementsContainer(elemsContainer)
		}
	}
	peer.SendStagedPackets()
}

func (peer *Peer) SendPriorityMessage() {
	f := peer.device.priorityMsgFn.Load()
	if f == nil {
		return
	}
	keypair := peer.keypairs.Current()
	if keypair == nil || keypair.sendNonce.Load() >= RejectAfterMessages || time.Since(keypair.created) >= RejectAfterTime {
		return
	}

	msg := (*f)(peer.handshake.remoteStatic)
	if len(msg) == 0 {
		return
	}
	if len(msg) > MaxPriorityMessageContentSize {
		peer.device.log.Verbosef("%v - Failed to queue priority message due to size", peer)
		return
	}

	elem := peer.device.NewOutboundElement()
	elemsContainer := peer.device.GetOutboundElementsContainer()
	elemsContainer.elems = append(elemsContainer.elems, elem)
	packetQueued := false
	defer func() {
		if !packetQueued {
			peer.device.PutOutboundBuffer(elem.buffer)
			peer.device.PutOutboundElement(elem)
			peer.device.PutOutboundElementsContainer(elemsContainer)
		}
	}()

	const offset = MessageEncapsulatingTransportSize + MessageTransportHeaderSize
	n := copy(elem.buffer[offset:], msg)
	elem.packet = elem.buffer[offset : offset+n]
	elem.peer = peer
	elem.nonce = keypair.sendNonce.Add(1) - 1
	if elem.nonce >= RejectAfterMessages {
		keypair.sendNonce.Store(RejectAfterMessages)
		return
	}
	elem.keypair = keypair

	if peer.isRunning.Load() {
		elemsContainer.filling.Add(1)
		peer.queuedOutboundPackets.Add(1)
		peer.queue.outbound.c <- elemsContainer
		peer.device.queue.encryption.c <- elemsContainer
		packetQueued = true
	}
}

func (peer *Peer) SendHandshakeInitiation(isRetry bool) error {
	if !isRetry {
		peer.timers.handshakeAttempts.Store(0)
		peer.timers.maxHandshakeAttempts.Store(peer.device.maxHandshakeAttempts())
	}

	timeout := peer.device.rekeyMinTimeout()

	peer.handshake.mutex.RLock()
	if time.Since(peer.handshake.lastSentHandshake) < timeout {
		peer.handshake.mutex.RUnlock()
		return nil
	}
	peer.handshake.mutex.RUnlock()

	peer.handshake.mutex.Lock()
	if time.Since(peer.handshake.lastSentHandshake) < timeout {
		peer.handshake.mutex.Unlock()
		return nil
	}
	peer.handshake.lastSentHandshake = time.Now()
	peer.handshake.mutex.Unlock()

	peer.device.log.Verbosef("%v - Sending handshake initiation", peer)

	candidates := peer.resolveEndpoints()

	msg, err := peer.device.CreateMessageInitiation(peer)
	if err != nil {
		peer.device.log.Errorf("%v - Failed to create initiation message: %v", peer, err)
		return err
	}

	var sendBuffer [][]byte

	for _, ipacket := range peer.device.ipackets {
		if ipacket != nil {
			ibuf := make([]byte, MessageEncapsulatingTransportSize+ipacket.ObfuscatedLen(0))
			ipacket.Obfuscate(ibuf[MessageEncapsulatingTransportSize:], nil)
			sendBuffer = append(sendBuffer, ibuf)
		}
	}

	sendBuffer = append(sendBuffer, peer.device.JunkPackets()...)

	padding := peer.device.paddings.init.Load()
	buf := make([]byte, MessageEncapsulatingTransportSize+padding+MessageInitiationSize)
	crypt := buf[MessageEncapsulatingTransportSize : MessageEncapsulatingTransportSize+padding]
	rand.Read(crypt)

	packet := buf[MessageEncapsulatingTransportSize+padding:]
	_ = msg.marshal(packet)
	peer.cookieGenerator.AddMacs(packet)

	peer.timersAnyAuthenticatedPacketTraversal()
	peer.timersAnyAuthenticatedPacketSent()

	cip, err := peer.device.HeaderProtectionCipher(crypt)
	if err != nil {
		return err
	}
	if cip != nil {
		cip.XORKeyStream(packet, packet)
	}

	sendBuffer = append(sendBuffer, buf)

	if len(candidates) > 0 {
		err = peer.sendHandshakeBuffers(sendBuffer, candidates)
	} else {
		err = peer.SendBuffers(sendBuffer)
	}
	if err != nil {
		peer.device.log.Errorf("%v - Failed to send handshake initiation: %v", peer, err)
	}
	peer.timersHandshakeInitiated()

	return err
}

func (peer *Peer) SendHandshakeResponse() error {
	peer.handshake.mutex.Lock()
	peer.handshake.lastSentHandshake = time.Now()
	peer.handshake.mutex.Unlock()

	peer.device.log.Verbosef("%v - Sending handshake response", peer)

	response, err := peer.device.CreateMessageResponse(peer)
	if err != nil {
		peer.device.log.Errorf("%v - Failed to create response message: %v", peer, err)
		return err
	}

	padding := peer.device.paddings.response.Load()
	buf := make([]byte, MessageEncapsulatingTransportSize+padding+MessageResponseSize)
	crypt := buf[MessageEncapsulatingTransportSize : MessageEncapsulatingTransportSize+padding]
	rand.Read(crypt)

	packet := buf[MessageEncapsulatingTransportSize+padding:]
	_ = response.marshal(packet)
	peer.cookieGenerator.AddMacs(packet)

	err = peer.BeginSymmetricSession()
	if err != nil {
		peer.device.log.Errorf("%v - Failed to derive keypair: %v", peer, err)
		return err
	}

	peer.timersSessionDerived()
	peer.timersAnyAuthenticatedPacketTraversal()
	peer.timersAnyAuthenticatedPacketSent()

	cip, err := peer.device.HeaderProtectionCipher(crypt)
	if err != nil {
		return err
	}
	if cip != nil {
		cip.XORKeyStream(packet, packet)
	}

	err = peer.SendBuffers([][]byte{buf})
	if err != nil {
		peer.device.log.Errorf("%v - Failed to send handshake response: %v", peer, err)
	}
	return err
}

func (device *Device) SendHandshakeCookie(initiatingElem *QueueHandshakeElement) error {
	device.log.Verbosef("Sending cookie response for denied handshake message for %v", initiatingElem.endpoint.DstToString())

	sender := binary.LittleEndian.Uint32(initiatingElem.packet[4:8])
	reply, err := device.cookieChecker.CreateReply(initiatingElem.packet, sender, initiatingElem.endpoint.DstToBytes())
	if err != nil {
		device.log.Errorf("Failed to create cookie reply: %v", err)
		return err
	}

	reply.Type = device.headers.cookie.Load().PickOne()

	padding := device.paddings.cookie.Load()
	buf := make([]byte, MessageEncapsulatingTransportSize+padding+MessageCookieReplySize)
	crypt := buf[MessageEncapsulatingTransportSize : MessageEncapsulatingTransportSize+padding]
	rand.Read(crypt)

	packet := buf[MessageEncapsulatingTransportSize+padding:]
	_ = reply.marshal(packet)

	cip, err := device.HeaderProtectionCipher(crypt)
	if err != nil {
		return err
	}
	if cip != nil {
		cip.XORKeyStream(packet, packet)
	}

	device.net.bind.Send([][]byte{buf}, initiatingElem.endpoint, MessageEncapsulatingTransportSize)

	return nil
}

func (peer *Peer) keepKeyFreshSending() {
	keypair := peer.keypairs.Current()
	if keypair == nil {
		return
	}
	nonce := keypair.sendNonce.Load()
	if nonce > RekeyAfterMessages || (keypair.isInitiator && time.Since(keypair.created) > peer.device.keyRefreshTimeoutSending()) {
		peer.SendHandshakeInitiation(false)
	}
}

func (device *Device) RoutineReadFromTUN() {
	defer func() {
		device.log.Verbosef("Routine: TUN reader - stopped")
		device.state.stopping.Done()
		device.queue.encryption.wg.Done()
	}()

	device.log.Verbosef("Routine: TUN reader - started")

	var (
		batchSize   = device.BatchSize()
		readErr     error
		elems       = make([]*QueueOutboundElement, batchSize)
		bufs        = make([][]byte, batchSize)
		elemsByPeer = make(map[*Peer]*QueueOutboundElementsContainer, batchSize)
		count       = 0
		sizes       = make([]int, batchSize)
	)

	for i := range elems {
		elems[i] = device.NewOutboundElement()
		bufs[i] = elems[i].buffer[:]
	}

	defer func() {
		for _, elem := range elems {
			if elem != nil {
				device.PutOutboundBuffer(elem.buffer)
				device.PutOutboundElement(elem)
			}
		}
	}()

	for {
		padding := device.paddings.transport.Load()
		offset := MessageEncapsulatingTransportSize + int(padding) + MessageTransportHeaderSize

		count, readErr = device.tun.device.Read(bufs, sizes, offset)
		for i := 0; i < count; i++ {
			if sizes[i] < 1 {
				continue
			}

			elem := elems[i]
			elem.packet = bufs[i][offset : offset+sizes[i]]
			elem.padding = padding

			var peer *Peer
			switch elem.packet[0] >> 4 {
			case 4:
				if len(elem.packet) < ipv4.HeaderLen {
					continue
				}
				src := netip.AddrFrom4([4]byte(elem.packet[IPv4offsetSrc : IPv4offsetSrc+net.IPv4len]))
				dst := netip.AddrFrom4([4]byte(elem.packet[IPv4offsetDst : IPv4offsetDst+net.IPv4len]))
				peer = device.allowedips.LookupFromPacket(src, dst, elem.packet)

			case 6:
				if len(elem.packet) < ipv6.HeaderLen {
					continue
				}
				src := netip.AddrFrom16([16]byte(elem.packet[IPv6offsetSrc : IPv6offsetSrc+net.IPv6len]))
				dst := netip.AddrFrom16([16]byte(elem.packet[IPv6offsetDst : IPv6offsetDst+net.IPv6len]))
				peer = device.allowedips.LookupFromPacket(src, dst, elem.packet)

			default:
				device.log.Verbosef("Received packet with unknown IP version")
			}

			if peer == nil {
				continue
			}
			elemsForPeer, ok := elemsByPeer[peer]
			if !ok {
				elemsForPeer = device.GetOutboundElementsContainer()
				elemsByPeer[peer] = elemsForPeer
			}
			elemsForPeer.elems = append(elemsForPeer.elems, elem)
			elems[i] = device.NewOutboundElement()
			bufs[i] = elems[i].buffer[:]
		}

		for peer, elemsForPeer := range elemsByPeer {
			if peer.isRunning.Load() {
				peer.StagePackets(elemsForPeer)
				peer.SendStagedPackets()
			} else {
				for _, elem := range elemsForPeer.elems {
					device.PutOutboundBuffer(elem.buffer)
					device.PutOutboundElement(elem)
				}
				device.PutOutboundElementsContainer(elemsForPeer)
			}
			delete(elemsByPeer, peer)
		}

		if readErr != nil {
			if errors.Is(readErr, tun.ErrTooManySegments) {
				device.log.Verbosef("Dropped some packets from multi-segment read: %v", readErr)
				continue
			}
			if !device.isClosed() {
				if !errors.Is(readErr, os.ErrClosed) {
					device.log.Errorf("Failed to read packet from TUN device: %v", readErr)
				}
				go device.Close()
			}
			return
		}
	}
}

const maxQueuedInputPackets = 2048

func (device *Device) inputPacketPeer(destination []byte, packetSlices [][]byte) *Peer {
	var src, dst netip.Addr
	switch len(destination) {
	case net.IPv4len:
		dst = netip.AddrFrom4([4]byte(destination))
		var srcBytes [net.IPv4len]byte
		if !gatherPacketBytes(packetSlices, IPv4offsetSrc, srcBytes[:]) {
			return nil
		}
		src = netip.AddrFrom4(srcBytes)
	case net.IPv6len:
		dst = netip.AddrFrom16([16]byte(destination))
		var srcBytes [net.IPv6len]byte
		if !gatherPacketBytes(packetSlices, IPv6offsetSrc, srcBytes[:]) {
			return nil
		}
		src = netip.AddrFrom16(srcBytes)
	default:
		return nil
	}
	var ipPkt []byte
	if len(packetSlices) == 1 {
		ipPkt = packetSlices[0]
	}
	return device.allowedips.LookupFromPacket(src, dst, ipPkt)
}

func gatherPacketBytes(packetSlices [][]byte, offset int, destination []byte) bool {
	for _, packetSlice := range packetSlices {
		if offset >= len(packetSlice) {
			offset -= len(packetSlice)
			continue
		}
		n := copy(destination, packetSlice[offset:])
		destination = destination[n:]
		offset = 0
		if len(destination) == 0 {
			return true
		}
	}
	return false
}

func (device *Device) InputPacket(destination []byte, packetSlices [][]byte) {
	peer := device.inputPacketPeer(destination, packetSlices)
	if peer == nil {
		return
	}
	if peer.queuedOutboundPackets.Load() >= maxQueuedInputPackets {
		return
	}
	var totalLength int
	for _, packetSlice := range packetSlices {
		totalLength += len(packetSlice)
	}

	padding := device.paddings.transport.Load()
	allocLength := MessageEncapsulatingTransportSize + int(padding) + MessageTransportHeaderSize + totalLength + PaddingMultiple + chacha20poly1305.Overhead
	if allocLength > MaxMessageSize {
		return
	}
	elem := device.GetOutboundElement()
	elem.buffer = device.GetOutboundBuffer(allocLength)
	elem.nonce = 0
	elem.padding = padding
	packet := elem.buffer[MessageEncapsulatingTransportSize+int(padding)+MessageTransportHeaderSize:]
	var n int
	for _, packetSlice := range packetSlices {
		n += copy(packet[n:], packetSlice)
	}
	elem.packet = packet[:n]
	elemsForPeer := device.GetOutboundElementsContainer()
	if peer.isRunning.Load() {
		elemsForPeer.elems = append(elemsForPeer.elems, elem)
		peer.StagePackets(elemsForPeer)
		peer.SendStagedPackets()
	} else {
		device.PutOutboundBuffer(elem.buffer)
		device.PutOutboundElement(elem)
		device.PutOutboundElementsContainer(elemsForPeer)
	}
}

type InputPacketRef struct {
	Destination  []byte
	PacketSlices [][]byte
}

func (device *Device) InputPackets(packets []*InputPacketRef) []*InputPacketRef {
	var unmatched []*InputPacketRef
	elemsByPeer := make(map[*Peer][]*QueueOutboundElementsContainer, len(packets))
	for _, packetRef := range packets {
		peer := device.inputPacketPeer(packetRef.Destination, packetRef.PacketSlices)
		if peer == nil {
			unmatched = append(unmatched, packetRef)
			continue
		}
		if peer.queuedOutboundPackets.Load() >= maxQueuedInputPackets {
			continue
		}
		var totalLength int
		for _, packetSlice := range packetRef.PacketSlices {
			totalLength += len(packetSlice)
		}

		padding := device.paddings.transport.Load()
		allocLength := MessageEncapsulatingTransportSize + int(padding) + MessageTransportHeaderSize + totalLength + PaddingMultiple + chacha20poly1305.Overhead
		if allocLength > MaxMessageSize {
			continue
		}
		elem := device.GetOutboundElement()
		elem.buffer = device.GetOutboundBuffer(allocLength)
		elem.nonce = 0
		elem.padding = padding
		packet := elem.buffer[MessageEncapsulatingTransportSize+int(padding)+MessageTransportHeaderSize:]
		var n int
		for _, packetSlice := range packetRef.PacketSlices {
			n += copy(packet[n:], packetSlice)
		}
		elem.packet = packet[:n]
		containers := elemsByPeer[peer]
		if len(containers) == 0 || len(containers[len(containers)-1].elems) >= conn.IdealBatchSize {
			containers = append(containers, device.GetOutboundElementsContainer())
			elemsByPeer[peer] = containers
		}
		elemsForPeer := containers[len(containers)-1]
		elemsForPeer.elems = append(elemsForPeer.elems, elem)
	}
	for peer, containers := range elemsByPeer {
		if peer.isRunning.Load() {
			for _, elemsForPeer := range containers {
				peer.StagePackets(elemsForPeer)
			}
			peer.SendStagedPackets()
		} else {
			for _, elemsForPeer := range containers {
				for _, elem := range elemsForPeer.elems {
					device.PutOutboundBuffer(elem.buffer)
					device.PutOutboundElement(elem)
				}
				device.PutOutboundElementsContainer(elemsForPeer)
			}
		}
	}
	return unmatched
}

func (peer *Peer) StagePackets(elems *QueueOutboundElementsContainer) {
	peer.queuedOutboundPackets.Add(int32(len(elems.elems)))
	for {
		select {
		case peer.queue.staged <- elems:
			return
		default:
		}
		select {
		case tooOld := <-peer.queue.staged:
			peer.queuedOutboundPackets.Add(-int32(len(tooOld.elems)))
			for _, elem := range tooOld.elems {
				peer.device.PutOutboundBuffer(elem.buffer)
				peer.device.PutOutboundElement(elem)
			}
			peer.device.PutOutboundElementsContainer(tooOld)
		default:
		}
	}
}

func (peer *Peer) SendStagedPackets() {
top:
	if len(peer.queue.staged) == 0 || !peer.device.isUp() {
		return
	}

	keypair := peer.keypairs.Current()
	if keypair == nil || keypair.sendNonce.Load() >= RejectAfterMessages || time.Since(keypair.created) >= peer.device.keychainExpireTime() {
		peer.SendHandshakeInitiation(false)
		return
	}

	for {
		var elemsContainerOOO *QueueOutboundElementsContainer
		select {
		case elemsContainer := <-peer.queue.staged:
			i := 0
			for _, elem := range elemsContainer.elems {
				elem.peer = peer
				elem.nonce = keypair.sendNonce.Add(1) - 1
				if elem.nonce >= RejectAfterMessages {
					keypair.sendNonce.Store(RejectAfterMessages)
					if elemsContainerOOO == nil {
						elemsContainerOOO = peer.device.GetOutboundElementsContainer()
					}
					elemsContainerOOO.elems = append(elemsContainerOOO.elems, elem)
					continue
				} else {
					elemsContainer.elems[i] = elem
					i++
				}

				elem.keypair = keypair
			}
			elemsContainer.elems = elemsContainer.elems[:i]

			if elemsContainerOOO != nil {
				peer.queuedOutboundPackets.Add(-int32(len(elemsContainerOOO.elems)))
				peer.StagePackets(elemsContainerOOO)
			}

			if len(elemsContainer.elems) == 0 {
				peer.device.PutOutboundElementsContainer(elemsContainer)
				goto top
			}

			if peer.isRunning.Load() {
				elemsContainer.filling.Add(1)
				peer.queue.outbound.c <- elemsContainer
				peer.device.queue.encryption.c <- elemsContainer
			} else {
				peer.queuedOutboundPackets.Add(-int32(len(elemsContainer.elems)))
				for _, elem := range elemsContainer.elems {
					peer.device.PutOutboundBuffer(elem.buffer)
					peer.device.PutOutboundElement(elem)
				}
				peer.device.PutOutboundElementsContainer(elemsContainer)
			}

			if elemsContainerOOO != nil {
				goto top
			}
		default:
			return
		}
	}
}

func (peer *Peer) FlushStagedPackets() {
	for {
		select {
		case elemsContainer := <-peer.queue.staged:
			peer.queuedOutboundPackets.Add(-int32(len(elemsContainer.elems)))
			for _, elem := range elemsContainer.elems {
				peer.device.PutOutboundBuffer(elem.buffer)
				peer.device.PutOutboundElement(elem)
			}
			peer.device.PutOutboundElementsContainer(elemsContainer)
		default:
			return
		}
	}
}

func calculatePaddingSize(packetSize, mtu int) int {
	lastUnit := packetSize
	if mtu == 0 {
		return ((lastUnit + PaddingMultiple - 1) & ^(PaddingMultiple - 1)) - lastUnit
	}
	if lastUnit > mtu {
		lastUnit %= mtu
	}
	paddedSize := ((lastUnit + PaddingMultiple - 1) & ^(PaddingMultiple - 1))
	if paddedSize > mtu {
		paddedSize = mtu
	}
	return paddedSize - lastUnit
}

func (device *Device) randomPaddingAddition(packetSize, mtu int) int {
	addition := device.contentPaddingAddition.Load()

	if addition.IsZero() {
		return -1
	}

	add := int(addition.PickOne())
	if mtu != 0 {
		if packetSize > mtu {
			packetSize %= mtu
		}

		space := mtu - packetSize
		if add > space {
			add = space
		}
	}
	return add
}

func (device *Device) RoutineEncryption(id int) {
	var nonce [chacha20poly1305.NonceSize]byte

	defer device.log.Verbosef("Routine: encryption worker %d - stopped", id)
	device.log.Verbosef("Routine: encryption worker %d - started", id)

	for elemsContainer := range device.queue.encryption.c {
		for _, elem := range elemsContainer.elems {
			crypt := elem.buffer[MessageEncapsulatingTransportSize : MessageEncapsulatingTransportSize+elem.padding]
			rand.Read(crypt)

			headerOffset := MessageEncapsulatingTransportSize + int(elem.padding)
			header := elem.buffer[headerOffset : headerOffset+MessageTransportHeaderSize]

			fieldType := header[0:4]
			fieldReceiver := header[4:8]
			fieldNonce := header[8:16]

			binary.LittleEndian.PutUint32(fieldType, device.headers.transport.Load().PickOne())
			binary.LittleEndian.PutUint32(fieldReceiver, elem.keypair.remoteIndex)
			binary.LittleEndian.PutUint64(fieldNonce, elem.nonce)

			contentOffset := headerOffset + MessageTransportHeaderSize
			packetSize := len(elem.packet)
			mtu := int(device.tun.mtu.Load())

			paddingSize := device.randomPaddingAddition(packetSize, mtu)
			if paddingSize < 0 {
				paddingSize = calculatePaddingSize(packetSize, mtu)
			}

			if room := len(elem.buffer) - contentOffset - poly1305.TagSize - packetSize; paddingSize > room {
				paddingSize = room
				if paddingSize < 0 {
					paddingSize = 0
				}
			}
			elem.packet = elem.buffer[contentOffset : contentOffset+packetSize+paddingSize]
			for i := packetSize; i < len(elem.packet); i++ {
				elem.packet[i] = 0
			}

			binary.LittleEndian.PutUint64(nonce[4:], elem.nonce)
			elem.packet = elem.keypair.send.Seal(
				elem.buffer[MessageEncapsulatingTransportSize:headerOffset+MessageTransportHeaderSize],
				nonce[:],
				elem.packet,
				nil,
			)

			cip, err := device.HeaderProtectionCipher(crypt)
			if err != nil {
				device.log.Errorf("Routine: header protection failed - packet dropped: %v", err)
				elem.packet = nil
				continue
			}
			if cip != nil {
				cip.XORKeyStream(header, header)
			}

			elem.packet = elem.buffer[:MessageEncapsulatingTransportSize+len(elem.packet)]
		}
		elemsContainer.filling.Done()
	}
}

func (peer *Peer) RoutineSequentialSender(maxBatchSize int) {
	device := peer.device
	defer func() {
		defer device.log.Verbosef("%v - Routine: sequential sender - stopped", peer)
		peer.stopping.Done()
	}()
	device.log.Verbosef("%v - Routine: sequential sender - started", peer)

	bufs := make([][]byte, 0, max(maxBatchSize, conn.IdealBatchSize))

	for elemsContainer := range peer.queue.outbound.c {
		if elemsContainer == nil {
			return
		}
		peer.processOutboundContainer(elemsContainer, bufs[:0])
	}
}

func (peer *Peer) processOutboundContainer(elemsContainer *QueueOutboundElementsContainer, scratch [][]byte) {
	if len(scratch) != 0 || cap(scratch) == 0 {
		panic(fmt.Sprintf("processOutboundContainer: scratch must be empty with non-zero cap; got len=%d cap=%d",
			len(scratch), cap(scratch)))
	}
	if cap(scratch) < len(elemsContainer.elems) {
		panic(fmt.Sprintf("processOutboundContainer: scratch cap %d < elems %d",
			cap(scratch), len(elemsContainer.elems)))
	}

	device := peer.device
	defer device.PutOutboundElementsContainer(elemsContainer)

	elemsContainer.filling.Wait()

	if !peer.isRunning.Load() {
		peer.queuedOutboundPackets.Add(-int32(len(elemsContainer.elems)))
		for _, elem := range elemsContainer.elems {
			device.PutOutboundBuffer(elem.buffer)
			device.PutOutboundElement(elem)
		}
		return
	}

	dataSent := false
	for _, elem := range elemsContainer.elems {
		if elem.packet == nil {
			continue
		}
		if len(elem.packet[MessageEncapsulatingTransportSize:]) != MessageKeepaliveSize {
			dataSent = true
		}
		scratch = append(scratch, elem.packet)
	}

	peer.timersAnyAuthenticatedPacketTraversal()
	peer.timersAnyAuthenticatedPacketSent()

	err := peer.SendBuffers(scratch)
	if dataSent {
		peer.timersDataSent()
	}
	peer.queuedOutboundPackets.Add(-int32(len(elemsContainer.elems)))
	for _, elem := range elemsContainer.elems {
		device.PutOutboundBuffer(elem.buffer)
		device.PutOutboundElement(elem)
	}
	if err != nil {
		var errGSO conn.ErrUDPGSODisabled
		if errors.As(err, &errGSO) {
			device.log.Verbosef(err.Error())
			err = errGSO.RetryErr
		}
	}
	if err != nil {
		device.log.Errorf("%v - Failed to send data packets: %v", peer, err)
		return
	}

	peer.keepKeyFreshSending()
}
