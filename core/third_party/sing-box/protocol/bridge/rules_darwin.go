package bridge

import (
	"net"
	"net/netip"
	"slices"
	"strconv"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"

	"golang.org/x/sys/unix"
)

func buildBridgeAnchorRules(tunName string, egress string, boundInterface string, inet4Port netip.Addr, inet6Port netip.Addr) ([]pfAnchorRule, error) {
	rules := bridgeDropRules(tunName, inet4Port, inet6Port)
	egressInterface, err := net.InterfaceByName(egress)
	if err != nil {
		return rules, E.Cause(err, "find bridge egress ", egress)
	}
	isCellular := strings.HasPrefix(egressInterface.Name, "pdp_ip")
	isPhysical := egressInterface.Flags&net.FlagBroadcast != 0 && egressInterface.Flags&net.FlagLoopback == 0 &&
		egressInterface.Flags&net.FlagPointToPoint == 0
	if boundInterface == "" && !isCellular && !isPhysical {
		return rules, E.New("bridge egress ", egress, " is not a physical or cellular interface")
	}
	routeWithoutGateway := isCellular || boundInterface != "" && egressInterface.Flags&net.FlagPointToPoint != 0

	mtu := egressInterface.MTU
	if mtu < 576 || mtu > bridgeTunMTUDarwin {
		mtu = bridgeTunMTUDarwin
	}
	localPrefixes, inet4Interfaces, inet6Interfaces := collectLocalSegments(egress, boundInterface, inet4Port.IsValid(), inet6Port.IsValid())
	if inet4Port.IsValid() {
		rules = append(rules, pfScrubRule(egress, inet4Port, uint16(mtu-40)))
	}
	if inet6Port.IsValid() {
		rules = append(rules, pfScrubRule(egress, inet6Port, uint16(mtu-60)))
	}
	if inet4Port.IsValid() {
		rules = append(rules, pfNatRule(egress, inet4Port))
		for _, name := range inet4Interfaces {
			rules = append(rules, pfNatRule(name, inet4Port))
		}
	}
	if inet6Port.IsValid() {
		rules = append(rules, pfNatRule(egress, inet6Port))
		for _, name := range inet6Interfaces {
			rules = append(rules, pfNatRule(name, inet6Port))
		}
	}

	if inet4Port.IsValid() {
		gateway := interfaceGateway(egressInterface.Index, true)
		if gateway.IsValid() || routeWithoutGateway {
			rules = append(rules, pfRouteToRule(tunName, egress, gateway, inet4Port))
			rules = append(rules, pfReplyToRule(tunName, egress, inet4Port))
		}
	}
	if inet6Port.IsValid() {
		gateway := interfaceGateway(egressInterface.Index, false)
		if gateway.IsValid() || routeWithoutGateway {
			rules = append(rules, pfRouteToRule(tunName, egress, gateway, inet6Port))
			rules = append(rules, pfReplyToRule(tunName, egress, inet6Port))
		}
	}

	for _, prefix := range localPrefixes {
		port := inet4Port
		if !prefix.Addr().Is4() {
			port = inet6Port
		}
		rules = append(rules, pfPassInRule(tunName, port, prefix))
	}
	return rules, nil
}

func bridgeDropRules(tunName string, inet4Port netip.Addr, inet6Port netip.Addr) []pfAnchorRule {
	var rules []pfAnchorRule
	if inet4Port.IsValid() {
		rules = append(rules, pfDropInRule(tunName, inet4Port))
	}
	if inet6Port.IsValid() {
		rules = append(rules, pfDropInRule(tunName, inet6Port))
	}
	return rules
}

func collectLocalSegments(egress string, boundInterface string, inet4Active bool, inet6Active bool) (prefixes []netip.Prefix, inet4Interfaces []string, inet6Interfaces []string) {
	localInterfaces, err := net.Interfaces()
	if err != nil {
		return
	}
	for _, localInterface := range localInterfaces {
		if boundInterface != "" && localInterface.Name != boundInterface {
			continue
		}
		if localInterface.Flags&net.FlagUp == 0 || localInterface.Flags&net.FlagBroadcast == 0 ||
			localInterface.Flags&net.FlagLoopback != 0 || localInterface.Flags&net.FlagPointToPoint != 0 {
			continue
		}
		interfaceAddrs, addrsErr := localInterface.Addrs()
		if addrsErr != nil {
			continue
		}
		var (
			hasInet4 bool
			hasInet6 bool
		)
		for _, interfaceAddr := range interfaceAddrs {
			ipNet, isIPNet := interfaceAddr.(*net.IPNet)
			if !isIPNet {
				continue
			}
			address, valid := netip.AddrFromSlice(ipNet.IP)
			if !valid {
				continue
			}
			address = address.Unmap()
			if address.IsLinkLocalUnicast() {
				continue
			}
			if address.Is4() {
				if !inet4Active {
					continue
				}
				hasInet4 = true
			} else {
				if !inet6Active {
					continue
				}
				hasInet6 = true
			}
			bits, _ := ipNet.Mask.Size()
			prefix := netip.PrefixFrom(address, bits).Masked()
			if !slices.Contains(prefixes, prefix) {
				prefixes = append(prefixes, prefix)
			}
		}
		if localInterface.Name == egress {
			continue
		}
		if hasInet4 {
			inet4Interfaces = append(inet4Interfaces, localInterface.Name)
		}
		if hasInet6 {
			inet6Interfaces = append(inet6Interfaces, localInterface.Name)
		}
	}
	return
}

func pfScrubRule(egress string, port netip.Addr, maxMSS uint16) pfAnchorRule {
	rule := pfRule{
		Action: pfActionScrub,
		AF:     pfFamily(port.Is4()),
		Proto:  unix.IPPROTO_TCP,
		MaxMSS: maxMSS,
	}
	copy(rule.IfName[:], egress)
	rule.Src.Addr = pfHostAddress(port)
	return pfAnchorRule{RulesetIndex: pfRulesetScrub, Rule: rule}
}

func pfNatRule(interfaceName string, port netip.Addr) pfAnchorRule {
	rule := pfRule{
		Action: pfActionNat,
		AF:     pfFamily(port.Is4()),
	}
	rule.RPool.ProxyPort = [2]uint16{pfNatProxyPortLow, pfNatProxyPortHigh}
	copy(rule.IfName[:], interfaceName)
	rule.Src.Addr = pfHostAddress(port)
	return pfAnchorRule{
		RulesetIndex: pfRulesetNat,
		Rule:         rule,
		Pool:         pfPoolAddr{Addr: pfDynamicInterfaceAddress(interfaceName, port.Is4())},
	}
}

func pfPassInRule(tunName string, port netip.Addr, destination netip.Prefix) pfAnchorRule {
	rule := pfRule{
		Action:    pfActionPass,
		Direction: pfDirectionIn,
		AF:        pfFamily(port.Is4()),
		KeepState: pfStateNormal,
	}
	copy(rule.IfName[:], tunName)
	rule.Src.Addr = pfHostAddress(port)
	if destination.IsValid() {
		rule.Dst.Addr = pfPrefixAddress(destination)
	}
	return pfAnchorRule{RulesetIndex: pfRulesetFilter, Rule: rule}
}

func pfDropInRule(tunName string, port netip.Addr) pfAnchorRule {
	rule := pfRule{
		Action:    pfActionDrop,
		Direction: pfDirectionIn,
		AF:        pfFamily(port.Is4()),
	}
	copy(rule.IfName[:], tunName)
	rule.Src.Addr = pfHostAddress(port)
	return pfAnchorRule{RulesetIndex: pfRulesetFilter, Rule: rule}
}

func pfRouteToRule(tunName string, egress string, gateway netip.Addr, port netip.Addr) pfAnchorRule {
	anchorRule := pfPassInRule(tunName, port, netip.Prefix{})
	anchorRule.Rule.RouteAction = pfRouteActionRouteTo
	copy(anchorRule.Rule.TagName[:], bridgeTagName(tunName))
	if gateway.IsValid() {
		anchorRule.Pool.Addr = pfHostAddress(gateway)
	}
	copy(anchorRule.Pool.IfName[:], egress)
	return anchorRule
}

func pfReplyToRule(tunName string, egress string, port netip.Addr) pfAnchorRule {
	rule := pfRule{
		Action:      pfActionPass,
		Direction:   pfDirectionOut,
		AF:          pfFamily(port.Is4()),
		KeepState:   pfStateNormal,
		RouteAction: pfRouteActionReplyTo,
	}
	copy(rule.IfName[:], egress)
	copy(rule.MatchTagName[:], bridgeTagName(tunName))
	anchorRule := pfAnchorRule{
		RulesetIndex: pfRulesetFilter,
		Rule:         rule,
		Pool:         pfPoolAddr{Addr: pfHostAddress(port)},
	}
	copy(anchorRule.Pool.IfName[:], tunName)
	return anchorRule
}

func bridgeTagName(tunName string) string {
	return "sing-box-" + tunName
}

func assignBridgePortAddress(tunName string, local netip.Addr, port netip.Addr) error {
	if !port.IsValid() {
		return nil
	}
	err := assignPointToPointAddress(tunName, local, port)
	if err != nil {
		return E.Cause(err, "assign bridge address")
	}
	err = addInterfaceHostRoute(port, tunName)
	if err != nil {
		return E.Cause(err, "add bridge host route")
	}
	return nil
}

func enableDarwinForwarding(forwardingLogger logger.ContextLogger, inet4Active bool, inet6Active bool) []sysctlState {
	var restore []sysctlState
	enable := func(name string) {
		mib := forwardingMibs[name]
		value, err := getSysctlInt32(mib)
		if err != nil {
			forwardingLogger.Debug(E.Cause(err, "read ", name))
			return
		}
		if value == 1 {
			return
		}
		err = setSysctlInt32(mib, 1)
		if err != nil {
			forwardingLogger.Debug(E.Cause(err, "enable ", name))
			return
		}
		restore = append(restore, sysctlState{name: name, value: strconv.Itoa(int(value))})
	}
	if inet4Active {
		enable("net.inet.ip.forwarding")
	}
	if inet6Active {
		enable("net.inet6.ip6.forwarding")
	}
	return restore
}

func restoreDarwinForwarding(states []sysctlState) {
	for _, state := range states {
		value, err := strconv.Atoi(state.value)
		if err != nil {
			continue
		}
		_ = setSysctlInt32(forwardingMibs[state.name], int32(value))
	}
}
