package tlsspoof

import (
	"bytes"
	"context"
	"crypto/tls"

	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
)

func buildFakeClientHello(sni string) ([]byte, error) {
	if sni == "" {
		return nil, E.New("empty sni")
	}
	var buf bytes.Buffer
	tlsConn := tls.Client(bufio.NewWriteOnlyConn(&buf), &tls.Config{
		ServerName: sni,

		CurvePreferences:   []tls.CurveID{tls.X25519, tls.CurveP256, tls.CurveP384},
		MinVersion:         tls.VersionTLS12,
		MaxVersion:         tls.VersionTLS13,
		NextProtos:         []string{"h2", "http/1.1"},
		InsecureSkipVerify: true,
	})
	_ = tlsConn.HandshakeContext(context.Background())
	if buf.Len() == 0 {
		return nil, E.New("tls ClientHello not produced")
	}
	return buf.Bytes(), nil
}
