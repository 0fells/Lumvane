//go:build windows && (amd64 || 386)

package tlsspoof

import (
	"errors"
	"net"
	"net/netip"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/common/windivert"
	"github.com/sagernet/sing-tun/gtcpip/header"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

const PlatformSupported = true

const closeGracePeriod = 2 * time.Second

type windowsSpoofer struct {
	method   Method
	src, dst netip.AddrPort
	divertH  *windivert.Handle

	fakeReady chan []byte
	done      chan struct{}
	closeOnce sync.Once
	runErr    atomic.Pointer[error]
}

func newRawSpoofer(conn net.Conn, method Method) (rawSpoofer, error) {
	_, src, dst, err := tcpEndpoints(conn)
	if err != nil {
		return nil, err
	}
	filter, err := windivert.OutboundTCP(src, dst)
	if err != nil {
		return nil, err
	}
	divertH, err := windivert.Open(filter, windivert.LayerNetwork, 0, 0)
	if err != nil {
		return nil, E.Cause(err, "tls_spoof: open WinDivert")
	}
	s := &windowsSpoofer{
		method:    method,
		src:       src,
		dst:       dst,
		divertH:   divertH,
		fakeReady: make(chan []byte, 1),
		done:      make(chan struct{}),
	}
	go s.run()
	return s, nil
}

func (s *windowsSpoofer) Inject(payload []byte) error {
	select {
	case s.fakeReady <- payload:
		return nil
	case <-s.done:
		if p := s.runErr.Load(); p != nil {
			return *p
		}
		return E.New("tls_spoof: spoofer closed before Inject")
	}
}

func (s *windowsSpoofer) Close() error {
	s.closeOnce.Do(func() {
		select {
		case <-s.done:
		case <-time.After(closeGracePeriod):

			s.divertH.Close()
			<-s.done
		}
	})
	if p := s.runErr.Load(); p != nil {
		return *p
	}
	return nil
}

func (s *windowsSpoofer) recordErr(err error) { s.runErr.Store(&err) }

func (s *windowsSpoofer) run() {
	defer close(s.done)
	defer s.divertH.Close()

	buf := make([]byte, windivert.MTUMax)
	for {
		n, addr, err := s.divertH.Recv(buf)
		if err != nil {
			if errors.Is(err, windows.ERROR_OPERATION_ABORTED) ||
				errors.Is(err, windows.ERROR_NO_DATA) {
				return
			}
			s.recordErr(E.Cause(err, "windivert recv"))
			return
		}
		pkt := buf[:n]
		seq, ack, tcpOptions, payloadLen, ok := parseTCPPacket(pkt, addr.IPv6())
		if !ok {
			_, sendErr := s.divertH.Send(pkt, &addr)
			if sendErr != nil {
				s.recordErr(E.Cause(sendErr, "windivert re-inject malformed"))
				return
			}
			s.recordErr(E.New("windivert received malformed packet matching spoof filter"))
			return
		}
		if payloadLen == 0 {
			_, err := s.divertH.Send(pkt, &addr)
			if err != nil {
				s.recordErr(E.Cause(err, "windivert re-inject empty"))
				return
			}
			continue
		}

		var fake []byte
		select {
		case fake = <-s.fakeReady:
		default:

			_, err := s.divertH.Send(pkt, &addr)
			if err != nil {
				s.recordErr(E.Cause(err, "windivert re-inject early data"))
				return
			}
			continue
		}

		var timestamp uint32
		if parsed := header.ParseTCPOptions(tcpOptions); parsed.TS {
			timestamp = parsed.TSVal
		}
		frame, err := buildSpoofFrame(s.method, s.src, s.dst, seq, ack, timestamp, tcpOptions, fake)
		if err != nil {
			s.recordErr(err)
			return
		}
		fakeAddr := addr

		fakeAddr.SetIPChecksum(true)
		fakeAddr.SetTCPChecksum(true)
		_, err = s.divertH.Send(frame, &fakeAddr)
		if err != nil {
			s.recordErr(E.Cause(err, "windivert inject fake"))
			return
		}
		_, err = s.divertH.Send(pkt, &addr)
		if err != nil {
			s.recordErr(E.Cause(err, "windivert re-inject real"))
			return
		}
		return
	}
}

func parseTCPPacket(pkt []byte, isV6 bool) (seq, ack uint32, options []byte, payloadLen int, ok bool) {
	if isV6 {
		if len(pkt) < header.IPv6MinimumSize+header.TCPMinimumSize {
			return 0, 0, nil, 0, false
		}
		ip := header.IPv6(pkt)
		if ip.TransportProtocol() != header.TCPProtocolNumber {
			return 0, 0, nil, 0, false
		}
		tcp := header.TCP(pkt[header.IPv6MinimumSize:])
		tcpHdr := int(tcp.DataOffset())
		if tcpHdr < header.TCPMinimumSize || header.IPv6MinimumSize+tcpHdr > len(pkt) {
			return 0, 0, nil, 0, false
		}
		total := header.IPv6MinimumSize + int(ip.PayloadLength())
		if total == header.IPv6MinimumSize || total > len(pkt) {
			total = len(pkt)
		}
		if total < header.IPv6MinimumSize+tcpHdr {
			return 0, 0, nil, 0, false
		}
		return tcp.SequenceNumber(), tcp.AckNumber(), slices.Clone(tcp.Options()),
			total - header.IPv6MinimumSize - tcpHdr, true
	}
	if len(pkt) < header.IPv4MinimumSize+header.TCPMinimumSize {
		return 0, 0, nil, 0, false
	}
	ip := header.IPv4(pkt)
	if ip.Protocol() != uint8(header.TCPProtocolNumber) {
		return 0, 0, nil, 0, false
	}
	ihl := int(ip.HeaderLength())

	if ihl < header.IPv4MinimumSize || ihl+header.TCPMinimumSize > len(pkt) {
		return 0, 0, nil, 0, false
	}
	tcp := header.TCP(pkt[ihl:])
	tcpHdr := int(tcp.DataOffset())
	if tcpHdr < header.TCPMinimumSize || ihl+tcpHdr > len(pkt) {
		return 0, 0, nil, 0, false
	}
	total := int(ip.TotalLength())
	if total == 0 || total > len(pkt) {
		total = len(pkt)
	}
	if total < ihl+tcpHdr {
		return 0, 0, nil, 0, false
	}
	return tcp.SequenceNumber(), tcp.AckNumber(), slices.Clone(tcp.Options()),
		total - ihl - tcpHdr, true
}
