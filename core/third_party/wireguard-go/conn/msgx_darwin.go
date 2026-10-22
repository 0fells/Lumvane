//go:build darwin && !ios

package conn

import (
	"fmt"
	"io"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"syscall"
	"unsafe"

	M "github.com/sagernet/sing/common/metadata"
	"golang.org/x/net/ipv6"
	"golang.org/x/sys/unix"
)

const supportsMsgX = true

const msgXBatchSize = IdealBatchSize

type msghdrX struct {
	Msg     unix.Msghdr
	DataLen uint32
}

type msgXState struct {
	singlePeer  atomic.Bool
	connected4  atomic.Bool
	connected6  atomic.Bool
	endpoint    atomic.Pointer[StdNetEndpoint]
	connectLock sync.Mutex
}

func (m *msgXState) reset() {
	m.connected4.Store(false)
	m.connected6.Store(false)
	m.endpoint.Store(nil)
}

func (m *msgXState) connectedFlag(isV6 bool) *atomic.Bool {
	if isV6 {
		return &m.connected6
	}
	return &m.connected4
}

func (s *StdNetBind) SetSinglePeerMode() {
	s.msgx.singlePeer.Store(true)
}

func sockaddrFromAddrPort(addrPort netip.AddrPort, isV6 bool, storage4 *unix.RawSockaddrInet4, storage6 *unix.RawSockaddrInet6) (*byte, uint32) {
	port := addrPort.Port()<<8 | addrPort.Port()>>8
	if !isV6 {
		*storage4 = unix.RawSockaddrInet4{
			Len:    unix.SizeofSockaddrInet4,
			Family: unix.AF_INET,
			Port:   port,
			Addr:   addrPort.Addr().Unmap().As4(),
		}
		return (*byte)(unsafe.Pointer(storage4)), unix.SizeofSockaddrInet4
	}
	*storage6 = unix.RawSockaddrInet6{
		Len:    unix.SizeofSockaddrInet6,
		Family: unix.AF_INET6,
		Port:   port,
		Addr:   addrPort.Addr().As16(),
	}
	return (*byte)(unsafe.Pointer(storage6)), unix.SizeofSockaddrInet6
}

func (s *StdNetBind) ensureConnected(rawConn syscall.RawConn, isV6 bool, destination netip.AddrPort) (bool, error) {
	if !s.msgx.singlePeer.Load() {
		return false, nil
	}
	connected := s.msgx.connectedFlag(isV6)
	if connected.Load() && s.msgx.endpoint.Load().AddrPort == destination {
		return true, nil
	}
	s.msgx.connectLock.Lock()
	defer s.msgx.connectLock.Unlock()
	if connected.Load() {
		if s.msgx.endpoint.Load().AddrPort == destination {
			return true, nil
		}
		s.msgx.singlePeer.Store(false)
		var disconnected bool
		controlErr := rawConn.Control(func(fd uintptr) {
			addr := unix.RawSockaddrAny{}
			addr.Addr.Family = unix.AF_UNSPEC

			_, _, _ = unix.Syscall(unix.SYS_CONNECT, fd, uintptr(unsafe.Pointer(&addr)), unix.SizeofSockaddrAny)

			_, peerErr := unix.Getpeername(int(fd))
			disconnected = peerErr == unix.ENOTCONN
		})
		if controlErr != nil {
			return false, controlErr
		}
		if !disconnected {
			return false, ErrRebindRequired
		}
		connected.Store(false)
		return false, nil
	}
	if !s.msgx.singlePeer.Load() {
		return false, nil
	}
	var (
		storage4   unix.RawSockaddrInet4
		storage6   unix.RawSockaddrInet6
		connectErr unix.Errno
	)
	name, nameLen := sockaddrFromAddrPort(destination, isV6, &storage4, &storage6)
	controlErr := rawConn.Control(func(fd uintptr) {
		_, _, connectErr = unix.Syscall(unix.SYS_CONNECT, fd, uintptr(unsafe.Pointer(name)), uintptr(nameLen))
	})
	if controlErr != nil {
		return false, controlErr
	}
	if connectErr != 0 {
		return false, connectErr
	}
	s.msgx.endpoint.Store(&StdNetEndpoint{AddrPort: destination})
	connected.Store(true)
	return true, nil
}

func rebindErrno(errno unix.Errno) bool {
	switch errno {
	case unix.EADDRNOTAVAIL, unix.ENETUNREACH, unix.EHOSTUNREACH, unix.ENETDOWN, unix.EHOSTDOWN:
		return true
	}
	return false
}

type sendMsgXState struct {
	hdrs     []msghdrX
	iovs     []unix.Iovec
	storage4 unix.RawSockaddrInet4
	storage6 unix.RawSockaddrInet6
}

var sendMsgXPool = sync.Pool{New: func() any {
	return &sendMsgXState{
		hdrs: make([]msghdrX, IdealBatchSize),
		iovs: make([]unix.Iovec, IdealBatchSize),
	}
}}

func (s *StdNetBind) sendMsgX(conn *net.UDPConn, msgs []ipv6.Message) error {
	var (
		rawConn syscall.RawConn
		isV6    bool
	)
	s.mu.Lock()
	if conn == s.ipv6 {
		rawConn = s.ipv6RC
		isV6 = true
	} else {
		rawConn = s.ipv4RC
	}
	s.mu.Unlock()
	destination := M.AddrPortFromNet(msgs[0].Addr)
	connected, err := s.ensureConnected(rawConn, isV6, destination)
	if err != nil {
		return err
	}
	state := sendMsgXPool.Get().(*sendMsgXState)
	defer sendMsgXPool.Put(state)
	var (
		name    *byte
		nameLen uint32
	)
	if !connected {
		name, nameLen = sockaddrFromAddrPort(destination, isV6, &state.storage4, &state.storage6)
	}
	for i := range msgs {
		buffer := msgs[i].Buffers[0]
		state.iovs[i] = unix.Iovec{Base: &buffer[0]}
		state.iovs[i].SetLen(len(buffer))
		state.hdrs[i] = msghdrX{}
		state.hdrs[i].Msg.Name = name
		state.hdrs[i].Msg.Namelen = nameLen
		state.hdrs[i].Msg.Iov = &state.iovs[i]
		state.hdrs[i].Msg.Iovlen = 1
	}
	var sent int
	for sent < len(msgs) {
		var (
			n     uintptr
			errno unix.Errno
		)
		writeErr := rawConn.Write(func(fd uintptr) bool {
			n, _, errno = unix.RawSyscall6(unix.SYS_SENDMSG_X, fd,
				uintptr(unsafe.Pointer(&state.hdrs[sent])), uintptr(len(msgs)-sent), unix.MSG_DONTWAIT, 0, 0)
			return errno != unix.EAGAIN
		})
		if writeErr != nil {
			return writeErr
		}
		if errno != 0 {
			if connected && rebindErrno(errno) {
				return fmt.Errorf("%w: %w", ErrRebindRequired, errno)
			}
			return errno
		}
		sent += int(n)
	}
	return nil
}

type receiveMsgXState struct {
	hdrs  []msghdrX
	iovs  []unix.Iovec
	names []unix.RawSockaddrInet6
}

func (s *StdNetBind) makeReceiveMsgX(conn *net.UDPConn, isV6 bool) (ReceiveFunc, error) {
	rawConn, err := conn.SyscallConn()
	if err != nil {
		return nil, err
	}
	state := &receiveMsgXState{
		hdrs:  make([]msghdrX, msgXBatchSize),
		iovs:  make([]unix.Iovec, msgXBatchSize),
		names: make([]unix.RawSockaddrInet6, msgXBatchSize),
	}
	return func(bufs [][]byte, sizes []int, eps []Endpoint) (int, error) {
		var connectedEndpoint *StdNetEndpoint
		if s.msgx.connectedFlag(isV6).Load() {
			connectedEndpoint = s.msgx.endpoint.Load()
		}
		count := len(bufs)
		if count > msgXBatchSize {
			count = msgXBatchSize
		}
		for i := 0; i < count; i++ {
			state.iovs[i] = unix.Iovec{Base: &bufs[i][0]}
			state.iovs[i].SetLen(len(bufs[i]))
			state.hdrs[i] = msghdrX{}
			if connectedEndpoint == nil {
				state.hdrs[i].Msg.Name = (*byte)(unsafe.Pointer(&state.names[i]))
				state.hdrs[i].Msg.Namelen = unix.SizeofSockaddrInet6
			}
			state.hdrs[i].Msg.Iov = &state.iovs[i]
			state.hdrs[i].Msg.Iovlen = 1
		}
		var (
			n     uintptr
			errno unix.Errno
		)
		readErr := rawConn.Read(func(fd uintptr) bool {
			n, _, errno = unix.RawSyscall6(unix.SYS_RECVMSG_X, fd,
				uintptr(unsafe.Pointer(&state.hdrs[0])), uintptr(count), unix.MSG_DONTWAIT, 0, 0)
			return errno != unix.EAGAIN
		})
		if readErr != nil {
			return 0, readErr
		}
		if errno != 0 {
			if errno == unix.ECONNREFUSED {
				return 0, nil
			}
			if connectedEndpoint != nil && rebindErrno(errno) {
				return 0, fmt.Errorf("%w: %w", ErrRebindRequired, errno)
			}
			return 0, errno
		}
		if n == 0 {
			return 0, fmt.Errorf("%w: %w", ErrRebindRequired, io.EOF)
		}
		numMsgs := int(n)
		for i := 0; i < numMsgs; i++ {
			sizes[i] = int(state.hdrs[i].DataLen)
			if sizes[i] > 3 {
				bufs[i][1] = 0
				bufs[i][2] = 0
				bufs[i][3] = 0
			}
			if connectedEndpoint != nil {
				eps[i] = connectedEndpoint
				continue
			}
			var addrPort netip.AddrPort
			name := &state.names[i]
			if name.Family == unix.AF_INET6 {
				port := name.Port<<8 | name.Port>>8
				addrPort = netip.AddrPortFrom(netip.AddrFrom16(name.Addr).Unmap(), port)
			} else {
				name4 := (*unix.RawSockaddrInet4)(unsafe.Pointer(name))
				port := name4.Port<<8 | name4.Port>>8
				addrPort = netip.AddrPortFrom(netip.AddrFrom4(name4.Addr), port)
			}
			eps[i] = &StdNetEndpoint{AddrPort: addrPort}
		}
		return numMsgs, nil
	}, nil
}
