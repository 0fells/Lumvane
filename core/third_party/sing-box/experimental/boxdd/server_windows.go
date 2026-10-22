package main

import (
	"net"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/tailscale/go-winio"
)

const pipeSecurityDescriptor = `D:P(A;;GA;;;SY)(A;;GA;;;BA)(A;;GRGW;;;AU)`

const (
	pipeBufferSize = 65536
	daemonPipePath = `\\.\pipe\ProtectedPrefix\Administrators\sing-box`
)

func listenEndpoint() (net.Listener, error) {
	if socketPath != "" && !strings.EqualFold(socketPath, daemonPipePath) {
		return nil, E.New("custom Windows daemon pipe paths are not supported")
	}
	return winio.ListenPipe(daemonPipePath, &winio.PipeConfig{
		SecurityDescriptor: pipeSecurityDescriptor,
		InputBufferSize:    pipeBufferSize,
		OutputBufferSize:   pipeBufferSize,
	})
}
