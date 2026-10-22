package option

import (
	"github.com/sagernet/sing/common/byteformats"
	"github.com/sagernet/sing/common/json/badoption"
)

type HysteriaInboundOptions struct {
	ListenOptions
	Up       *byteformats.NetworkBytesCompat `json:"up,omitempty"`
	UpMbps   int                             `json:"up_mbps,omitempty"`
	Down     *byteformats.NetworkBytesCompat `json:"down,omitempty"`
	DownMbps int                             `json:"down_mbps,omitempty"`
	Obfs     string                          `json:"obfs,omitempty"`
	Users    []HysteriaUser                  `json:"users,omitempty"`

	ReceiveWindowConn uint64 `json:"recv_window_conn,omitempty" schema:"omit"`

	ReceiveWindowClient uint64 `json:"recv_window_client,omitempty" schema:"omit"`

	MaxConnClient int `json:"max_conn_client,omitempty" schema:"omit"`

	DisableMTUDiscovery bool `json:"disable_mtu_discovery,omitempty" schema:"omit"`
	InboundTLSOptionsContainer
	QUICOptions
}

type HysteriaUser struct {
	Name       string `json:"name,omitempty"`
	Auth       []byte `json:"auth,omitempty"`
	AuthString string `json:"auth_str,omitempty"`
}

type HysteriaOutboundOptions struct {
	DialerOptions
	ServerOptions
	ServerPorts badoption.Listable[string]      `json:"server_ports,omitempty"`
	HopInterval badoption.Duration              `json:"hop_interval,omitempty"`
	Up          *byteformats.NetworkBytesCompat `json:"up,omitempty"`
	UpMbps      int                             `json:"up_mbps,omitempty"`
	Down        *byteformats.NetworkBytesCompat `json:"down,omitempty"`
	DownMbps    int                             `json:"down_mbps,omitempty"`
	Obfs        string                          `json:"obfs,omitempty"`
	Auth        []byte                          `json:"auth,omitempty"`
	AuthString  string                          `json:"auth_str,omitempty"`

	ReceiveWindowConn uint64 `json:"recv_window_conn,omitempty" schema:"omit"`

	ReceiveWindow uint64 `json:"recv_window,omitempty" schema:"omit"`

	DisableMTUDiscovery bool        `json:"disable_mtu_discovery,omitempty" schema:"omit"`
	Network             NetworkList `json:"network,omitempty"`
	OutboundTLSOptionsContainer
	QUICOptions
}
