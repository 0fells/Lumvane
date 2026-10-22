package hysteria

import (
	"github.com/sagernet/sing-box/option"
	qtls "github.com/sagernet/sing-quic"
)

func buildBaseQUICOptions(options option.QUICOptions) qtls.QUICOptions {
	return qtls.QUICOptions{
		IdleTimeout:             options.IdleTimeout.Build(),
		KeepAlivePeriod:         options.KeepAlivePeriod.Build(),
		StreamReceiveWindow:     options.StreamReceiveWindow.Value(),
		ConnectionReceiveWindow: options.ConnectionReceiveWindow.Value(),
		MaxConcurrentStreams:    options.MaxConcurrentStreams,
		InitialPacketSize:       options.InitialPacketSize,
		DisablePathMTUDiscovery: options.DisablePathMTUDiscovery,
	}
}

func buildInboundQUICOptions(options option.HysteriaInboundOptions) qtls.QUICOptions {
	quicOptions := buildBaseQUICOptions(options.QUICOptions)
	if quicOptions.ConnectionReceiveWindow == 0 {
		quicOptions.ConnectionReceiveWindow = options.ReceiveWindowConn
	}
	if quicOptions.StreamReceiveWindow == 0 {
		quicOptions.StreamReceiveWindow = options.ReceiveWindowClient
	}
	if quicOptions.MaxConcurrentStreams == 0 {
		quicOptions.MaxConcurrentStreams = options.MaxConnClient
	}
	if !quicOptions.DisablePathMTUDiscovery {
		quicOptions.DisablePathMTUDiscovery = options.DisableMTUDiscovery
	}
	return quicOptions
}

func buildOutboundQUICOptions(options option.HysteriaOutboundOptions) qtls.QUICOptions {
	quicOptions := buildBaseQUICOptions(options.QUICOptions)
	if quicOptions.ConnectionReceiveWindow == 0 {
		quicOptions.ConnectionReceiveWindow = options.ReceiveWindowConn
	}
	if quicOptions.StreamReceiveWindow == 0 {
		quicOptions.StreamReceiveWindow = options.ReceiveWindow
	}
	if !quicOptions.DisablePathMTUDiscovery {
		quicOptions.DisablePathMTUDiscovery = options.DisableMTUDiscovery
	}
	return quicOptions
}
