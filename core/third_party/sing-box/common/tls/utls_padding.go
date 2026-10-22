//go:build with_utls

package tls

import (
	"io"
	"math/rand"
	"net"
	"strings"

	"github.com/sagernet/sing-box/option"

	utls "github.com/metacubex/utls"
)

const (
	extensionServerName uint16 = 0x0
	tlsExtensionPadding uint16 = 0x15
)

func hostnameInSNI(name string) string {
	host := name
	if len(host) > 0 && host[0] == '[' && host[len(host)-1] == ']' {
		host = host[1 : len(host)-1]
	}
	if i := strings.LastIndex(host, "%"); i > 0 {
		host = host[:i]
	}
	if net.ParseIP(host) != nil {
		return ""
	}
	for len(name) > 0 && name[len(name)-1] == '.' {
		name = name[:len(name)-1]
	}
	return name
}

type SNIExtension struct {
	*utls.GenericExtension
	ServerName string
}

func (e *SNIExtension) Len() int {
	hostName := hostnameInSNI(e.ServerName)
	if len(hostName) == 0 {
		return 0
	}
	return 4 + 2 + 1 + 2 + len(hostName)
}

func (e *SNIExtension) Read(b []byte) (int, error) {
	hostName := hostnameInSNI(e.ServerName)
	if len(hostName) == 0 {
		return 0, io.EOF
	}
	if len(b) < e.Len() {
		return 0, io.ErrShortBuffer
	}

	b[0] = byte(extensionServerName >> 8)
	b[1] = byte(extensionServerName)
	b[2] = byte((len(hostName) + 5) >> 8)
	b[3] = byte(len(hostName) + 5)
	b[4] = byte((len(hostName) + 3) >> 8)
	b[5] = byte(len(hostName) + 3)

	b[7] = byte(len(hostName) >> 8)
	b[8] = byte(len(hostName))
	copy(b[9:], hostName)
	return e.Len(), io.EOF
}

type FakePaddingExtension struct {
	*utls.GenericExtension
	PaddingLen int
	WillPad    bool
}

func (e *FakePaddingExtension) Len() int {
	if e.WillPad {
		return 4 + e.PaddingLen
	}
	return 0
}

func (e *FakePaddingExtension) Read(b []byte) (n int, err error) {
	if !e.WillPad {
		return 0, io.EOF
	}
	if len(b) < e.Len() {
		return 0, io.ErrShortBuffer
	}

	b[0] = byte(tlsExtensionPadding >> 8)
	b[1] = byte(tlsExtensionPadding)
	b[2] = byte(e.PaddingLen >> 8)
	b[3] = byte(e.PaddingLen)
	x := make([]byte, e.PaddingLen)
	_, err = rand.Read(x)
	if err != nil {
		return 0, err
	}
	copy(b[4:], x)
	return e.Len(), io.EOF
}

func makeTLSHelloPacketWithPadding(uConn *utls.UConn, configPaddingSize option.IntRange, sni string) (*utls.UConn, error) {
	paddingSize := int(configPaddingSize.UniformRand())
	if paddingSize <= 0 {
		paddingSize = 1
	}
	spec := utls.ClientHelloSpec{
		TLSVersMax: utls.VersionTLS13,
		TLSVersMin: utls.VersionTLS12,
		CipherSuites: []uint16{
			utls.GREASE_PLACEHOLDER,
			utls.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305,
			utls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
			utls.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
			utls.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
			utls.TLS_AES_128_GCM_SHA256,
			utls.FAKE_TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
			utls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			utls.TLS_RSA_WITH_AES_256_CBC_SHA,
		},
		Extensions: []utls.TLSExtension{
			&FakePaddingExtension{
				PaddingLen: paddingSize,
				WillPad:    true,
			},
			&utls.SupportedCurvesExtension{Curves: []utls.CurveID{utls.X25519, utls.CurveP256}},
			&utls.SupportedPointsExtension{SupportedPoints: []byte{0}},
			&utls.SessionTicketExtension{},
			&utls.ALPNExtension{AlpnProtocols: []string{"http/1.1"}},
			&utls.SignatureAlgorithmsExtension{SupportedSignatureAlgorithms: []utls.SignatureScheme{
				utls.ECDSAWithP256AndSHA256,
				utls.ECDSAWithP384AndSHA384,
				utls.ECDSAWithP521AndSHA512,
				utls.PSSWithSHA256,
				utls.PSSWithSHA384,
				utls.PSSWithSHA512,
				utls.PKCS1WithSHA256,
				utls.PKCS1WithSHA384,
				utls.PKCS1WithSHA512,
				utls.ECDSAWithSHA1,
				utls.PKCS1WithSHA1}},
			&utls.KeyShareExtension{KeyShares: []utls.KeyShare{
				{Group: utls.CurveID(utls.GREASE_PLACEHOLDER), Data: []byte{0}},
				{Group: utls.X25519},
			}},
			&utls.PSKKeyExchangeModesExtension{Modes: []uint8{1}},

			&SNIExtension{
				ServerName: sni,
			},
		},
		GetSessionID: nil,
	}
	err := uConn.ApplyPreset(&spec)
	if err != nil {
		return nil, err
	}
	return uConn, nil
}
