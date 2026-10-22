// Copyright 2021 The Go Authors. All rights reserved.
// Copyright 2015 Microsoft
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

//go:build windows

package namedpipe

import (
	"context"
	"io"
	"net"
	"os"
	"runtime"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

type pipe struct {
	*file
	path string
}

type messageBytePipe struct {
	pipe
	writeClosed atomic.Bool
	readEOF     bool
}

type pipeAddress string

func (f *pipe) LocalAddr() net.Addr {
	return pipeAddress(f.path)
}

func (f *pipe) RemoteAddr() net.Addr {
	return pipeAddress(f.path)
}

func (f *pipe) SetDeadline(t time.Time) error {
	f.SetReadDeadline(t)
	f.SetWriteDeadline(t)
	return nil
}

func (f *messageBytePipe) CloseWrite() error {
	if !f.writeClosed.CompareAndSwap(false, true) {
		return io.ErrClosedPipe
	}
	err := f.file.Flush()
	if err != nil {
		f.writeClosed.Store(false)
		return err
	}
	_, err = f.file.Write(nil)
	if err != nil {
		f.writeClosed.Store(false)
		return err
	}
	return nil
}

func (f *messageBytePipe) Write(b []byte) (int, error) {
	if f.writeClosed.Load() {
		return 0, io.ErrClosedPipe
	}
	if len(b) == 0 {
		return 0, nil
	}
	return f.file.Write(b)
}

func (f *messageBytePipe) Read(b []byte) (int, error) {
	if f.readEOF {
		return 0, io.EOF
	}
	n, err := f.file.Read(b)
	if err == io.EOF {
		f.readEOF = true
	} else if err == windows.ERROR_MORE_DATA {
		err = nil
	}
	return n, err
}

func (f *pipe) Handle() windows.Handle {
	return f.handle
}

func (s pipeAddress) Network() string {
	return "pipe"
}

func (s pipeAddress) String() string {
	return string(s)
}

func tryDialPipe(ctx context.Context, path *string) (windows.Handle, error) {
	for {
		select {
		case <-ctx.Done():
			return 0, ctx.Err()
		default:
			path16, err := windows.UTF16PtrFromString(*path)
			if err != nil {
				return 0, err
			}
			h, err := windows.CreateFile(path16, windows.GENERIC_READ|windows.GENERIC_WRITE, 0, nil, windows.OPEN_EXISTING, windows.FILE_FLAG_OVERLAPPED|windows.SECURITY_SQOS_PRESENT|windows.SECURITY_ANONYMOUS, 0)
			if err == nil {
				return h, nil
			}
			if err != windows.ERROR_PIPE_BUSY {
				return h, &os.PathError{Err: err, Op: "open", Path: *path}
			}

			time.Sleep(10 * time.Millisecond)
		}
	}
}

type DialConfig struct {
	ExpectedOwner *windows.SID
}

func (config *DialConfig) DialTimeout(path string, timeout time.Duration) (net.Conn, error) {
	if timeout == 0 {
		timeout = time.Second * 2
	}
	absTimeout := time.Now().Add(timeout)
	ctx, _ := context.WithDeadline(context.Background(), absTimeout)
	conn, err := config.DialContext(ctx, path)
	if err == context.DeadlineExceeded {
		return nil, os.ErrDeadlineExceeded
	}
	return conn, err
}

func (config *DialConfig) DialContext(ctx context.Context, path string) (net.Conn, error) {
	var err error
	var h windows.Handle
	h, err = tryDialPipe(ctx, &path)
	if err != nil {
		return nil, err
	}

	if config.ExpectedOwner != nil {
		sd, err := windows.GetSecurityInfo(h, windows.SE_FILE_OBJECT, windows.OWNER_SECURITY_INFORMATION)
		if err != nil {
			windows.Close(h)
			return nil, err
		}
		realOwner, _, err := sd.Owner()
		if err != nil {
			windows.Close(h)
			return nil, err
		}
		if !realOwner.Equals(config.ExpectedOwner) {
			windows.Close(h)
			return nil, windows.ERROR_ACCESS_DENIED
		}
	}

	var flags uint32
	err = windows.GetNamedPipeInfo(h, &flags, nil, nil, nil)
	if err != nil {
		windows.Close(h)
		return nil, err
	}

	f, err := makeFile(h)
	if err != nil {
		windows.Close(h)
		return nil, err
	}

	if flags&windows.PIPE_TYPE_MESSAGE != 0 {
		return &messageBytePipe{
			pipe: pipe{file: f, path: path},
		}, nil
	}
	return &pipe{file: f, path: path}, nil
}

var defaultDialer DialConfig

func DialTimeout(path string, timeout time.Duration) (net.Conn, error) {
	return defaultDialer.DialTimeout(path, timeout)
}

func DialContext(ctx context.Context, path string) (net.Conn, error) {
	return defaultDialer.DialContext(ctx, path)
}

type acceptResponse struct {
	f   *file
	err error
}

type pipeListener struct {
	firstHandle windows.Handle
	path        string
	config      ListenConfig
	acceptCh    chan chan acceptResponse
	closeCh     chan int
	doneCh      chan int
}

func makeServerPipeHandle(path string, sd *windows.SECURITY_DESCRIPTOR, c *ListenConfig, isFirstPipe bool) (windows.Handle, error) {
	path16, err := windows.UTF16PtrFromString(path)
	if err != nil {
		return 0, &os.PathError{Op: "open", Path: path, Err: err}
	}

	var oa windows.OBJECT_ATTRIBUTES
	oa.Length = uint32(unsafe.Sizeof(oa))

	var ntPath windows.NTUnicodeString
	if err := windows.RtlDosPathNameToNtPathName(path16, &ntPath, nil, nil); err != nil {
		if ntstatus, ok := err.(windows.NTStatus); ok {
			err = ntstatus.Errno()
		}
		return 0, &os.PathError{Op: "open", Path: path, Err: err}
	}
	defer windows.LocalFree(windows.Handle(unsafe.Pointer(ntPath.Buffer)))
	oa.ObjectName = &ntPath

	if isFirstPipe {
		if sd != nil {
			oa.SecurityDescriptor = sd
		} else {
			var acl *windows.ACL
			if err := windows.RtlDefaultNpAcl(&acl); err != nil {
				return 0, err
			}
			defer windows.LocalFree(windows.Handle(unsafe.Pointer(acl)))
			sd, err = windows.NewSecurityDescriptor()
			if err != nil {
				return 0, err
			}
			if err = sd.SetDACL(acl, true, false); err != nil {
				return 0, err
			}
			oa.SecurityDescriptor = sd
		}
	}

	typ := uint32(windows.FILE_PIPE_REJECT_REMOTE_CLIENTS)
	if c.MessageMode {
		typ |= windows.FILE_PIPE_MESSAGE_TYPE
	}

	disposition := uint32(windows.FILE_OPEN)
	access := uint32(windows.GENERIC_READ | windows.GENERIC_WRITE | windows.SYNCHRONIZE)
	if isFirstPipe {
		disposition = windows.FILE_CREATE

		access = windows.SYNCHRONIZE
	}

	timeout := int64(-50 * 10000)

	var (
		h    windows.Handle
		iosb windows.IO_STATUS_BLOCK
	)
	err = windows.NtCreateNamedPipeFile(&h, access, &oa, &iosb, windows.FILE_SHARE_READ|windows.FILE_SHARE_WRITE, disposition, 0, typ, 0, 0, 0xffffffff, uint32(c.InputBufferSize), uint32(c.OutputBufferSize), &timeout)
	if err != nil {
		if ntstatus, ok := err.(windows.NTStatus); ok {
			err = ntstatus.Errno()
		}
		return 0, &os.PathError{Op: "open", Path: path, Err: err}
	}

	runtime.KeepAlive(ntPath)
	return h, nil
}

func (l *pipeListener) makeServerPipe() (*file, error) {
	h, err := makeServerPipeHandle(l.path, nil, &l.config, false)
	if err != nil {
		return nil, err
	}
	f, err := makeFile(h)
	if err != nil {
		windows.Close(h)
		return nil, err
	}
	return f, nil
}

func (l *pipeListener) makeConnectedServerPipe() (*file, error) {
	p, err := l.makeServerPipe()
	if err != nil {
		return nil, err
	}

	ch := make(chan error)
	go func(p *file) {
		ch <- connectPipe(p)
	}(p)

	select {
	case err = <-ch:
		if err != nil {
			p.Close()
			p = nil
		}
	case <-l.closeCh:

		p.Close()
		p = nil
		err = <-ch
		if err == nil || err == os.ErrClosed {
			err = net.ErrClosed
		}
	}
	return p, err
}

func (l *pipeListener) listenerRoutine() {
	closed := false
	for !closed {
		select {
		case <-l.closeCh:
			closed = true
		case responseCh := <-l.acceptCh:
			var (
				p   *file
				err error
			)
			for {
				p, err = l.makeConnectedServerPipe()

				if err != windows.ERROR_NO_DATA {
					break
				}
			}
			responseCh <- acceptResponse{p, err}
			closed = err == net.ErrClosed
		}
	}
	windows.Close(l.firstHandle)
	l.firstHandle = 0

	close(l.doneCh)
}

type ListenConfig struct {
	SecurityDescriptor *windows.SECURITY_DESCRIPTOR

	MessageMode bool

	InputBufferSize int32

	OutputBufferSize int32
}

func (c *ListenConfig) Listen(path string) (net.Listener, error) {
	h, err := makeServerPipeHandle(path, c.SecurityDescriptor, c, true)
	if err != nil {
		return nil, err
	}
	l := &pipeListener{
		firstHandle: h,
		path:        path,
		config:      *c,
		acceptCh:    make(chan chan acceptResponse),
		closeCh:     make(chan int),
		doneCh:      make(chan int),
	}

	if maj, min, _ := windows.RtlGetNtVersionNumbers(); maj < 6 || (maj == 6 && min < 4) {
		path16, err := windows.UTF16PtrFromString(path)
		if err == nil {
			h, err = windows.CreateFile(path16, 0, 0, nil, windows.OPEN_EXISTING, windows.SECURITY_SQOS_PRESENT|windows.SECURITY_ANONYMOUS, 0)
			if err == nil {
				windows.CloseHandle(h)
			}
		}
	}
	go l.listenerRoutine()
	return l, nil
}

var defaultListener ListenConfig

func Listen(path string) (net.Listener, error) {
	return defaultListener.Listen(path)
}

func connectPipe(p *file) error {
	c, err := p.prepareIo()
	if err != nil {
		return err
	}
	defer p.wg.Done()

	err = windows.ConnectNamedPipe(p.handle, &c.o)
	_, err = p.asyncIo(c, nil, 0, err)
	if err != nil && err != windows.ERROR_PIPE_CONNECTED {
		return err
	}
	return nil
}

func (l *pipeListener) Accept() (net.Conn, error) {
	ch := make(chan acceptResponse)
	select {
	case l.acceptCh <- ch:
		response := <-ch
		err := response.err
		if err != nil {
			return nil, err
		}
		if l.config.MessageMode {
			return &messageBytePipe{
				pipe: pipe{file: response.f, path: l.path},
			}, nil
		}
		return &pipe{file: response.f, path: l.path}, nil
	case <-l.doneCh:
		return nil, net.ErrClosed
	}
}

func (l *pipeListener) Close() error {
	select {
	case l.closeCh <- 1:
		<-l.doneCh
	case <-l.doneCh:
	}
	return nil
}

func (l *pipeListener) Addr() net.Addr {
	return pipeAddress(l.path)
}
