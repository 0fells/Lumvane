/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package tun

import (
	"os"
)

type Event int

const (
	EventUp = 1 << iota
	EventDown
	EventMTUUpdate
)

type Device interface {
	File() *os.File

	Read(bufs [][]byte, sizes []int, offset int) (n int, err error)

	Write(bufs [][]byte, offset int) (int, error)

	MTU() (int, error)

	Name() (string, error)

	Events() <-chan Event

	Close() error

	BatchSize() int
}

type GRODevice interface {
	Device

	DisableUDPGRO()

	DisableTCPGRO()
}
