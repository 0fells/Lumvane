package lumcore

import (
	"context"
	"encoding/json"
	"net/netip"
	"os"
	"slices"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

type Platform interface {
	Protect(fd int32) bool

	OpenTun(optionsJSON string) (int32, error)

	InterfacesJSON() (string, error)

	StartDefaultInterfaceMonitor(listener *InterfaceListener) error
	CloseDefaultInterfaceMonitor() error

	FindConnectionOwner(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (string, error)
}

type InterfaceListener struct {
	hub *monitorHub
}

func (l *InterfaceListener) Update(name string, index int32, expensive bool, constrained bool) {
	l.hub.push(name, index, expensive, constrained)
}

type httpProxyJSON struct {
	Server       string   `json:"server"`
	Port         int32    `json:"port"`
	BypassDomain []string `json:"bypass_domain"`
	MatchDomain  []string `json:"match_domain"`
}

type tunOptionsJSON struct {
	Inet4Address             []string       `json:"inet4_address"`
	Inet6Address             []string       `json:"inet6_address"`
	DNSMode                  string         `json:"dns_mode"`
	DNSServers               []string       `json:"dns_servers"`
	MTU                      int32          `json:"mtu"`
	AutoRoute                bool           `json:"auto_route"`
	StrictRoute              bool           `json:"strict_route"`
	Inet4RouteAddress        []string       `json:"inet4_route_address"`
	Inet6RouteAddress        []string       `json:"inet6_route_address"`
	Inet4RouteExcludeAddress []string       `json:"inet4_route_exclude_address"`
	Inet6RouteExcludeAddress []string       `json:"inet6_route_exclude_address"`
	Inet4RouteRange          []string       `json:"inet4_route_range"`
	Inet6RouteRange          []string       `json:"inet6_route_range"`
	IncludePackage           []string       `json:"include_package"`
	ExcludePackage           []string       `json:"exclude_package"`
	HTTPProxy                *httpProxyJSON `json:"http_proxy,omitempty"`
}

func prefixStrings(prefixes []netip.Prefix) []string {
	return common.Map(prefixes, netip.Prefix.String)
}

type interfaceJSON struct {
	Index     int32    `json:"index"`
	MTU       int32    `json:"mtu"`
	Name      string   `json:"name"`
	Addresses []string `json:"addresses"`
	Flags     uint32   `json:"flags"`
	Type      int32    `json:"type"`
	DNS       []string `json:"dns"`
	Gateway   []string `json:"gateway"`
	Metered   bool     `json:"metered"`
}

var _ adapter.PlatformInterface = (*platformWrapper)(nil)

type platformWrapper struct {
	iif            Platform
	hub            *monitorHub
	networkManager adapter.NetworkManager

	access        sync.Mutex
	myTunName     string
	myTunAddress  []netip.Addr
	defaultIface  *control.Interface
	isExpensive   bool
	isConstrained bool
}

func (w *platformWrapper) Initialize(networkManager adapter.NetworkManager) error {
	w.networkManager = networkManager
	return nil
}

func (w *platformWrapper) UsePlatformAutoDetectInterfaceControl() bool { return true }

func (w *platformWrapper) AutoDetectInterfaceControl(fd int) error {
	if !w.iif.Protect(int32(fd)) {
		return E.New("protect(", fd, ") failed")
	}
	return nil
}

func (w *platformWrapper) UsePlatformInterface() bool { return true }

func (w *platformWrapper) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("platform: unsupported uid options")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("platform: unsupported android_user option")
	}
	routeRanges, err := options.BuildAutoRouteRanges(true)
	if err != nil {
		return nil, err
	}
	dnsServers, err := options.DNSServerAddress()
	if err != nil {
		return nil, err
	}
	desc := tunOptionsJSON{
		Inet4Address:             prefixStrings(options.Inet4Address),
		Inet6Address:             prefixStrings(options.Inet6Address),
		DNSMode:                  options.DNSMode,
		DNSServers:               common.Map(dnsServers, netip.Addr.String),
		MTU:                      int32(options.MTU),
		AutoRoute:                options.AutoRoute,
		StrictRoute:              options.StrictRoute,
		Inet4RouteAddress:        prefixStrings(options.Inet4RouteAddress),
		Inet6RouteAddress:        prefixStrings(options.Inet6RouteAddress),
		Inet4RouteExcludeAddress: prefixStrings(options.Inet4RouteExcludeAddress),
		Inet6RouteExcludeAddress: prefixStrings(options.Inet6RouteExcludeAddress),
		Inet4RouteRange: prefixStrings(common.Filter(routeRanges, func(it netip.Prefix) bool {
			return it.Addr().Is4()
		})),
		Inet6RouteRange: prefixStrings(common.Filter(routeRanges, func(it netip.Prefix) bool {
			return it.Addr().Is6()
		})),
		IncludePackage: options.IncludePackage,
		ExcludePackage: options.ExcludePackage,
	}
	if hp := platformOptions.HTTPProxy; hp != nil && hp.Enabled {
		desc.HTTPProxy = &httpProxyJSON{
			Server:       hp.Server,
			Port:         int32(hp.ServerPort),
			BypassDomain: hp.BypassDomain,
			MatchDomain:  hp.MatchDomain,
		}
	}
	raw, err := json.Marshal(desc)
	if err != nil {
		return nil, err
	}
	tunFd, err := w.iif.OpenTun(string(raw))
	if err != nil {
		return nil, err
	}
	options.Name, err = getTunnelName(tunFd)
	if err != nil {
		return nil, E.Cause(err, "query tun name")
	}
	options.InterfaceMonitor.RegisterMyInterface(options.Name)
	dupFd, err := dup(int(tunFd))
	if err != nil {
		return nil, E.Cause(err, "dup tun file descriptor")
	}
	options.FileDescriptor = dupFd
	w.access.Lock()
	w.myTunName = options.Name
	w.myTunAddress = nil
	for _, p := range options.Inet4Address {
		w.myTunAddress = append(w.myTunAddress, p.Addr())
	}
	for _, p := range options.Inet6Address {
		w.myTunAddress = append(w.myTunAddress, p.Addr())
	}
	w.access.Unlock()
	return tun.New(*options)
}

func (w *platformWrapper) ProcessPlatformOptions(option.TunPlatformOptions) error { return nil }

func (w *platformWrapper) MyInterfaceAddress() []netip.Addr {
	w.access.Lock()
	defer w.access.Unlock()
	return w.myTunAddress
}

func (w *platformWrapper) UsePlatformDefaultInterfaceMonitor() bool { return true }

func (w *platformWrapper) CreateDefaultInterfaceMonitor(l logger.Logger) tun.DefaultInterfaceMonitor {
	return &defaultInterfaceMonitor{platformWrapper: w, logger: l}
}

func (w *platformWrapper) UsePlatformNetworkInterfaces() bool { return true }

func (w *platformWrapper) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	raw, err := w.iif.InterfacesJSON()
	if err != nil {
		return nil, err
	}
	var items []interfaceJSON
	if err = json.Unmarshal([]byte(raw), &items); err != nil {
		return nil, E.Cause(err, "decode interfaces")
	}
	w.access.Lock()
	defIdx := -1
	if w.defaultIface != nil {
		defIdx = w.defaultIface.Index
	}
	myTun := w.myTunName
	expensive, constrained := w.isExpensive, w.isConstrained
	w.access.Unlock()
	var out []adapter.NetworkInterface
	for _, it := range items {
		isDefault := it.Name != myTun && int(it.Index) == defIdx
		addrs := make([]netip.Prefix, 0, len(it.Addresses))
		for _, a := range it.Addresses {
			if p, e := netip.ParsePrefix(a); e == nil {
				addrs = append(addrs, p)
			}
		}
		out = append(out, adapter.NetworkInterface{
			Interface: control.Interface{
				Index:     int(it.Index),
				MTU:       int(it.MTU),
				Name:      it.Name,
				Addresses: addrs,
				Flags:     linkFlags(it.Flags),
			},
			Type:       toInterfaceType(it.Type),
			DNSServers: it.DNS,
			Gateways: common.Filter(common.Map(it.Gateway, func(s string) netip.Addr {
				g, _ := netip.ParseAddr(s)
				return g.Unmap().WithZone("")
			}), netip.Addr.IsValid),
			Expensive:   it.Metered || isDefault && expensive,
			Constrained: isDefault && constrained,
		})
	}
	return common.UniqBy(out, func(it adapter.NetworkInterface) string { return it.Name }), nil
}

func (w *platformWrapper) UnderNetworkExtension() bool              { return false }
func (w *platformWrapper) NetworkExtensionIncludeAllNetworks() bool { return false }
func (w *platformWrapper) ClearDNSCache()                           {}
func (w *platformWrapper) RequestPermissionForWIFIState() error     { return nil }
func (w *platformWrapper) ReadWIFIState(context.Context) adapter.WIFIState {
	return adapter.WIFIState{}
}
func (w *platformWrapper) UsePlatformWIFIMonitor() bool { return false }

func (w *platformWrapper) UsePlatformConnectionOwnerFinder() bool { return true }
func (w *platformWrapper) FindConnectionOwner(r *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	unknown := &adapter.ConnectionOwner{UserId: -1}
	raw, err := w.iif.FindConnectionOwner(r.IpProtocol, r.SourceAddress, r.SourcePort, r.DestinationAddress, r.DestinationPort)
	if err != nil || raw == "" {
		return unknown, nil
	}
	var res struct {
		UID      int32    `json:"uid"`
		Packages []string `json:"packages"`
	}
	if json.Unmarshal([]byte(raw), &res) != nil {
		return unknown, nil
	}
	return &adapter.ConnectionOwner{UserId: res.UID, AndroidPackageNames: res.Packages}, nil
}
func (w *platformWrapper) UsePlatformNotification() bool                { return false }
func (w *platformWrapper) SendNotification(*adapter.Notification) error { return nil }
func (w *platformWrapper) CancelNotification(string, int32) error       { return nil }
func (w *platformWrapper) UsePlatformNeighborResolver() bool            { return false }
func (w *platformWrapper) StartNeighborMonitor(adapter.NeighborUpdateListener) error {
	return os.ErrInvalid
}
func (w *platformWrapper) CloseNeighborMonitor(adapter.NeighborUpdateListener) error { return nil }
func (w *platformWrapper) UsePlatformShell() bool                                    { return false }
func (w *platformWrapper) CheckPlatformShell() error                                 { return nil }
func (w *platformWrapper) OpenShellSession(*adapter.PlatformUser, string, []string, string, int32, int32) (adapter.ShellSession, error) {
	return nil, os.ErrInvalid
}
func (w *platformWrapper) LookupUser(string) (*adapter.PlatformUser, error) {
	return nil, os.ErrInvalid
}
func (w *platformWrapper) LookupSFTPServer() (string, error)     { return "", os.ErrInvalid }
func (w *platformWrapper) ReadSystemSSHHostKey() ([]byte, error) { return nil, os.ErrInvalid }
func (w *platformWrapper) TailscaleHostname() string             { return "" }
func (w *platformWrapper) UsePlatformBridge() bool               { return false }
func (w *platformWrapper) CreateBridge(adapter.BridgeOptions) (adapter.BridgeSession, error) {
	return nil, os.ErrInvalid
}

type defaultInterfaceMonitor struct {
	*platformWrapper
	logger       logger.Logger
	callbacks    list.List[tun.DefaultInterfaceUpdateCallback]
	myInterfaces []string
	initialized  bool
	dnsServers   []string
}

var _ tun.DefaultInterfaceMonitor = (*defaultInterfaceMonitor)(nil)

func (m *defaultInterfaceMonitor) Start() error {
	return m.hub.subscribe(m)
}

func (m *defaultInterfaceMonitor) Close() error { return m.hub.unsubscribe(m) }

func (m *defaultInterfaceMonitor) DefaultInterface() *control.Interface {
	m.access.Lock()
	defer m.access.Unlock()
	return m.defaultIface
}

func (m *defaultInterfaceMonitor) OverrideAndroidVPN() bool { return false }
func (m *defaultInterfaceMonitor) AndroidVPNEnabled() bool  { return false }

func (m *defaultInterfaceMonitor) RegisterCallback(cb tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.access.Lock()
	defer m.access.Unlock()
	return m.callbacks.PushBack(cb)
}

func (m *defaultInterfaceMonitor) UnregisterCallback(el *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.access.Lock()
	defer m.access.Unlock()
	m.callbacks.Remove(el)
}

func (m *defaultInterfaceMonitor) RegisterMyInterface(name string) {
	m.access.Lock()
	defer m.access.Unlock()
	m.myInterfaces = append(m.myInterfaces, name)
}

func (m *defaultInterfaceMonitor) MyInterfaces() []string {
	m.access.Lock()
	defer m.access.Unlock()
	return m.myInterfaces
}

func (m *defaultInterfaceMonitor) update(name string, index int32, expensive, constrained bool) {
	m.access.Lock()
	m.isExpensive, m.isConstrained = expensive, constrained
	m.access.Unlock()
	if m.networkManager == nil {
		return
	}
	if err := m.networkManager.UpdateInterfaces(); err != nil {
		m.logger.Error(E.Cause(err, "update interfaces"))
	}
	m.access.Lock()
	if index == -1 {
		m.defaultIface, m.dnsServers, m.initialized = nil, nil, true
		cbs := m.callbacks.Array()
		m.access.Unlock()
		for _, cb := range cbs {
			cb(nil, 0)
		}
		return
	}
	old := m.defaultIface
	next, err := m.networkManager.InterfaceFinder().ByIndex(int(index))
	if err != nil {
		m.access.Unlock()
		m.logger.Error(E.Cause(err, "find updated interface: ", name))
		return
	}
	oldDNS := m.dnsServers
	m.defaultIface = next
	m.dnsServers = common.Find(m.networkManager.NetworkInterfaces(), func(it adapter.NetworkInterface) bool {
		return it.Index == next.Index
	}).DNSServers
	if m.initialized && old != nil && old.Name == next.Name && old.Index == next.Index && slices.Equal(oldDNS, m.dnsServers) {
		m.access.Unlock()
		return
	}
	m.initialized = true
	cbs := m.callbacks.Array()
	m.access.Unlock()
	for _, cb := range cbs {
		cb(next, 0)
	}
}
