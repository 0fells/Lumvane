package lumcore

import (
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"
)

const simpleConfig = `{
  "log": {"level": "info"},
  "inbounds": [{"type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 0}],
  "outbounds": [{"type": "direct", "tag": "direct"}],
  "route": {"final": "direct"}
}`

func TestCheckConfigOK(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	if err := CheckConfig(simpleConfig); err != nil {
		t.Fatalf("valid config rejected: %v", err)
	}
}

func TestCheckConfigErrors(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	for name, cfg := range map[string]string{
		"garbage":          `{not json`,
		"unknown outbound": `{"outbounds":[{"type":"nope","tag":"x"}]}`,
		"missing detour":   `{"outbounds":[{"type":"direct","tag":"d","detour":"zzz"}]}`,
	} {
		if err := CheckConfig(cfg); err == nil {
			t.Errorf("%s: expected error", name)
		}
	}
}

func TestProtocolsRegistered(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	for _, typ := range []string{"vless", "vmess", "trojan", "shadowsocks", "socks", "http", "anytls",
		"hysteria", "hysteria2", "juicity", "mieru", "shadowtls", "ssh", "trusttunnel", "tuic", "selector", "urltest"} {
		cfg := `{"outbounds":[{"type":"` + typ + `","tag":"x"}]}`
		err := CheckConfig(cfg)
		if err != nil && strings.Contains(err.Error(), "unknown outbound type") {
			t.Errorf("outbound type %q is not compiled in: %v", typ, err)
		}
	}
	for _, typ := range []string{"wireguard", "tailscale"} {
		err := CheckConfig(`{"endpoints":[{"type":"` + typ + `","tag":"x"}]}`)
		if err != nil && strings.Contains(err.Error(), "unknown endpoint type") {
			t.Errorf("endpoint type %q is not compiled in: %v", typ, err)
		}
	}
}

const xrayRealityXHTTP = `{
  "inbounds": [{"listen": "127.0.0.1", "port": 10808, "protocol": "socks", "settings": {"udp": true}}],
  "outbounds": [{
    "protocol": "vless",
    "settings": {"vnext": [{"address": "example.com", "port": 443,
      "users": [{"id": "5783a3e7-e373-51cd-8642-c83782b807c5", "encryption": "none", "flow": ""}]}]},
    "streamSettings": {
      "network": "xhttp", "security": "reality",
      "realitySettings": {"serverName": "example.com", "fingerprint": "chrome",
        "publicKey": "q0KuF4o4a5QxVQ6PrjWQcMiOlAeS4hzmNa4VU2Zl3Fc", "shortId": "0123456789abcdef"},
      "xhttpSettings": {"path": "/x", "mode": "auto",
        "extra": {"xmux": {"maxConcurrency": "16-32", "cMaxReuseTimes": "64-128"}}}
    }
  }]
}`

func TestCheckXrayConfigRealityXHTTP(t *testing.T) {
	if err := CheckXrayConfig(xrayRealityXHTTP); err != nil {
		t.Fatalf("vless+reality+xhttp(xmux) rejected: %v", err)
	}
	if err := CheckXrayConfig(`{"outbounds":[{"protocol":"vless","settings":{}}]}`); err == nil {
		t.Fatal("expected error for broken vless")
	}
}

func TestStartStopStatsAndURLTest(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer srv.Close()

	out, err := URLTest(&TestOptions{CoreConfig: simpleConfig, URL: srv.URL, Tags: "direct", TimeoutMs: 3000})
	if err != nil {
		t.Fatal(err)
	}
	var res []urlTestResult
	if err = json.Unmarshal([]byte(out), &res); err != nil || len(res) != 1 || res[0].Error != "" || res[0].Tag != "direct" {
		t.Fatalf("bad url test result %s (%v)", out, err)
	}

	if err = Start(&StartOptions{CoreConfig: simpleConfig}); err != nil {
		t.Fatal(err)
	}
	if !IsRunning() {
		t.Fatal("not running")
	}
	if err = Start(&StartOptions{CoreConfig: simpleConfig}); err == nil {
		t.Fatal("second Start must fail")
	}
	stats, err := QueryStats()
	if err != nil || !strings.Contains(stats, "total_up") {
		t.Fatalf("stats: %s %v", stats, err)
	}
	if _, err = URLTest(&TestOptions{Current: true, URL: srv.URL}); err != nil {
		t.Fatal(err)
	}
	if err = Stop(); err != nil || IsRunning() {
		t.Fatalf("stop: %v running=%v", err, IsRunning())
	}
	if err = Stop(); err != nil {
		t.Fatal(err)
	}
}

func TestQueryConnectionsSeesProxiedRequest(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte("hello")) }))
	defer srv.Close()

	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := l.Addr().(*net.TCPAddr).Port
	_ = l.Close()
	cfg := strings.Replace(simpleConfig, `"listen_port": 0`, `"listen_port": `+strconv.Itoa(port), 1)
	if err = Start(&StartOptions{CoreConfig: cfg}); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = Stop() }()

	proxy, _ := url.Parse("http://127.0.0.1:" + strconv.Itoa(port))
	client := &http.Client{Transport: &http.Transport{Proxy: http.ProxyURL(proxy)}, Timeout: 5 * time.Second}
	resp, err := client.Get(srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = io.ReadAll(resp.Body)
	_ = resp.Body.Close()

	raw, err := QueryConnections(0)
	if err != nil {
		t.Fatal(err)
	}
	var out struct {
		Active []connectionInfo `json:"active"`
		Closed []connectionInfo `json:"closed"`
	}
	if err = json.Unmarshal([]byte(raw), &out); err != nil {
		t.Fatal(err)
	}
	target := strings.TrimPrefix(srv.URL, "http://")
	for _, c := range append(out.Active, out.Closed...) {
		if c.Dest == target {
			if c.Outbound != "direct" || c.Download == 0 || c.UID != -1 || c.ID == "" {
				t.Fatalf("unexpected connection %+v", c)
			}
			return
		}
	}
	t.Fatalf("no connection to %s in %s", target, raw)
}

func TestQueryConnectionsClosedFilter(t *testing.T) {
	Init(t.TempDir(), t.TempDir(), nil, nil)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.Header().Set("Connection", "close") }))
	defer srv.Close()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := l.Addr().(*net.TCPAddr).Port
	_ = l.Close()
	if err = Start(&StartOptions{CoreConfig: strings.Replace(simpleConfig, `"listen_port": 0`, `"listen_port": `+strconv.Itoa(port), 1)}); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = Stop() }()
	proxy, _ := url.Parse("http://127.0.0.1:" + strconv.Itoa(port))
	client := &http.Client{Transport: &http.Transport{Proxy: http.ProxyURL(proxy), DisableKeepAlives: true}, Timeout: 5 * time.Second}
	resp, err := client.Get(srv.URL)
	if err != nil {
		t.Fatal(err)
	}
	_ = resp.Body.Close()

	closedCount := func(since int64) int {
		raw, err := QueryConnections(since)
		if err != nil {
			t.Fatal(err)
		}
		var out struct {
			Closed []connectionInfo `json:"closed"`
		}
		if err = json.Unmarshal([]byte(raw), &out); err != nil {
			t.Fatal(err)
		}
		return len(out.Closed)
	}
	deadline := time.Now().Add(3 * time.Second)
	for closedCount(0) == 0 {
		if time.Now().After(deadline) {
			t.Fatal("the proxied connection never showed up as closed")
		}
		time.Sleep(50 * time.Millisecond)
	}
	if n := closedCount(-1); n != 0 {
		t.Fatalf("negative since must skip closed connections, got %d", n)
	}
	if n := closedCount(time.Now().Add(time.Hour).UnixMilli()); n != 0 {
		t.Fatalf("connections closed before the mark must be skipped, got %d", n)
	}
}

func TestQueryConnectionsWithoutInstance(t *testing.T) {
	raw, err := QueryConnections(0)
	if err != nil || raw != `{"active":[],"closed":[]}` {
		t.Fatalf("got %s %v", raw, err)
	}
}
