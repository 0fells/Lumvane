/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"runtime"
	"sync"
)

type outboundQueue struct {
	c  chan *QueueOutboundElementsContainer
	wg sync.WaitGroup
}

func newOutboundQueue() *outboundQueue {
	q := &outboundQueue{
		c: make(chan *QueueOutboundElementsContainer, QueueOutboundSize),
	}
	q.wg.Add(1)
	go func() {
		q.wg.Wait()
		close(q.c)
	}()
	return q
}

type inboundQueue struct {
	c  chan *QueueInboundElementsContainer
	wg sync.WaitGroup
}

func newInboundQueue() *inboundQueue {
	q := &inboundQueue{
		c: make(chan *QueueInboundElementsContainer, QueueInboundSize),
	}
	q.wg.Add(1)
	go func() {
		q.wg.Wait()
		close(q.c)
	}()
	return q
}

type handshakeQueue struct {
	c  chan QueueHandshakeElement
	wg sync.WaitGroup
}

func newHandshakeQueue() *handshakeQueue {
	q := &handshakeQueue{
		c: make(chan QueueHandshakeElement, QueueHandshakeSize),
	}
	q.wg.Add(1)
	go func() {
		q.wg.Wait()
		close(q.c)
	}()
	return q
}

type autodrainingInboundQueue struct {
	c chan *QueueInboundElementsContainer
}

func newAutodrainingInboundQueue(device *Device) *autodrainingInboundQueue {
	q := &autodrainingInboundQueue{
		c: make(chan *QueueInboundElementsContainer, QueueInboundSize),
	}
	if device.needsInboundQueueFinalizer() {
		runtime.AddCleanup(q, device.flushInboundQueue, q.c)
	}
	return q
}

func (device *Device) needsInboundQueueFinalizer() bool {
	return device.pool.messageBuffers.hasAccounting()
}

func (device *Device) flushInboundQueue(c <-chan *QueueInboundElementsContainer) {
	for {
		select {
		case elemsContainer := <-c:
			elemsContainer.filling.Wait()
			for _, elem := range elemsContainer.elems {
				device.PutMessageBuffer(elem.buffer)
				device.PutInboundElement(elem)
			}
			device.PutInboundElementsContainer(elemsContainer)
		default:
			return
		}
	}
}

type autodrainingOutboundQueue struct {
	c chan *QueueOutboundElementsContainer
}

func newAutodrainingOutboundQueue(device *Device) *autodrainingOutboundQueue {
	q := &autodrainingOutboundQueue{
		c: make(chan *QueueOutboundElementsContainer, QueueOutboundSize),
	}
	if device.needsOutboundQueueFinalizer() {
		runtime.AddCleanup(q, device.flushOutboundQueue, q.c)
	}
	return q
}

func (device *Device) needsOutboundQueueFinalizer() bool {
	return device.pool.messageBuffers.hasAccounting()
}

func (device *Device) flushOutboundQueue(c <-chan *QueueOutboundElementsContainer) {
	for {
		select {
		case elemsContainer := <-c:
			elemsContainer.filling.Wait()
			for _, elem := range elemsContainer.elems {
				device.PutOutboundBuffer(elem.buffer)
				device.PutOutboundElement(elem)
			}
			device.PutOutboundElementsContainer(elemsContainer)
		default:
			return
		}
	}
}
