/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package conn

import (
	"github.com/sagernet/sing/common/control"
)

const socketBufferSize = 7 << 20

var controlFns []control.Func
