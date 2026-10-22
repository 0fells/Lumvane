//go:build ios

/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

var (
	QueueStagedSize                   = 128
	QueueOutboundSize                 = 1024
	QueueInboundSize                  = 1024
	QueueHandshakeSize                = 1024
	PreallocatedBuffersPerPool uint32 = 1024
)

const MaxSegmentSize = 1700
