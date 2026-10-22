package tlsspoof

import (
	"net"
	"net/netip"

	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

const PlatformSupported = true

const (
	tcpRecvQueue = 1
	tcpSendQueue = 2
)

type linuxSpoofer struct {
	method      Method
	src         netip.AddrPort
	dst         netip.AddrPort
	rawFD       int
	rawSockAddr unix.Sockaddr
	sendNext    uint32
	receiveNext uint32
	timestamp   uint32
}

func newRawSpoofer(conn net.Conn, method Method) (rawSpoofer, error) {
	tcpConn, src, dst, err := tcpEndpoints(conn)
	if err != nil {
		return nil, err
	}
	fd, sockaddr, err := openLinuxRawSocket(dst)
	if err != nil {
		return nil, err
	}
	spoofer := &linuxSpoofer{
		method:      method,
		src:         src,
		dst:         dst,
		rawFD:       fd,
		rawSockAddr: sockaddr,
	}
	err = spoofer.loadSequenceNumbers(tcpConn)
	if err != nil {
		unix.Close(fd)
		return nil, err
	}
	return spoofer, nil
}

func openLinuxRawSocket(dst netip.AddrPort) (int, unix.Sockaddr, error) {
	if dst.Addr().Is4() {
		return openIPv4RawSocket(dst)
	}
	fd, err := unix.Socket(unix.AF_INET6, unix.SOCK_RAW, unix.IPPROTO_TCP)
	if err != nil {
		return -1, nil, E.Cause(err, "open AF_INET6 SOCK_RAW")
	}
	err = unix.SetsockoptInt(fd, unix.IPPROTO_IPV6, unix.IPV6_HDRINCL, 1)
	if err != nil {
		unix.Close(fd)
		return -1, nil, E.Cause(err, "set IPV6_HDRINCL")
	}

	sockaddr := &unix.SockaddrInet6{Addr: dst.Addr().As16()}
	return fd, sockaddr, nil
}

func (s *linuxSpoofer) loadSequenceNumbers(tcpConn *net.TCPConn) error {
	return control.Conn(tcpConn, func(raw uintptr) (err error) {
		fd := int(raw)

		if s.method == MethodWrongTimestamp {
			timestamp, err := unix.GetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_TIMESTAMP)
			if err != nil {
				return E.Cause(err, "read timestamp")
			}
			s.timestamp = uint32(timestamp)
		}

		err = unix.SetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_REPAIR, unix.TCP_REPAIR_ON)
		if err != nil {
			return E.Cause(err, "enter TCP_REPAIR (need CAP_NET_ADMIN)")
		}
		defer func() {
			offErr := unix.SetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_REPAIR, unix.TCP_REPAIR_OFF)
			if err == nil && offErr != nil {
				err = E.Cause(offErr, "leave TCP_REPAIR")
			}
		}()

		err = unix.SetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_REPAIR_QUEUE, tcpSendQueue)
		if err != nil {
			return E.Cause(err, "select TCP_SEND_QUEUE")
		}
		sendSequence, err := unix.GetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_QUEUE_SEQ)
		if err != nil {
			return E.Cause(err, "read send queue sequence")
		}
		err = unix.SetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_REPAIR_QUEUE, tcpRecvQueue)
		if err != nil {
			return E.Cause(err, "select TCP_RECV_QUEUE")
		}
		receiveSequence, err := unix.GetsockoptInt(fd, unix.IPPROTO_TCP, unix.TCP_QUEUE_SEQ)
		if err != nil {
			return E.Cause(err, "read recv queue sequence")
		}
		s.sendNext = uint32(sendSequence)
		s.receiveNext = uint32(receiveSequence)
		return nil
	})
}

func (s *linuxSpoofer) Inject(payload []byte) error {
	frame, err := buildSpoofFrame(s.method, s.src, s.dst, s.sendNext, s.receiveNext, s.timestamp, nil, payload)
	if err != nil {
		return err
	}
	err = unix.Sendto(s.rawFD, frame, 0, s.rawSockAddr)
	if err != nil {
		return E.Cause(err, "sendto raw socket")
	}
	return nil
}

func (s *linuxSpoofer) Close() error {
	if s.rawFD < 0 {
		return nil
	}
	err := unix.Close(s.rawFD)
	s.rawFD = -1
	return err
}
