// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

//go:build linux && go1.25 && badlinkname

package ktls

const (
	maxPlaintext               = 16384
	maxCiphertext              = 16384 + 2048
	maxCiphertextTLS13         = 16384 + 256
	recordHeaderLen            = 5
	maxHandshake               = 65536
	maxHandshakeCertificateMsg = 262144
	maxUselessRecords          = 16
)

const (
	recordTypeChangeCipherSpec = 20
	recordTypeAlert            = 21
	recordTypeHandshake        = 22
	recordTypeApplicationData  = 23
)
