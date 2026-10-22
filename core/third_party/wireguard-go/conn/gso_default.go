//go:build !linux

/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package conn

func getGSOSize(control []byte) (int, error) {
	return 0, nil
}

func setGSOSize(control *[]byte, gsoSize uint16) {
}

const gsoControlSize = 0
