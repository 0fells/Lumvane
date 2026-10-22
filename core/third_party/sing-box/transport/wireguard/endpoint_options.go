package wireguard

import (
	"context"
	"encoding/base64"
	"encoding/hex"
	"net/netip"
	"strconv"
	"strings"
	"time"

	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

type EndpointOptions struct {
	Context      context.Context
	Logger       logger.ContextLogger
	System       bool
	Handler      tun.Handler
	UDPTimeout   time.Duration
	ICMPTimeout  time.Duration
	UDPMapping   tun.NATMapping
	UDPFiltering tun.NATFiltering
	UDPNATMax    uint32

	InterfaceFinder   control.InterfaceFinder
	EgressPoolOptions tun.UDPEgressPoolOptions
	Dialer            N.Dialer
	CreateDialer      func(interfaceName string) N.Dialer
	Tag               string
	Name              string
	MTU               uint32
	Address           []netip.Prefix
	PrivateKey        string
	ListenPort        uint16
	ResolvePeer       func(domain string) ([]netip.Addr, error)
	Peers             []PeerOptions
	Workers           int
	AmneziaWG         *AmneziaWGOptions
}

type AmneziaWGOptions struct {
	JunkPacketCount              int
	JunkPacketMinSize            int
	JunkPacketMaxSize            int
	InitPacketJunkSize           int
	ResponsePacketJunkSize       int
	CookieReplyPacketJunkSize    int
	TransportPacketJunkSize      int
	InitPacketMagicHeader        string
	ResponsePacketMagicHeader    string
	CookieReplyPacketMagicHeader string
	TransportPacketMagicHeader   string
	SignaturePackets             []string

	HeaderProtectionKey    string
	ContentPaddingAddition string
	RekeyAfterTime         string
	RekeyTimeout           string
	RejectAfterTime        string
	KeepaliveTimeout       string
	MaxHandshakeAttempts   string
}

func (o *AmneziaWGOptions) GenerateIpcLines() (string, error) {
	if o == nil {
		return "", nil
	}
	var b strings.Builder
	writeInt := func(key string, value int) {
		if value != 0 {
			b.WriteString("\n")
			b.WriteString(key)
			b.WriteString("=")
			b.WriteString(strconv.Itoa(value))
		}
	}
	writeStr := func(key, value string) {
		if value != "" {
			b.WriteString("\n")
			b.WriteString(key)
			b.WriteString("=")
			b.WriteString(value)
		}
	}
	writeInt("jc", o.JunkPacketCount)
	writeInt("jmin", o.JunkPacketMinSize)
	writeInt("jmax", o.JunkPacketMaxSize)
	writeInt("s1", o.InitPacketJunkSize)
	writeInt("s2", o.ResponsePacketJunkSize)
	writeInt("s3", o.CookieReplyPacketJunkSize)
	writeInt("s4", o.TransportPacketJunkSize)
	writeStr("h1", o.InitPacketMagicHeader)
	writeStr("h2", o.ResponsePacketMagicHeader)
	writeStr("h3", o.CookieReplyPacketMagicHeader)
	writeStr("h4", o.TransportPacketMagicHeader)
	for i, sig := range o.SignaturePackets {
		if i >= 5 {
			break
		}
		writeStr("i"+strconv.Itoa(i+1), sig)
	}
	if o.HeaderProtectionKey != "" {
		keyBytes, err := base64.StdEncoding.DecodeString(o.HeaderProtectionKey)
		if err != nil {
			return "", E.Cause(err, "decode amnezia_wg header protection key")
		}
		writeStr("header_protection_key", hex.EncodeToString(keyBytes))
	}
	writeStr("content_padding_addition", o.ContentPaddingAddition)
	writeStr("rekey_after_time", o.RekeyAfterTime)
	writeStr("rekey_timeout", o.RekeyTimeout)
	writeStr("reject_after_time", o.RejectAfterTime)
	writeStr("keepalive_timeout", o.KeepaliveTimeout)
	writeStr("max_handshake_attempts", o.MaxHandshakeAttempts)
	return b.String(), nil
}

type PeerOptions struct {
	Endpoint                    M.Socksaddr
	PublicKey                   string
	PreSharedKey                string
	AllowedIPs                  []netip.Prefix
	PersistentKeepaliveInterval string
	Reserved                    []uint8
}
