//go:build !linux

package lumcore

import (
	"errors"
	"net"

	C "github.com/sagernet/sing-box/constant"
)

func getTunnelName(fd int32) (string, error)  { return "", errors.New("tun is Android-only") }
func dup(fd int) (int, error)                 { return 0, errors.New("tun is Android-only") }
func toInterfaceType(t int32) C.InterfaceType { return C.InterfaceType(t) }
func linkFlags(rawFlags uint32) net.Flags     { return net.Flags(rawFlags) }
