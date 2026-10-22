// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

//go:build linux && go1.25 && badlinkname

package ktls

import (
	"bytes"
	"crypto/tls"
	"fmt"
	"io"
	"net"
	"unsafe"
)

func (c *Conn) Read(b []byte) (int, error) {
	if !c.kernelRx {
		return c.Conn.Read(b)
	}

	if len(b) == 0 {
		return 0, nil
	}

	c.rawConn.In.Lock()
	defer c.rawConn.In.Unlock()

	for c.rawConn.Input.Len() == 0 {
		if err := c.readRecord(); err != nil {
			return 0, err
		}
		for c.rawConn.Hand.Len() > 0 {
			if err := c.handlePostHandshakeMessage(); err != nil {
				return 0, err
			}
		}
	}

	n, _ := c.rawConn.Input.Read(b)

	if n != 0 && c.rawConn.Input.Len() == 0 && c.rawConn.RawInput.Len() > 0 &&
		c.rawConn.RawInput.Bytes()[0] == recordTypeAlert {
		if err := c.readRecord(); err != nil {
			return n, err
		}
	}

	return n, nil
}

func (c *Conn) readRecord() error {
	if *c.rawConn.In.Err != nil {
		return *c.rawConn.In.Err
	}

	typ, data, err := c.readRawRecord()
	if err != nil {
		return err
	}

	if len(data) > maxPlaintext {
		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertRecordOverflow))
	}

	if c.rawConn.In.Cipher == nil && typ == recordTypeApplicationData {
		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
	}

	if *c.rawConn.Vers == tls.VersionTLS13 && typ != recordTypeHandshake && c.rawConn.Hand.Len() > 0 {
		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
	}

	switch typ {
	default:
		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
	case recordTypeAlert:

		if len(data) != 2 {
			return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}
		if data[1] == alertCloseNotify {
			return c.rawConn.In.SetErrorLocked(io.EOF)
		}
		if *c.rawConn.Vers == tls.VersionTLS13 {
			if data[1] == alertUserCanceled {
				return c.retryReadRecord()
			}
			return c.rawConn.In.SetErrorLocked(&net.OpError{Op: "remote error", Err: tls.AlertError(data[1])})
		}
		switch data[0] {
		case alertLevelWarning:

			return c.retryReadRecord()
		case alertLevelError:
			return c.rawConn.In.SetErrorLocked(&net.OpError{Op: "remote error", Err: tls.AlertError(data[1])})
		default:
			return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}

	case recordTypeChangeCipherSpec:
		if len(data) != 1 || data[0] != 1 {
			return c.rawConn.In.SetErrorLocked(c.sendAlert(alertDecodeError))
		}

		if c.rawConn.Hand.Len() > 0 {
			return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}

		if *c.rawConn.Vers == tls.VersionTLS13 {
			return c.retryReadRecord()
		}

		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))

	case recordTypeApplicationData:

		if len(data) == 0 {
			return c.retryReadRecord()
		}

		c.rawConn.Input.Reset(data)
	case recordTypeHandshake:
		if len(data) == 0 {
			return c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}
		c.rawConn.Hand.Write(data)
	}

	return nil
}

func (c *Conn) readRawRecord() (typ uint8, data []byte, err error) {
	if c.kernelRx {
		return c.readKernelRecord()
	}

	if err = c.readFromUntil(c.conn, recordHeaderLen); err != nil {
		if err == io.ErrUnexpectedEOF && c.rawConn.RawInput.Len() == 0 {
			err = io.EOF
		}
		if e, ok := err.(net.Error); !ok || !e.Temporary() {
			c.rawConn.In.SetErrorLocked(err)
		}
		return
	}
	hdr := c.rawConn.RawInput.Bytes()[:recordHeaderLen]
	typ = hdr[0]

	vers := uint16(hdr[1])<<8 | uint16(hdr[2])
	expectedVers := *c.rawConn.Vers
	if expectedVers == tls.VersionTLS13 {
		expectedVers = tls.VersionTLS12
	}
	n := int(hdr[3])<<8 | int(hdr[4])
	if vers != expectedVers {
		c.sendAlert(alertProtocolVersion)
		msg := fmt.Sprintf("received record with version %x when expecting version %x", vers, expectedVers)
		err = c.rawConn.In.SetErrorLocked(c.newRecordHeaderError(nil, msg))
		return
	}

	if *c.rawConn.Vers == tls.VersionTLS13 && n > maxCiphertextTLS13 || n > maxCiphertext {
		c.sendAlert(alertRecordOverflow)
		msg := fmt.Sprintf("oversized record received with length %d", n)
		err = c.rawConn.In.SetErrorLocked(c.newRecordHeaderError(nil, msg))
		return
	}
	if err = c.readFromUntil(c.conn, recordHeaderLen+n); err != nil {
		if e, ok := err.(net.Error); !ok || !e.Temporary() {
			c.rawConn.In.SetErrorLocked(err)
		}
		return
	}

	record := c.rawConn.RawInput.Next(recordHeaderLen + n)
	data, typ, err = c.rawConn.In.Decrypt(record)
	if err != nil {
		err = c.rawConn.In.SetErrorLocked(c.sendAlert(*(*uint8)((*[2]unsafe.Pointer)(unsafe.Pointer(&err))[1])))
		return
	}
	return
}

func (c *Conn) retryReadRecord() error {
	return c.readRecord()
}

type atLeastReader struct {
	R io.Reader
	N int64
}

func (r *atLeastReader) Read(p []byte) (int, error) {
	if r.N <= 0 {
		return 0, io.EOF
	}
	n, err := r.R.Read(p)
	r.N -= int64(n)
	if r.N > 0 && err == io.EOF {
		return n, io.ErrUnexpectedEOF
	}
	if r.N <= 0 && err == nil {
		return n, io.EOF
	}
	return n, err
}

func (c *Conn) readFromUntil(r io.Reader, n int) error {
	if c.rawConn.RawInput.Len() >= n {
		return nil
	}
	needs := n - c.rawConn.RawInput.Len()

	c.rawConn.RawInput.Grow(needs + bytes.MinRead)
	_, err := c.rawConn.RawInput.ReadFrom(&atLeastReader{r, int64(needs)})
	return err
}

func (c *Conn) newRecordHeaderError(conn net.Conn, msg string) (err tls.RecordHeaderError) {
	err.Msg = msg
	err.Conn = conn
	copy(err.RecordHeader[:], c.rawConn.RawInput.Bytes())
	return err
}
