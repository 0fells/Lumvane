// Copyright 2021 The Go Authors. All rights reserved.
// Copyright 2015 Microsoft
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

//go:build windows

package namedpipe

import (
	"io"
	"os"
	"runtime"
	"sync"
	"sync/atomic"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

type timeoutChan chan struct{}

var (
	ioInitOnce       sync.Once
	ioCompletionPort windows.Handle
)

type ioResult struct {
	bytes uint32
	err   error
}

type ioOperation struct {
	o  windows.Overlapped
	ch chan ioResult
}

func initIo() {
	h, err := windows.CreateIoCompletionPort(windows.InvalidHandle, 0, 0, 0)
	if err != nil {
		panic(err)
	}
	ioCompletionPort = h
	go ioCompletionProcessor(h)
}

type file struct {
	handle        windows.Handle
	wg            sync.WaitGroup
	wgLock        sync.RWMutex
	closing       atomic.Bool
	socket        bool
	readDeadline  deadlineHandler
	writeDeadline deadlineHandler
}

type deadlineHandler struct {
	setLock     sync.Mutex
	channel     timeoutChan
	channelLock sync.RWMutex
	timer       *time.Timer
	timedout    atomic.Bool
}

func makeFile(h windows.Handle) (*file, error) {
	f := &file{handle: h}
	ioInitOnce.Do(initIo)
	_, err := windows.CreateIoCompletionPort(h, ioCompletionPort, 0, 0)
	if err != nil {
		return nil, err
	}
	err = windows.SetFileCompletionNotificationModes(h, windows.FILE_SKIP_COMPLETION_PORT_ON_SUCCESS|windows.FILE_SKIP_SET_EVENT_ON_HANDLE)
	if err != nil {
		return nil, err
	}
	f.readDeadline.channel = make(timeoutChan)
	f.writeDeadline.channel = make(timeoutChan)
	return f, nil
}

func (f *file) closeHandle() {
	f.wgLock.Lock()

	if f.closing.Swap(true) == false {
		f.wgLock.Unlock()

		windows.CancelIoEx(f.handle, nil)
		f.wg.Wait()

		windows.Close(f.handle)
		f.handle = 0
	} else {
		f.wgLock.Unlock()
	}
}

func (f *file) Close() error {
	f.closeHandle()
	return nil
}

func (f *file) prepareIo() (*ioOperation, error) {
	f.wgLock.RLock()
	if f.closing.Load() {
		f.wgLock.RUnlock()
		return nil, os.ErrClosed
	}
	f.wg.Add(1)
	f.wgLock.RUnlock()
	c := &ioOperation{}
	c.ch = make(chan ioResult)
	return c, nil
}

func ioCompletionProcessor(h windows.Handle) {
	for {
		var bytes uint32
		var key uintptr
		var op *ioOperation
		err := windows.GetQueuedCompletionStatus(h, &bytes, &key, (**windows.Overlapped)(unsafe.Pointer(&op)), windows.INFINITE)
		if op == nil {
			panic(err)
		}
		op.ch <- ioResult{bytes, err}
	}
}

func (f *file) asyncIo(c *ioOperation, d *deadlineHandler, bytes uint32, err error) (int, error) {
	if err != windows.ERROR_IO_PENDING {
		return int(bytes), err
	}

	if f.closing.Load() {
		windows.CancelIoEx(f.handle, &c.o)
	}

	var timeout timeoutChan
	if d != nil {
		d.channelLock.Lock()
		timeout = d.channel
		d.channelLock.Unlock()
	}

	var r ioResult
	select {
	case r = <-c.ch:
		err = r.err
		if err == windows.ERROR_OPERATION_ABORTED {
			if f.closing.Load() {
				err = os.ErrClosed
			}
		} else if err != nil && f.socket {
			var bytes, flags uint32
			err = windows.WSAGetOverlappedResult(f.handle, &c.o, &bytes, false, &flags)
		}
	case <-timeout:
		windows.CancelIoEx(f.handle, &c.o)
		r = <-c.ch
		err = r.err
		if err == windows.ERROR_OPERATION_ABORTED {
			err = os.ErrDeadlineExceeded
		}
	}

	runtime.KeepAlive(c)
	return int(r.bytes), err
}

func (f *file) Read(b []byte) (int, error) {
	c, err := f.prepareIo()
	if err != nil {
		return 0, err
	}
	defer f.wg.Done()

	if f.readDeadline.timedout.Load() {
		return 0, os.ErrDeadlineExceeded
	}

	var bytes uint32
	err = windows.ReadFile(f.handle, b, &bytes, &c.o)
	n, err := f.asyncIo(c, &f.readDeadline, bytes, err)
	runtime.KeepAlive(b)

	if err == nil && n == 0 && len(b) != 0 {
		return 0, io.EOF
	} else if err == windows.ERROR_BROKEN_PIPE {
		return 0, io.EOF
	} else {
		return n, err
	}
}

func (f *file) Write(b []byte) (int, error) {
	c, err := f.prepareIo()
	if err != nil {
		return 0, err
	}
	defer f.wg.Done()

	if f.writeDeadline.timedout.Load() {
		return 0, os.ErrDeadlineExceeded
	}

	var bytes uint32
	err = windows.WriteFile(f.handle, b, &bytes, &c.o)
	n, err := f.asyncIo(c, &f.writeDeadline, bytes, err)
	runtime.KeepAlive(b)
	return n, err
}

func (f *file) SetReadDeadline(deadline time.Time) error {
	return f.readDeadline.set(deadline)
}

func (f *file) SetWriteDeadline(deadline time.Time) error {
	return f.writeDeadline.set(deadline)
}

func (f *file) Flush() error {
	return windows.FlushFileBuffers(f.handle)
}

func (f *file) Fd() uintptr {
	return uintptr(f.handle)
}

func (d *deadlineHandler) set(deadline time.Time) error {
	d.setLock.Lock()
	defer d.setLock.Unlock()

	if d.timer != nil {
		if !d.timer.Stop() {
			<-d.channel
		}
		d.timer = nil
	}
	d.timedout.Store(false)

	select {
	case <-d.channel:
		d.channelLock.Lock()
		d.channel = make(chan struct{})
		d.channelLock.Unlock()
	default:
	}

	if deadline.IsZero() {
		return nil
	}

	timeoutIO := func() {
		d.timedout.Store(true)
		close(d.channel)
	}

	now := time.Now()
	duration := deadline.Sub(now)
	if deadline.After(now) {
		d.timer = time.AfterFunc(duration, timeoutIO)
	} else {
		timeoutIO()
	}
	return nil
}
