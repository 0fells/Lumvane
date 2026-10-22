//go:build darwin && cgo

package adapter

import "unsafe"

type AppleAnchors interface {
	Retain() AppleAnchors
	Release()

	Ref() unsafe.Pointer
}

type AppleCertificateStore interface {
	CertificateStore
	AppleAnchors() AppleAnchors
}
