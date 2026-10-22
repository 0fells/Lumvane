// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

//go:build linux && go1.25 && badlinkname

package ktls

import (
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"os"
)

func (c *Conn) handlePostHandshakeMessage() error {
	if *c.rawConn.Vers != tls.VersionTLS13 {
		return errors.New("ktls: kernel does not support TLS 1.2 renegotiation")
	}

	msg, err := c.readHandshake(nil)
	if err != nil {
		return err
	}

	switch msg := msg.(type) {
	case *newSessionTicketMsgTLS13:

		return nil
	case *keyUpdateMsg:
		return c.handleKeyUpdate(msg)
	}

	c.sendAlert(alertUnexpectedMessage)
	return fmt.Errorf("tls: received unexpected handshake message of type %T", msg)
}

func (c *Conn) handleKeyUpdate(keyUpdate *keyUpdateMsg) error {
	cipherSuite := cipherSuiteTLS13ByID(*c.rawConn.CipherSuite)
	if cipherSuite == nil {
		return c.rawConn.In.SetErrorLocked(c.sendAlert(alertInternalError))
	}

	newSecret := nextTrafficSecret(cipherSuite, *c.rawConn.In.TrafficSecret)
	c.rawConn.In.SetTrafficSecret(cipherSuite, 0, newSecret)

	err := c.resetupRX()
	if err != nil {
		c.sendAlert(alertInternalError)
		return c.rawConn.In.SetErrorLocked(fmt.Errorf("ktls: resetupRX failed: %w", err))
	}

	if keyUpdate.updateRequested {
		c.rawConn.Out.Lock()
		defer c.rawConn.Out.Unlock()

		resetup, err := c.resetupTX()
		if err != nil {
			c.sendAlertLocked(alertInternalError)
			return c.rawConn.Out.SetErrorLocked(fmt.Errorf("ktls: resetupTX failed: %w", err))
		}

		msg := &keyUpdateMsg{}
		msgBytes, err := msg.marshal()
		if err != nil {
			return err
		}
		_, err = c.writeRecordLocked(recordTypeHandshake, msgBytes)
		if err != nil {
			c.rawConn.Out.SetErrorLocked(err)
			return nil
		}

		newSecret := nextTrafficSecret(cipherSuite, *c.rawConn.Out.TrafficSecret)
		c.rawConn.Out.SetTrafficSecret(cipherSuite, 0, newSecret)

		err = resetup()
		if err != nil {
			return c.rawConn.Out.SetErrorLocked(fmt.Errorf("ktls: resetupTX failed: %w", err))
		}
	}

	return nil
}

func (c *Conn) readHandshakeBytes(n int) error {
	for c.rawConn.Hand.Len() < n {
		if err := c.readRecord(); err != nil {
			return err
		}
	}
	return nil
}

func (c *Conn) readHandshake(transcript io.Writer) (any, error) {
	if err := c.readHandshakeBytes(4); err != nil {
		return nil, err
	}
	data := c.rawConn.Hand.Bytes()

	maxHandshakeSize := maxHandshake

	n := int(data[1])<<16 | int(data[2])<<8 | int(data[3])
	if n > maxHandshakeSize {
		c.sendAlertLocked(alertInternalError)
		return nil, c.rawConn.In.SetErrorLocked(fmt.Errorf("tls: handshake message of length %d bytes exceeds maximum of %d bytes", n, maxHandshakeSize))
	}
	if err := c.readHandshakeBytes(4 + n); err != nil {
		return nil, err
	}
	data = c.rawConn.Hand.Next(4 + n)
	return c.unmarshalHandshakeMessage(data, transcript)
}

func (c *Conn) unmarshalHandshakeMessage(data []byte, transcript io.Writer) (any, error) {
	var m handshakeMessage
	switch data[0] {
	case typeNewSessionTicket:
		if *c.rawConn.Vers == tls.VersionTLS13 {
			m = new(newSessionTicketMsgTLS13)
		} else {
			return nil, os.ErrInvalid
		}
	case typeKeyUpdate:
		m = new(keyUpdateMsg)
	default:
		return nil, c.rawConn.In.SetErrorLocked(c.sendAlert(alertUnexpectedMessage))
	}

	data = append([]byte(nil), data...)

	if !m.unmarshal(data) {
		return nil, c.rawConn.In.SetErrorLocked(c.sendAlert(alertDecodeError))
	}

	if transcript != nil {
		transcript.Write(data)
	}

	return m, nil
}
