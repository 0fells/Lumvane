package sniff

import (
	"context"
	"encoding/binary"
	"os"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
)

func NTP(ctx context.Context, metadata *adapter.InboundContext, packet []byte) error {
	pLen := len(packet)
	if pLen < 48 {
		return os.ErrInvalid
	}

	firstByte := packet[0]
	li := (firstByte >> 6) & 0x03
	vn := (firstByte >> 3) & 0x07
	mode := firstByte & 0x07

	if li > 3 {
		return os.ErrInvalid
	}

	if vn != 3 && vn != 4 {
		return os.ErrInvalid
	}

	if mode != 3 {
		return os.ErrInvalid
	}

	rootDelay := binary.BigEndian.Uint32(packet[4:8])
	rootDispersion := binary.BigEndian.Uint32(packet[8:12])

	if float64(rootDelay)/65536.0 > 16.0 {
		return os.ErrInvalid
	}
	if float64(rootDispersion)/65536.0 > 16.0 {
		return os.ErrInvalid
	}

	metadata.Protocol = C.ProtocolNTP

	return nil
}
