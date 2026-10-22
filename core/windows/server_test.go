package main

import (
	"context"
	"crypto/ecdh"
	"encoding/base64"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"LumvaneCore/gen"
)

const simpleConfig = `{
  "log": {"level": "warn"},
  "inbounds": [{"type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 0}],
  "outbounds": [{"type": "direct", "tag": "direct"}],
  "route": {"final": "direct"}
}`

const statsConfig = `{
  "log": {"level": "warn"},
  "inbounds": [{"type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 0}],
  "outbounds": [{"type": "direct", "tag": "direct"}],
  "route": {"final": "direct"},
  "experimental": {"clash_api": {"default_mode": ""}}
}`

func freePort(t *testing.T) int {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port
}

func xraySocksConfig(port int) string {
	return `{
  "inbounds": [{"listen": "127.0.0.1", "port": ` + strconv.Itoa(port) + `, "protocol": "socks", "settings": {"udp": true}}],
  "outbounds": [{"protocol": "freedom"}]
}`
}

func stopCore(t *testing.T, s *server) {
	t.Helper()
	out, err := s.Stop(context.Background(), &gen.EmptyReq{})
	if err != nil || out.GetError() != "" {
		t.Fatalf("stop: %v %q", err, out.GetError())
	}
}

func TestCheckConfig(t *testing.T) {
	s := &server{}
	ctx := context.Background()
	cases := []struct {
		name string
		req  *gen.LoadConfigReq
		ok   bool
	}{
		{"valid", &gen.LoadConfigReq{CoreConfig: To(simpleConfig)}, true},
		{"garbage", &gen.LoadConfigReq{CoreConfig: To(`{not json`)}, false},
		{"unknown outbound", &gen.LoadConfigReq{CoreConfig: To(`{"outbounds":[{"type":"nope","tag":"x"}]}`)}, false},
		{"missing detour", &gen.LoadConfigReq{CoreConfig: To(`{"outbounds":[{"type":"direct","tag":"d","detour":"zzz"}]}`)}, false},
		{"xray valid", &gen.LoadConfigReq{NeedXray: To(true), XrayConfig: To(xraySocksConfig(10808))}, true},
		{"xray broken vless", &gen.LoadConfigReq{NeedXray: To(true), XrayConfig: To(`{"outbounds":[{"protocol":"vless","settings":{}}]}`)}, false},
		{"empty request", &gen.LoadConfigReq{}, false},
	}
	for _, c := range cases {
		out, err := s.CheckConfig(ctx, c.req)
		if err != nil {
			t.Fatalf("%s: transport error %v", c.name, err)
		}
		if got := out.GetError() == ""; got != c.ok {
			t.Errorf("%s: ok=%v, error %q", c.name, got, out.GetError())
		}
	}
}

func TestProtocolsRegistered(t *testing.T) {
	s := &server{}
	ctx := context.Background()
	for _, typ := range []string{"vless", "vmess", "trojan", "shadowsocks", "socks", "http", "anytls", "naive",
		"hysteria", "hysteria2", "juicity", "mieru", "shadowtls", "ssh", "trusttunnel", "tuic", "selector", "urltest", "auto-selector"} {
		out, _ := s.CheckConfig(ctx, &gen.LoadConfigReq{CoreConfig: To(`{"outbounds":[{"type":"` + typ + `","tag":"x"}]}`)})
		if strings.Contains(out.GetError(), "unknown outbound type") {
			t.Errorf("outbound type %q is not compiled in: %s", typ, out.GetError())
		}
	}
	for _, typ := range []string{"wireguard", "tailscale"} {
		out, _ := s.CheckConfig(ctx, &gen.LoadConfigReq{CoreConfig: To(`{"endpoints":[{"type":"` + typ + `","tag":"x"}]}`)})
		if strings.Contains(out.GetError(), "unknown endpoint type") {
			t.Errorf("endpoint type %q is not compiled in: %s", typ, out.GetError())
		}
	}
}

func TestStartStopAndStats(t *testing.T) {
	s := &server{}
	ctx := context.Background()
	out, err := s.Start(ctx, &gen.LoadConfigReq{CoreConfig: To(statsConfig)})
	if err != nil || out.GetError() != "" {
		t.Fatalf("start: %v %q", err, out.GetError())
	}
	defer stopCore(t, s)
	if currentBox() == nil {
		t.Fatal("no running instance after Start")
	}

	out, _ = s.Start(ctx, &gen.LoadConfigReq{CoreConfig: To(simpleConfig)})
	if out.GetError() == "" {
		t.Fatal("second Start must fail")
	}
	if currentBox() == nil {
		t.Fatal("a rejected second Start tore down the running instance")
	}

	stats, err := s.QueryStats(ctx, &gen.EmptyReq{})
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := stats.GetUps()["direct"]; !ok {
		t.Fatalf("stats lack the direct outbound: %v", stats.GetUps())
	}
	if _, err = s.QueryConnections(ctx, &gen.EmptyReq{}); err != nil {
		t.Fatal(err)
	}
}

func TestStopWithoutInstance(t *testing.T) {
	s := &server{}
	stopCore(t, s)
	stopCore(t, s)
}

func TestFailedStartReleasesXray(t *testing.T) {
	s := &server{}
	port := freePort(t)
	out, err := s.Start(context.Background(), &gen.LoadConfigReq{
		CoreConfig:    To(`{"outbounds":[{"type":"nope","tag":"x"}]}`),
		NeedXray:      To(true),
		XrayConfig:    To(xraySocksConfig(port)),
		XrayLazyStart: To(true),
	})
	if err != nil {
		t.Fatal(err)
	}
	if out.GetError() == "" {
		t.Fatal("Start with a broken sing-box config must fail")
	}
	if currentBox() != nil || xrayGate != nil || xrayInstance != nil {
		t.Fatal("failed Start left state behind")
	}
	l, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
	if err != nil {
		t.Fatalf("gate port still taken after failed Start: %v", err)
	}
	_ = l.Close()
}

func TestLazyXrayGateHoldsPort(t *testing.T) {
	s := &server{}
	port := freePort(t)
	out, err := s.Start(context.Background(), &gen.LoadConfigReq{
		CoreConfig:      To(simpleConfig),
		NeedXray:        To(true),
		XrayConfig:      To(xraySocksConfig(port)),
		XrayLazyStart:   To(true),
		XrayIdleSeconds: To(int32(1)),
	})
	if err != nil || out.GetError() != "" {
		t.Fatalf("start: %v %q", err, out.GetError())
	}
	if xrayGate == nil || xrayGate.Instance() != nil {
		t.Fatal("lazy start must keep the Xray instance cold")
	}
	conn, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)), 2*time.Second)
	if err != nil {
		t.Fatalf("gate does not accept on the Xray port: %v", err)
	}
	_ = conn.Close()
	stopCore(t, s)
	l, err := net.Listen("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(port)))
	if err != nil {
		t.Fatalf("gate port still taken after Stop: %v", err)
	}
	_ = l.Close()
}

func TestURLTestThroughThrowawayCore(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusNoContent) }))
	defer srv.Close()

	s := &server{}
	resp, err := s.Test(context.Background(), &gen.TestReq{
		Config:        To(simpleConfig),
		OutboundTags:  []string{"direct"},
		Url:           To(srv.URL),
		TestTimeoutMs: To(int32(3000)),
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetResults()) != 1 {
		t.Fatalf("want one result, got %v", resp.GetResults())
	}
	r := resp.GetResults()[0]
	if r.GetOutboundTag() != "direct" || r.GetError() != "" {
		t.Fatalf("bad result: tag %q error %q", r.GetOutboundTag(), r.GetError())
	}
}

func TestURLTestCurrentWithoutInstance(t *testing.T) {
	s := &server{}
	resp, err := s.Test(context.Background(), &gen.TestReq{TestCurrent: To(true), Url: To("http://192.0.2.1/")})
	if err != nil {
		t.Fatal(err)
	}
	if len(resp.GetResults()) != 1 || resp.GetResults()[0].GetError() == "" {
		t.Fatalf("expected a single error result, got %v", resp.GetResults())
	}
}

func TestGenWgKeyPair(t *testing.T) {
	out, err := (&server{}).GenWgKeyPair(context.Background(), &gen.EmptyReq{})
	if err != nil || out.GetError() != "" {
		t.Fatalf("%v %q", err, out.GetError())
	}
	priv, err := base64.StdEncoding.DecodeString(out.GetPrivateKey())
	if err != nil || len(priv) != 32 {
		t.Fatalf("bad private key %q", out.GetPrivateKey())
	}
	key, err := ecdh.X25519().NewPrivateKey(priv)
	if err != nil {
		t.Fatal(err)
	}
	if want := base64.StdEncoding.EncodeToString(key.PublicKey().Bytes()); out.GetPublicKey() != want {
		t.Fatalf("public key %q does not match private key (want %q)", out.GetPublicKey(), want)
	}
}
