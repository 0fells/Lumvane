package tf

import (
	"context"
	"net"
	"time"

	"github.com/sagernet/sing/common/control"

	"golang.org/x/sys/unix"
)

func writeAndWaitAck(ctx context.Context, conn *net.TCPConn, payload []byte, fallbackDelay time.Duration) error {
	_, err := conn.Write(payload)
	if err != nil {
		return err
	}
	return control.Conn(conn, func(fd uintptr) error {
		start := time.Now()
		for {
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}
			unacked, err := unix.GetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_NWRITE)
			if err != nil {
				return err
			}
			if unacked == 0 {
				if time.Since(start) <= 20*time.Millisecond {
					time.Sleep(fallbackDelay)
				}
				return nil
			}
			time.Sleep(10 * time.Millisecond)
		}
	})
}
