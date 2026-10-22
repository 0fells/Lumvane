package sniff

import (
	"slices"

	"github.com/sagernet/sing-box/common/ja3"
)

const (
	x25519Kyber768Draft00 uint16 = 0x11EC

	extensionRenegotiationInfo uint16 = 0xFF01
)

func isQUICGo(fingerprint *ja3.ClientHello) bool {
	if slices.Contains(fingerprint.EllipticCurves, x25519Kyber768Draft00) {
		return true
	}
	return slices.Contains(fingerprint.Extensions, extensionRenegotiationInfo)
}
