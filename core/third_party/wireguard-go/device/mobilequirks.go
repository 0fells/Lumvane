/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

func (device *Device) DisableSomeRoamingForBrokenMobileSemantics() {
	device.net.brokenRoaming = true
	device.peers.RLock()
	for _, peer := range device.peers.keyMap {
		peer.endpoint.Lock()
		peer.endpoint.disableRoaming = peer.endpoint.val != nil
		peer.endpoint.Unlock()
	}
	device.peers.RUnlock()
}
