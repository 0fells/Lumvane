//go:build !linux || android

/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package conn

import "net/netip"

func (e *StdNetEndpoint) SrcIP() netip.Addr {
	return netip.Addr{}
}

func (e *StdNetEndpoint) SrcIfidx() int32 {
	return 0
}

func (e *StdNetEndpoint) SrcToString() string {
	return ""
}

func getSrcFromControl(control []byte, ep *StdNetEndpoint) {
}

func setSrcControl(control *[]byte, ep *StdNetEndpoint) {
}

const stickyControlSize = 0

const StdNetSupportsStickySockets = false
