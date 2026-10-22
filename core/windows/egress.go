package main

import (
	"LumvaneCore/internal/boxdns"
	"lumshared/xray"

	"github.com/xtls/xray-core/core"
	xinternet "github.com/xtls/xray-core/transport/internet"
)

func defaultInterfaceFinder() string {
	ifc := boxdns.DefaultInterface()
	if ifc == nil {
		return ""
	}
	return ifc.Name
}

func bindXrayEgress(inst *core.Instance, resolver *xray.Resolver, strategy xinternet.DomainStrategy) {
	xray.InitEgressInterface(defaultInterfaceFinder())
	xray.BindEgress(inst, resolver, strategy)
}
