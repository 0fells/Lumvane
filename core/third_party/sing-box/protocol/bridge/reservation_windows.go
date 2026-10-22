//go:build windows && (amd64 || 386)

package bridge

import (
	"encoding/binary"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

const sioAcquirePortReservation uint32 = 0x80000000 | 0x18000000 | 100

type portReservation struct {
	socket    windows.Handle
	startPort uint16
}

func acquirePortReservation(family, socketType, protocol int, count uint16) (*portReservation, error) {
	socket, err := windows.Socket(family, socketType, protocol)
	if err != nil {
		return nil, E.Cause(err, "create reservation socket")
	}

	var in [4]byte
	binary.LittleEndian.PutUint16(in[0:2], 0)
	binary.LittleEndian.PutUint16(in[2:4], count)

	var out [16]byte
	var returned uint32
	err = windows.WSAIoctl(socket, sioAcquirePortReservation,
		&in[0], uint32(len(in)), &out[0], uint32(len(out)), &returned, nil, 0)
	if err != nil {
		windows.Closesocket(socket)
		return nil, E.Cause(err, "acquire port reservation")
	}

	startPort := binary.BigEndian.Uint16(out[0:2])
	reservedCount := binary.LittleEndian.Uint16(out[2:4])
	if startPort == 0 || reservedCount < count {
		windows.Closesocket(socket)
		return nil, E.New("acquire port reservation: stack returned ", reservedCount, " of ", count, " ports")
	}
	return &portReservation{
		socket:    socket,
		startPort: startPort,
	}, nil
}

func (r *portReservation) Close() {
	if r == nil {
		return
	}
	windows.Closesocket(r.socket)
}
