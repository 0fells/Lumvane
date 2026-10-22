/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package replay

type block uint64

const (
	blockBitLog = 6
	blockBits   = 1 << blockBitLog
	ringBlocks  = 1 << 7
	windowSize  = (ringBlocks - 1) * blockBits
	blockMask   = ringBlocks - 1
	bitMask     = blockBits - 1
)

type Filter struct {
	last uint64
	ring [ringBlocks]block
}

func (f *Filter) Reset() {
	f.last = 0
	f.ring[0] = 0
}

func (f *Filter) ValidateCounter(counter, limit uint64) bool {
	if counter >= limit {
		return false
	}
	indexBlock := counter >> blockBitLog
	if counter > f.last {
		current := f.last >> blockBitLog
		diff := indexBlock - current
		if diff > ringBlocks {
			diff = ringBlocks
		}
		for i := current + 1; i <= current+diff; i++ {
			f.ring[i&blockMask] = 0
		}
		f.last = counter
	} else if f.last-counter > windowSize {
		return false
	}

	indexBlock &= blockMask
	indexBit := counter & bitMask
	old := f.ring[indexBlock]
	new := old | 1<<indexBit
	f.ring[indexBlock] = new
	return old != new
}
