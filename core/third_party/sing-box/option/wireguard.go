package option

import (
	"net/netip"
	"strconv"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/common/json/badoption"
)

type WireGuardEndpointOptions struct {
	System       bool                             `json:"system,omitempty"`
	Name         string                           `json:"name,omitempty"`
	MTU          uint32                           `json:"mtu,omitempty"`
	Address      badoption.Listable[netip.Prefix] `json:"address"`
	PrivateKey   string                           `json:"private_key"`
	ListenPort   uint16                           `json:"listen_port,omitempty"`
	Peers        []WireGuardPeer                  `json:"peers,omitempty"`
	UDPTimeout   badoption.Duration               `json:"udp_timeout,omitempty"`
	UDPMapping   UDPNATBehavior                   `json:"udp_mapping,omitempty"`
	UDPFiltering UDPNATBehavior                   `json:"udp_filtering,omitempty"`
	UDPNATMax    uint32                           `json:"udp_nat_max,omitempty"`
	Workers      int                              `json:"workers,omitempty"`
	AmneziaWG    *AmneziaWGOptions                `json:"amnezia_wg,omitempty"`
	DialerOptions
}

type AmneziaWGOptions struct {
	Jc   int    `json:"jc,omitempty"`
	Jmin int    `json:"jmin,omitempty"`
	Jmax int    `json:"jmax,omitempty"`
	S1   int    `json:"s1,omitempty"`
	S2   int    `json:"s2,omitempty"`
	S3   int    `json:"s3,omitempty"`
	S4   int    `json:"s4,omitempty"`
	H1   string `json:"h1,omitempty"`
	H2   string `json:"h2,omitempty"`
	H3   string `json:"h3,omitempty"`
	H4   string `json:"h4,omitempty"`
	I1   string `json:"i1,omitempty"`
	I2   string `json:"i2,omitempty"`
	I3   string `json:"i3,omitempty"`
	I4   string `json:"i4,omitempty"`
	I5   string `json:"i5,omitempty"`

	HeaderProtectionKey    string         `json:"header_protection_key,omitempty"`
	ContentPaddingAddition AmneziaWGRange `json:"content_padding_addition,omitempty"`
	RekeyAfterTime         AmneziaWGRange `json:"rekey_after_time,omitempty"`
	RekeyTimeout           AmneziaWGRange `json:"rekey_timeout,omitempty"`
	RejectAfterTime        AmneziaWGRange `json:"reject_after_time,omitempty"`
	KeepaliveTimeout       AmneziaWGRange `json:"keepalive_timeout,omitempty"`
	MaxHandshakeAttempts   AmneziaWGRange `json:"max_handshake_attempts,omitempty"`
}

type AmneziaWGRange string

func (r AmneziaWGRange) MarshalJSON() ([]byte, error) {
	if uintValue, err := strconv.ParseUint(string(r), 10, 32); err == nil {
		return json.Marshal(uintValue)
	}
	return json.Marshal(string(r))
}

func (r *AmneziaWGRange) UnmarshalJSON(content []byte) error {
	var stringValue string
	if err := json.Unmarshal(content, &stringValue); err == nil {
		*r = AmneziaWGRange(stringValue)
		return nil
	}
	var uintValue uint32
	if err := json.Unmarshal(content, &uintValue); err != nil {
		return E.New("invalid range value: ", string(content))
	}
	*r = AmneziaWGRange(strconv.FormatUint(uint64(uintValue), 10))
	return nil
}

type WireGuardPeer struct {
	Address                     string                           `json:"address,omitempty"`
	Port                        uint16                           `json:"port,omitempty"`
	PublicKey                   string                           `json:"public_key,omitempty"`
	PreSharedKey                string                           `json:"pre_shared_key,omitempty"`
	AllowedIPs                  badoption.Listable[netip.Prefix] `json:"allowed_ips,omitempty"`
	PersistentKeepaliveInterval AmneziaWGRange                   `json:"persistent_keepalive_interval,omitempty"`
	Reserved                    []uint8                          `json:"reserved,omitempty"`
}
