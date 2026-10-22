package lumcore

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"lumshared/xray"
)

func TestXrayOutboundSocketsAreProtected(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { _, _ = io.WriteString(w, "ok") }))
	defer srv.Close()
	host, port, _ := net.SplitHostPort(srv.Listener.Addr().String())

	free, _ := net.Listen("tcp", "127.0.0.1:0")
	inPort := free.Addr().(*net.TCPAddr).Port
	free.Close()

	var protected atomic.Int32
	xray.SetProtect(func(fd int) bool { protected.Add(1); return fd > 0 })
	defer xray.SetProtect(nil)

	cfg := fmt.Sprintf(`{"inbounds":[{"listen":"127.0.0.1","port":%d,"protocol":"dokodemo-door",
	  "settings":{"address":"%s","port":%s,"network":"tcp"}}],"outbounds":[{"protocol":"freedom"}]}`, inPort, host, port)
	inst, err := xray.CreateXrayInstance(cfg)
	if err != nil {
		t.Fatal(err)
	}
	xray.BindEgress(inst, nil, 0)
	if err = inst.Start(); err != nil {
		t.Fatal(err)
	}
	defer inst.Close()

	resp, err := http.Get(fmt.Sprintf("http://127.0.0.1:%d/", inPort))
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if string(body) != "ok" {
		t.Fatalf("unexpected body %q", body)
	}
	if protected.Load() == 0 {
		t.Fatal("protect hook was never called for the Xray outbound socket")
	}
}
