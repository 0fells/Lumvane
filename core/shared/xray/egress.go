package xray

import (
	"context"
	"errors"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
	"weak"

	"github.com/miekg/dns"
	"github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/transport/internet"
	"google.golang.org/protobuf/proto"
)

var egressInterface atomic.Pointer[string]

func SetEgressInterface(name string) {
	egressInterface.Store(&name)
}

func InitEgressInterface(name string) {
	egressInterface.CompareAndSwap(nil, &name)
}

var protectFn atomic.Pointer[func(fd int) bool]

func SetProtect(fn func(fd int) bool) {
	if fn == nil {
		protectFn.Store(nil)
		return
	}
	protectFn.Store(&fn)
}

func protectController(network, address string, c syscall.RawConn) error {
	p := protectFn.Load()
	if p == nil {
		return nil
	}
	var ok bool
	if err := c.Control(func(fd uintptr) { ok = (*p)(int(fd)) }); err != nil {
		return err
	}
	if !ok {
		return errors.New("xray egress: protect(fd) failed for " + address)
	}
	return nil
}

func installDialer() {
	internet.ControllersLock.Lock()
	internet.Controllers = append(internet.Controllers, protectController)
	internet.ControllersLock.Unlock()
	internet.UseAlternativeSystemDialer(&egressDialer{})
}

type instanceWiring struct {
	resolver *Resolver
	strategy internet.DomainStrategy
}

var (
	wiringAccess sync.RWMutex
	wirings      = map[weak.Pointer[core.Instance]]*instanceWiring{}

	profileWiring atomic.Pointer[instanceWiring]
	installOnce   sync.Once
)

func BindEgress(inst *core.Instance, resolver *Resolver, strategy internet.DomainStrategy) {
	installOnce.Do(installDialer)
	wiring := &instanceWiring{resolver: resolver, strategy: strategy}
	key := weak.Make(inst)
	wiringAccess.Lock()
	wirings[key] = wiring
	wiringAccess.Unlock()
	runtime.AddCleanup(inst, func(key weak.Pointer[core.Instance]) {
		wiringAccess.Lock()
		delete(wirings, key)
		wiringAccess.Unlock()
	}, key)
	if resolver != nil {
		profileWiring.Store(wiring)
	}
}

func ClearProfileResolver() {
	profileWiring.Store(nil)
}

func wiringFromContext(ctx context.Context) *instanceWiring {
	if inst := core.FromContext(ctx); inst != nil {
		wiringAccess.RLock()
		wiring := wirings[weak.Make(inst)]
		wiringAccess.RUnlock()
		if wiring != nil {
			return wiring
		}
	}
	return profileWiring.Load()
}

type egressDialer struct {
	internet.DefaultSystemDialer
}

func (d *egressDialer) Dial(ctx context.Context, src net.Address, dest net.Destination, sockopt *internet.SocketConfig) (net.Conn, error) {
	if isLoopback(dest) {
		return d.DefaultSystemDialer.Dial(ctx, src, dest, sockopt)
	}
	ifacePtr := egressInterface.Load()
	if ifacePtr != nil && *ifacePtr == "" {
		return nil, errors.New("xray egress: no default interface available")
	}
	wiring := wiringFromContext(ctx)

	if wiring != nil && wiring.resolver != nil && dest.Address.Family().IsDomain() && wiring.strategy.HasStrategy() {
		ip, err := wiring.resolver.Lookup(ctx, dest.Address.Domain(), wiring.strategy)
		if err == nil {
			dest.Address = net.IPAddress(ip)
		} else if wiring.strategy.ForceIP() {
			return nil, err
		}
	}
	if ifacePtr == nil {
		return d.DefaultSystemDialer.Dial(ctx, src, dest, sockopt)
	}
	bound := &internet.SocketConfig{}
	if sockopt != nil {
		bound = proto.Clone(sockopt).(*internet.SocketConfig)
	}
	bound.Interface = *ifacePtr
	return d.DefaultSystemDialer.Dial(ctx, src, dest, bound)
}

func isLoopback(dest net.Destination) bool {
	if dest.Address.Family().IsDomain() {
		return strings.EqualFold(dest.Address.Domain(), "localhost")
	}
	return dest.Address.IP().IsLoopback()
}

func ParseDomainStrategy(s string) internet.DomainStrategy {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "asis":
		return internet.DomainStrategy_AS_IS
	case "useipv4", "useip4":
		return internet.DomainStrategy_USE_IP4
	case "useipv6", "useip6":
		return internet.DomainStrategy_USE_IP6
	case "useipv4v6", "useip46":
		return internet.DomainStrategy_USE_IP46
	case "useipv6v4", "useip64":
		return internet.DomainStrategy_USE_IP64
	case "forceip":
		return internet.DomainStrategy_FORCE_IP
	case "forceipv4", "forceip4":
		return internet.DomainStrategy_FORCE_IP4
	case "forceipv6", "forceip6":
		return internet.DomainStrategy_FORCE_IP6
	case "forceipv4v6", "forceip46":
		return internet.DomainStrategy_FORCE_IP46
	case "forceipv6v4", "forceip64":
		return internet.DomainStrategy_FORCE_IP64
	default:
		return internet.DomainStrategy_USE_IP
	}
}

const (
	resolverTimeout = 5 * time.Second
	minCacheTTL     = 10 * time.Second
	maxCacheTTL     = 10 * time.Minute
)

type cacheEntry struct {
	ips     []net.IP
	expires time.Time
}

type Resolver struct {
	server string
	client *dns.Client
	access sync.Mutex
	cache  map[string]cacheEntry
}

func NewResolver(server string) (*Resolver, error) {
	if _, _, err := net.SplitHostPort(server); err != nil {
		return nil, err
	}
	return &Resolver{
		server: server,
		client: &dns.Client{Net: "udp", Timeout: resolverTimeout},
		cache:  map[string]cacheEntry{},
	}, nil
}

func (r *Resolver) Lookup(ctx context.Context, domain string, strategy internet.DomainStrategy) (net.IP, error) {
	var order []uint16
	switch {
	case strategy.PreferIP4() && strategy.PreferIP6():
		order = []uint16{dns.TypeA, dns.TypeAAAA}
	case strategy.PreferIP4():
		order = []uint16{dns.TypeA}
		if strategy.FallbackIP6() {
			order = append(order, dns.TypeAAAA)
		}
	default:
		order = []uint16{dns.TypeAAAA}
		if strategy.FallbackIP4() {
			order = append(order, dns.TypeA)
		}
	}
	var lastErr error
	for _, qtype := range order {
		ips, err := r.query(ctx, domain, qtype)
		if err == nil && len(ips) > 0 {
			return ips[0], nil
		}
		lastErr = err
	}
	if lastErr == nil {
		lastErr = errors.New("xray egress: no address for " + domain)
	}
	return nil, lastErr
}

func (r *Resolver) query(ctx context.Context, domain string, qtype uint16) ([]net.IP, error) {
	key := dns.TypeToString[qtype] + " " + strings.ToLower(domain)
	r.access.Lock()
	if entry, ok := r.cache[key]; ok && time.Now().Before(entry.expires) {
		r.access.Unlock()
		return entry.ips, nil
	}
	r.access.Unlock()

	msg := new(dns.Msg)
	msg.SetQuestion(dns.Fqdn(domain), qtype)
	resp, _, err := r.client.ExchangeContext(ctx, msg, r.server)
	if err != nil {
		return nil, err
	}
	if resp.Rcode != dns.RcodeSuccess {
		return nil, errors.New("xray egress: " + dns.RcodeToString[resp.Rcode] + " for " + domain)
	}
	var ips []net.IP
	ttl := maxCacheTTL
	for _, rr := range resp.Answer {
		switch v := rr.(type) {
		case *dns.A:
			ips = append(ips, v.A)
		case *dns.AAAA:
			ips = append(ips, v.AAAA)
		default:
			continue
		}
		if t := time.Duration(rr.Header().Ttl) * time.Second; t < ttl {
			ttl = t
		}
	}
	if len(ips) == 0 {
		return nil, nil
	}
	ttl = max(ttl, minCacheTTL)
	r.access.Lock()
	r.cache[key] = cacheEntry{ips: ips, expires: time.Now().Add(ttl)}
	r.access.Unlock()
	return ips, nil
}
