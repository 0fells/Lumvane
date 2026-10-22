package xray

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/transport/internet"
)

func TestParseDomainStrategy(t *testing.T) {
	for in, want := range map[string]internet.DomainStrategy{
		"AsIs":        internet.DomainStrategy_AS_IS,
		" UseIPv4 ":   internet.DomainStrategy_USE_IP4,
		"useip6":      internet.DomainStrategy_USE_IP6,
		"UseIPv4v6":   internet.DomainStrategy_USE_IP46,
		"UseIPv6v4":   internet.DomainStrategy_USE_IP64,
		"ForceIP":     internet.DomainStrategy_FORCE_IP,
		"ForceIPv4":   internet.DomainStrategy_FORCE_IP4,
		"forceip6":    internet.DomainStrategy_FORCE_IP6,
		"ForceIPv4v6": internet.DomainStrategy_FORCE_IP46,
		"ForceIPv6v4": internet.DomainStrategy_FORCE_IP64,
		"":            internet.DomainStrategy_USE_IP,
		"whatever":    internet.DomainStrategy_USE_IP,
	} {
		if got := ParseDomainStrategy(in); got != want {
			t.Errorf("ParseDomainStrategy(%q) = %v, want %v", in, got, want)
		}
	}
}

func TestIsLoopback(t *testing.T) {
	for addr, want := range map[string]bool{
		"127.0.0.1":   true,
		"::1":         true,
		"localhost":   true,
		"LocalHost":   true,
		"192.0.2.1":   false,
		"example.com": false,
	} {
		dest := net.TCPDestination(net.ParseAddress(addr), 443)
		if got := isLoopback(dest); got != want {
			t.Errorf("isLoopback(%s) = %v, want %v", addr, got, want)
		}
	}
}

func TestSweepIntervalFor(t *testing.T) {
	for idle, want := range map[time.Duration]time.Duration{
		0:                gateSweepInterval,
		time.Hour:        gateSweepInterval,
		10 * time.Second: 5 * time.Second,
		time.Second:      minGateSweepInterval,
	} {
		if got := sweepIntervalFor(idle); got != want {
			t.Errorf("sweepIntervalFor(%v) = %v, want %v", idle, got, want)
		}
	}
}

func TestRemapInboundsMovesPortsAndKeepsOutbounds(t *testing.T) {
	const outbounds = `[{"protocol":"freedom","settings":{"note":"kept  as is"}}]`
	config := `{"inbounds":[{"listen":"127.0.0.1","port":10808,"protocol":"socks"},{"port":10809,"protocol":"http"}],"outbounds":` + outbounds + `}`
	rewritten, pairs, err := remapInbounds(config)
	if err != nil {
		t.Fatal(err)
	}
	if len(pairs) != 2 || pairs[0].gate != "127.0.0.1:10808" || pairs[1].gate != "127.0.0.1:10809" {
		t.Fatalf("unexpected gate addresses %+v", pairs)
	}
	if pairs[0].target == pairs[1].target || pairs[0].target == pairs[0].gate {
		t.Fatalf("inbounds were not moved to distinct fresh ports: %+v", pairs)
	}
	var root map[string]json.RawMessage
	if err = json.Unmarshal([]byte(rewritten), &root); err != nil {
		t.Fatal(err)
	}
	if string(root["outbounds"]) != outbounds {
		t.Fatalf("outbounds changed: %s", root["outbounds"])
	}
}

func TestRemapInboundsRejectsUngateable(t *testing.T) {
	for name, cfg := range map[string]string{
		"no inbounds":  `{"outbounds":[]}`,
		"dynamic port": `{"inbounds":[{"listen":"127.0.0.1","protocol":"socks"}]}`,
		"not json":     `{`,
		"bad inbounds": `{"inbounds":{}}`,
	} {
		if _, _, err := remapInbounds(cfg); err == nil || !strings.HasPrefix(err.Error(), "xray gate:") {
			t.Errorf("%s: want a gate error, got %v", name, err)
		}
	}
}

func TestEgressBindingRefusesWithoutInterface(t *testing.T) {
	defer egressInterface.Store(nil)
	d := &egressDialer{}
	SetEgressInterface("")
	_, err := d.Dial(t.Context(), nil, net.TCPDestination(net.ParseAddress("192.0.2.1"), 443), nil)
	if err == nil || !strings.Contains(err.Error(), "no default interface") {
		t.Fatalf("bound dial without an interface must be refused, got %v", err)
	}
	InitEgressInterface("eth0")
	if p := egressInterface.Load(); p == nil || *p != "" {
		t.Fatal("InitEgressInterface must not overwrite an interface the route monitor already set")
	}
}
