/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"encoding/binary"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"sync"
	"time"

	"github.com/sagernet/wireguard-go/conn"
	"golang.org/x/crypto/chacha20poly1305"
	"golang.org/x/net/ipv4"
	"golang.org/x/net/ipv6"
)

type QueueHandshakeElement struct {
	msgType  uint32
	packet   []byte
	endpoint conn.Endpoint
	buffer   *[MaxMessageSize]byte
}

type QueueInboundElement struct {
	buffer   *[MaxMessageSize]byte
	packet   []byte
	counter  uint64
	keypair  *Keypair
	endpoint conn.Endpoint

	padding uint32
}

type QueueInboundElementsContainer struct {
	filling sync.WaitGroup
	elems   []*QueueInboundElement
}

func (elem *QueueInboundElement) clearPointers() {
	elem.buffer = nil
	elem.packet = nil
	elem.keypair = nil
	elem.endpoint = nil
}

func (peer *Peer) keepKeyFreshReceiving() {
	if peer.timers.sentLastMinuteHandshake.Load() {
		return
	}
	keypair := peer.keypairs.Current()
	if keypair != nil && keypair.isInitiator && time.Since(keypair.created) > peer.device.keyRefreshTimeoutReceiving() {
		peer.timers.sentLastMinuteHandshake.Store(true)
		peer.SendHandshakeInitiation(false)
	}
}

func (device *Device) RoutineReceiveIncoming(maxBatchSize int, recv conn.ReceiveFunc) {
	recvName := recv.PrettyName()
	defer func() {
		device.log.Verbosef("Routine: receive incoming %s - stopped", recvName)
		device.queue.decryption.wg.Done()
		device.queue.handshake.wg.Done()
		device.net.stopping.Done()
	}()

	device.log.Verbosef("Routine: receive incoming %s - started", recvName)

	var (
		bufsArrs    = make([]*[MaxMessageSize]byte, maxBatchSize)
		bufs        = make([][]byte, maxBatchSize)
		err         error
		sizes       = make([]int, maxBatchSize)
		count       int
		endpoints   = make([]conn.Endpoint, maxBatchSize)
		deathSpiral int
		elemsByPeer = make(map[*Peer]*QueueInboundElementsContainer, maxBatchSize)
		typeHashBuf [4]byte
	)

	for i := range bufsArrs {
		bufsArrs[i] = device.GetMessageBuffer()
		bufs[i] = bufsArrs[i][:]
	}

	defer func() {
		for i := 0; i < maxBatchSize; i++ {
			if bufsArrs[i] != nil {
				device.PutMessageBuffer(bufsArrs[i])
			}
		}
	}()

	for {
		count, err = recv(bufs, sizes, endpoints)
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return
			}
			device.log.Verbosef("Failed to receive %s packet: %v", recvName, err)
			if errors.Is(err, conn.ErrRebindRequired) {
				device.scheduleBindUpdate()
				return
			}
			if neterr, ok := err.(net.Error); ok && !neterr.Temporary() {
				return
			}
			if deathSpiral < 10 {
				deathSpiral++
				time.Sleep(time.Second / 3)
				continue
			}
			return
		}
		deathSpiral = 0

		for i, size := range sizes[:count] {
			if size < MinMessageSize {
				continue
			}

			packet := bufsArrs[i][:size]

			cip, err := device.HeaderProtectionCipher(packet)
			if err != nil {
				device.log.Verbosef("Failed to initialize header protection cipher: %v", err)
				continue
			}

			typeHashBuf = [4]byte{}
			typeHash := typeHashBuf[:]
			if cip != nil {
				cip.XORKeyStream(typeHash, typeHash)
			}

			msgType, padding := device.DeterminePacketTypeAndPadding(packet, typeHash)
			packet = packet[padding:]

			if cip != nil {
				applyHash(packet[:4], packet[:4], typeHash)
			}

			switch msgType {
			case MessageTransportType:

				if len(packet) < MessageTransportSize {
					continue
				}
				if cip != nil {
					cip.XORKeyStream(packet[4:MessageTransportHeaderSize], packet[4:MessageTransportHeaderSize])
				}

				receiver := binary.LittleEndian.Uint32(
					packet[MessageTransportOffsetReceiver:MessageTransportOffsetCounter],
				)
				value := device.indexTable.Lookup(receiver)
				keypair := value.keypair
				if keypair == nil {
					continue
				}

				if keypair.created.Add(device.keychainExpireTime()).Before(time.Now()) {
					continue
				}

				peer := value.peer
				elem := device.GetInboundElement()
				elem.packet = packet
				elem.buffer = bufsArrs[i]
				elem.keypair = keypair
				elem.endpoint = endpoints[i]
				elem.counter = 0
				elem.padding = padding

				elemsForPeer, ok := elemsByPeer[peer]
				if !ok {
					elemsForPeer = device.GetInboundElementsContainer()
					elemsByPeer[peer] = elemsForPeer
				}
				elemsForPeer.elems = append(elemsForPeer.elems, elem)
				bufsArrs[i] = device.GetMessageBuffer()
				bufs[i] = bufsArrs[i][:]
				continue

			case MessageInitiationType:
				if len(packet) != MessageInitiationSize {
					continue
				}
				if cip != nil {
					cip.XORKeyStream(packet[4:MessageInitiationSize], packet[4:MessageInitiationSize])
				}

			case MessageResponseType:
				if len(packet) != MessageResponseSize {
					continue
				}
				if cip != nil {
					cip.XORKeyStream(packet[4:MessageResponseSize], packet[4:MessageResponseSize])
				}

			case MessageCookieReplyType:
				if len(packet) != MessageCookieReplySize {
					continue
				}
				if cip != nil {
					cip.XORKeyStream(packet[4:MessageCookieReplySize], packet[4:MessageCookieReplySize])
				}

			default:
				device.log.Verbosef("Received message with unknown type")
				continue
			}

			select {
			case device.queue.handshake.c <- QueueHandshakeElement{
				msgType:  msgType,
				buffer:   bufsArrs[i],
				packet:   packet,
				endpoint: endpoints[i],
			}:
				bufsArrs[i] = device.GetMessageBuffer()
				bufs[i] = bufsArrs[i][:]
			default:
			}
		}
		for peer, elemsContainer := range elemsByPeer {
			if peer.isRunning.Load() {
				elemsContainer.filling.Add(1)
				peer.queue.inbound.c <- elemsContainer
				device.queue.decryption.c <- elemsContainer
			} else {
				for _, elem := range elemsContainer.elems {
					device.PutMessageBuffer(elem.buffer)
					device.PutInboundElement(elem)
				}
				device.PutInboundElementsContainer(elemsContainer)
			}
			delete(elemsByPeer, peer)
		}
	}
}

func (device *Device) RoutineDecryption(id int) {
	var nonce [chacha20poly1305.NonceSize]byte

	defer device.log.Verbosef("Routine: decryption worker %d - stopped", id)
	device.log.Verbosef("Routine: decryption worker %d - started", id)

	for elemsContainer := range device.queue.decryption.c {
		for _, elem := range elemsContainer.elems {
			counter := elem.packet[MessageTransportOffsetCounter:MessageTransportOffsetContent]
			content := elem.packet[MessageTransportOffsetContent:]

			var err error
			elem.counter = binary.LittleEndian.Uint64(counter)

			binary.LittleEndian.PutUint64(nonce[0x4:0xc], elem.counter)
			elem.packet, err = elem.keypair.receive.Open(
				content[:0],
				nonce[:],
				content,
				nil,
			)
			if err != nil {
				elem.packet = nil
			}
		}
		elemsContainer.filling.Done()
	}
}

func (device *Device) RoutineHandshake(id int) {
	defer func() {
		device.log.Verbosef("Routine: handshake worker %d - stopped", id)
		device.queue.encryption.wg.Done()
	}()
	device.log.Verbosef("Routine: handshake worker %d - started", id)

	for elem := range device.queue.handshake.c {
		switch elem.msgType {
		case MessageCookieReplyType:

			var reply MessageCookieReply
			err := reply.unmarshal(elem.packet)
			if err != nil {
				device.log.Verbosef("Failed to decode cookie reply")
				goto skip
			}

			entry := device.indexTable.Lookup(reply.Receiver)

			if entry.peer == nil {
				goto skip
			}

			if peer := entry.peer; peer.isRunning.Load() {
				device.log.Verbosef("Receiving cookie response from %s", elem.endpoint.DstToString())
				if !peer.cookieGenerator.ConsumeReply(&reply) {
					device.log.Verbosef("Could not decrypt invalid cookie response")
				}
			}

			goto skip

		case MessageInitiationType, MessageResponseType:

			if !device.cookieChecker.CheckMAC1(elem.packet) {
				device.log.Verbosef("Received packet with invalid mac1")
				goto skip
			}

			if device.IsUnderLoad() {
				if !device.cookieChecker.CheckMAC2(elem.packet, elem.endpoint.DstToBytes()) {
					device.SendHandshakeCookie(&elem)
					goto skip
				}

				if !device.rate.limiter.Allow(elem.endpoint.DstIP()) {
					goto skip
				}
			}

		default:
			device.log.Errorf("Invalid packet ended up in the handshake queue")
			goto skip
		}

		switch elem.msgType {
		case MessageInitiationType:

			var msg MessageInitiation
			err := msg.unmarshal(elem.packet)
			if err != nil {
				device.log.Errorf("Failed to decode initiation message")
				goto skip
			}

			msg.Type = elem.msgType

			peer := device.ConsumeMessageInitiation(&msg, elem.endpoint)
			if peer == nil {
				device.log.Verbosef("Received invalid initiation message from %s", elem.endpoint.DstToString())
				goto skip
			}

			peer.timersAnyAuthenticatedPacketTraversal()
			peer.timersAnyAuthenticatedPacketReceived()

			peer.SetEndpointFromPacket(elem.endpoint)

			device.log.Verbosef("%v - Received handshake initiation", peer)
			peer.rxBytes.Add(uint64(len(elem.packet)))

			peer.SendHandshakeResponse()

		case MessageResponseType:

			var msg MessageResponse
			err := msg.unmarshal(elem.packet)
			if err != nil {
				device.log.Errorf("Failed to decode response message")
				goto skip
			}

			msg.Type = elem.msgType

			peer := device.ConsumeMessageResponse(&msg)
			if peer == nil {
				device.log.Verbosef("Received invalid response message from %s", elem.endpoint.DstToString())
				goto skip
			}

			peer.SetEndpointFromPacket(elem.endpoint)

			device.log.Verbosef("%v - Received handshake response", peer)
			peer.rxBytes.Add(uint64(len(elem.packet)))

			peer.timersAnyAuthenticatedPacketTraversal()
			peer.timersAnyAuthenticatedPacketReceived()

			err = peer.BeginSymmetricSession()
			if err != nil {
				device.log.Errorf("%v - Failed to derive keypair: %v", peer, err)
				goto skip
			}

			peer.timersSessionDerived()
			peer.timersHandshakeComplete()
			peer.SendPriorityMessage()
			peer.SendKeepalive()
		}
	skip:
		device.PutMessageBuffer(elem.buffer)
	}
}

func (peer *Peer) RoutineSequentialReceiver(maxBatchSize int) {
	device := peer.device
	defer func() {
		device.log.Verbosef("%v - Routine: sequential receiver - stopped", peer)
		peer.stopping.Done()
	}()
	device.log.Verbosef("%v - Routine: sequential receiver - started", peer)

	bufs := make([][]byte, 0, maxBatchSize)

	for elemsContainer := range peer.queue.inbound.c {
		if elemsContainer == nil {
			return
		}
		peer.processInboundContainer(elemsContainer, bufs[:0])
	}
}

func (peer *Peer) processInboundContainer(elemsContainer *QueueInboundElementsContainer, scratch [][]byte) {
	if len(scratch) != 0 || cap(scratch) == 0 {
		panic(fmt.Sprintf("processInboundContainer: scratch must be empty with non-zero cap; got len=%d cap=%d",
			len(scratch), cap(scratch)))
	}
	if cap(scratch) < len(elemsContainer.elems) {
		panic(fmt.Sprintf("processInboundContainer: scratch cap %d < elems %d",
			cap(scratch), len(elemsContainer.elems)))
	}

	device := peer.device
	defer device.PutInboundElementsContainer(elemsContainer)

	elemsContainer.filling.Wait()
	elems := elemsContainer.elems

	validTailPacket := -1
	dataPacketReceived := false
	rxBytesLen := uint64(0)
	for i, elem := range elems {
		if elem.packet == nil {
			continue
		}

		if !elem.keypair.replayFilter.ValidateCounter(elem.counter, RejectAfterMessages) {
			continue
		}

		validTailPacket = i
		if peer.ReceivedWithKeypair(elem.keypair) {
			peer.SetEndpointFromPacket(elem.endpoint)
			peer.timersHandshakeComplete()
			peer.SendPriorityMessage()
			peer.SendStagedPackets()
		}
		if ep, ok := elem.endpoint.(conn.PeerAwareEndpoint); ok {
			ep.FromPeer(peer.handshake.remoteStatic)
		}
		rxBytesLen += uint64(len(elem.packet) + MinMessageSize)

		if len(elem.packet) == 0 {
			device.log.Verbosef("%v - Receiving keepalive packet", peer)
			continue
		}
		dataPacketReceived = true

		switch elem.packet[0] >> 4 {
		case 4:
			if len(elem.packet) < ipv4.HeaderLen {
				continue
			}
			field := elem.packet[IPv4offsetTotalLength : IPv4offsetTotalLength+2]
			length := binary.BigEndian.Uint16(field)
			if int(length) > len(elem.packet) || int(length) < ipv4.HeaderLen {
				continue
			}
			elem.packet = elem.packet[:length]
			src := elem.packet[IPv4offsetSrc : IPv4offsetSrc+net.IPv4len]
			srcAddr, _ := netip.AddrFromSlice(src)
			if !peer.AllowedPeerSourceIP(srcAddr) {
				device.log.Verbosef("IPv4 packet with disallowed source address from %v", peer)
				continue
			}

		case 6:
			if len(elem.packet) < ipv6.HeaderLen {
				continue
			}
			field := elem.packet[IPv6offsetPayloadLength : IPv6offsetPayloadLength+2]
			length := binary.BigEndian.Uint16(field)
			length += ipv6.HeaderLen
			if int(length) > len(elem.packet) {
				continue
			}
			elem.packet = elem.packet[:length]
			src := elem.packet[IPv6offsetSrc : IPv6offsetSrc+net.IPv6len]
			srcAddr, _ := netip.AddrFromSlice(src)
			if !peer.AllowedPeerSourceIP(srcAddr) {
				device.log.Verbosef("IPv6 packet with disallowed source address from %v", peer)
				continue
			}

		default:
			device.log.Verbosef("Packet with invalid IP version from %v", peer)
			continue
		}

		scratch = append(scratch, elem.buffer[elem.padding:int(elem.padding)+MessageTransportOffsetContent+len(elem.packet)])
	}

	peer.rxBytes.Add(rxBytesLen)
	if validTailPacket >= 0 {
		peer.SetEndpointFromPacket(elems[validTailPacket].endpoint)
		peer.keepKeyFreshReceiving()
		peer.timersAnyAuthenticatedPacketTraversal()
		peer.timersAnyAuthenticatedPacketReceived()
	}
	if dataPacketReceived {
		peer.timersDataReceived()
	}
	if len(scratch) > 0 {
		_, err := device.tun.device.Write(scratch, MessageTransportOffsetContent)
		if err != nil && !device.isClosed() {
			device.log.Errorf("Failed to write packets to TUN device: %v", err)
		}
	}
	for _, elem := range elems {
		device.PutMessageBuffer(elem.buffer)
		device.PutInboundElement(elem)
	}
}

func applyHash(dst, src, hash []byte) {
	for i := range dst {
		dst[i] = src[i] ^ hash[i]
	}
}

func (device *Device) DeterminePacketTypeAndPadding(packet []byte, typeHash []byte) (uint32, uint32) {
	var headerBytes [4]byte
	size := len(packet)

	if padding := device.paddings.transport.Load(); size >= int(padding)+MessageTransportHeaderSize {
		applyHash(headerBytes[:], packet[padding:padding+4], typeHash)
		if device.headers.transport.Load().Contains(binary.LittleEndian.Uint32(headerBytes[:])) {
			return MessageTransportType, padding
		}
	}

	if padding := device.paddings.init.Load(); size == int(padding)+MessageInitiationSize {
		applyHash(headerBytes[:], packet[padding:padding+4], typeHash)
		if device.headers.init.Load().Contains(binary.LittleEndian.Uint32(headerBytes[:])) {
			return MessageInitiationType, padding
		}
	}

	if padding := device.paddings.response.Load(); size == int(padding)+MessageResponseSize {
		applyHash(headerBytes[:], packet[padding:padding+4], typeHash)
		if device.headers.response.Load().Contains(binary.LittleEndian.Uint32(headerBytes[:])) {
			return MessageResponseType, padding
		}
	}

	if padding := device.paddings.cookie.Load(); size == int(padding)+MessageCookieReplySize {
		applyHash(headerBytes[:], packet[padding:padding+4], typeHash)
		if device.headers.cookie.Load().Contains(binary.LittleEndian.Uint32(headerBytes[:])) {
			return MessageCookieReplyType, padding
		}
	}

	return MessageUnknownType, 0
}
