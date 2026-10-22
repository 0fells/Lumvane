package group

import (
	"context"
	"net"
	"testing"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

type fakeOutbound struct{ tag string }

func (f *fakeOutbound) Type() string           { return "fake" }
func (f *fakeOutbound) Tag() string            { return f.tag }
func (f *fakeOutbound) Network() []string      { return []string{N.NetworkTCP, N.NetworkUDP} }
func (f *fakeOutbound) Dependencies() []string { return nil }
func (f *fakeOutbound) DialContext(context.Context, string, M.Socksaddr) (net.Conn, error) {
	return nil, net.ErrClosed
}
func (f *fakeOutbound) ListenPacket(context.Context, M.Socksaddr) (net.PacketConn, error) {
	return nil, net.ErrClosed
}

func TestURLTestPreferFirst(t *testing.T) {
	a, b, c := &fakeOutbound{"a"}, &fakeOutbound{"b"}, &fakeOutbound{"c"}
	h := urltest.NewHistoryStorage()
	g := &URLTestGroup{outbounds: []adapter.Outbound{a, b, c}, tolerance: 50, history: h, preferFirst: true}
	set := func(tag string, d uint16) {
		h.StoreURLTestHistory(tag, &adapter.URLTestHistory{Time: time.Now(), Delay: d})
	}
	pick := func() string {
		o, _ := g.Select(N.NetworkTCP)
		return o.Tag()
	}

	set("a", 300)
	set("b", 100)
	set("c", 40)
	if got := pick(); got != "a" {
		t.Fatalf("first alive must win over faster ones, got %s", got)
	}

	h.DeleteURLTestHistory("a")
	if got := pick(); got != "c" {
		t.Fatalf("first down: fastest of the rest expected, got %s", got)
	}

	g.selectedOutboundTCP = c
	set("a", 500)
	if got := pick(); got != "a" {
		t.Fatalf("first back: must return to it, got %s", got)
	}

	g.preferFirst = false
	g.selectedOutboundTCP = nil
	if got := pick(); got != "c" {
		t.Fatalf("without prefer_first the fastest wins, got %s", got)
	}
}
