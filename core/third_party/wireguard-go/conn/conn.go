/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package conn

import (
	"errors"
	"fmt"
	"net/netip"
	"reflect"
	"runtime"
	"strings"
)

const (
	IdealBatchSize = 128
)

type ReceiveFunc func(packets [][]byte, sizes []int, eps []Endpoint) (n int, err error)

type Bind interface {
	Open(port uint16) (fns []ReceiveFunc, actualPort uint16, err error)

	Close() error

	SetMark(mark uint32) error

	Send(bufs [][]byte, ep Endpoint, offset int) error

	ParseEndpoint(s string) (Endpoint, error)

	BatchSize() int

	SetReservedForEndpoint(destination netip.AddrPort, reserved [3]byte)
}

type BindSocketToInterface interface {
	BindSocketToInterface4(interfaceIndex uint32, blackhole bool) error
	BindSocketToInterface6(interfaceIndex uint32, blackhole bool) error
}

type PeekLookAtSocketFd interface {
	PeekLookAtSocketFd4() (fd int, err error)
	PeekLookAtSocketFd6() (fd int, err error)
}

type Endpoint interface {
	ClearSrc()
	SrcToString() string
	DstToString() string
	DstToBytes() []byte
	DstIP() netip.Addr
	SrcIP() netip.Addr
}

type InitiationAwareEndpoint interface {
	InitiationMessagePublicKey(peerPublicKey [32]byte)
}

type PeerAwareEndpoint interface {
	FromPeer(peerPublicKey [32]byte)
}

var (
	ErrBindAlreadyOpen   = errors.New("bind is already open")
	ErrWrongEndpointType = errors.New("endpoint type does not correspond with bind type")
	ErrRebindRequired    = errors.New("bind must be reopened")
)

func (fn ReceiveFunc) PrettyName() string {
	name := runtime.FuncForPC(reflect.ValueOf(fn).Pointer()).Name()

	name = strings.TrimSuffix(name, "-fm")

	if idx := strings.LastIndexByte(name, '/'); idx != -1 {
		name = name[idx+1:]
	}
	for {
		var idx int
		for idx = len(name) - 1; idx >= 0; idx-- {
			if name[idx] < '0' || name[idx] > '9' {
				break
			}
		}
		if idx == len(name)-1 {
			break
		}
		const dotFunc = ".func"
		if !strings.HasSuffix(name[:idx+1], dotFunc) {
			break
		}
		name = name[:idx+1-len(dotFunc)]
	}
	if idx := strings.LastIndexByte(name, '.'); idx != -1 {
		name = name[idx+1:]
	}
	if name == "" {
		return fmt.Sprintf("%p", fn)
	}
	if strings.HasSuffix(name, "IPv4") {
		return "v4"
	}
	if strings.HasSuffix(name, "IPv6") {
		return "v6"
	}
	return name
}
