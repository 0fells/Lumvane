package lumcore

import (
	"encoding/json"
	"errors"
	"sort"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/trafficcontrol"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/service"

	"lumshared/wg"
)

func trafficManager() *trafficcontrol.Manager {
	box := currentBox()
	if box == nil {
		return nil
	}
	return service.PtrFromContext[trafficcontrol.Manager](box.Context())
}

func totals() (up, down int64) {
	if tm := trafficManager(); tm != nil {
		return tm.Total()
	}
	return 0, 0
}

func QueryStats() (string, error) {
	out := struct {
		TotalUp   int64            `json:"total_up"`
		TotalDown int64            `json:"total_down"`
		Ups       map[string]int64 `json:"ups"`
		Downs     map[string]int64 `json:"downs"`
	}{Ups: map[string]int64{}, Downs: map[string]int64{}}
	box := currentBox()
	if box != nil {
		if tm := service.PtrFromContext[trafficcontrol.Manager](box.Context()); tm != nil {
			out.TotalUp, out.TotalDown = tm.Total()
			if outbounds := service.FromContext[adapter.OutboundManager](box.Context()); outbounds != nil {
				for _, ob := range outbounds.Outbounds() {
					out.Ups[ob.Tag()], out.Downs[ob.Tag()] = tm.TotalOutbound(ob.Tag())
				}
			}
			if endpoints := service.FromContext[adapter.EndpointManager](box.Context()); endpoints != nil {
				for _, ep := range endpoints.Endpoints() {
					out.Ups[ep.Tag()], out.Downs[ep.Tag()] = tm.TotalOutbound(ep.Tag())
				}
			}
		}
	}
	raw, err := json.Marshal(out)
	return string(raw), err
}

type connectionInfo struct {
	ID        string   `json:"id"`
	CreatedAt int64    `json:"created_at"`
	ClosedAt  int64    `json:"closed_at,omitempty"`
	Upload    int64    `json:"upload"`
	Download  int64    `json:"download"`
	Network   string   `json:"network"`
	Dest      string   `json:"dest"`
	Domain    string   `json:"domain,omitempty"`
	Protocol  string   `json:"protocol,omitempty"`
	Outbound  string   `json:"outbound"`
	Chain     []string `json:"chain,omitempty"`
	Rule      string   `json:"rule,omitempty"`
	UID       int32    `json:"uid"`
	Packages  []string `json:"packages,omitempty"`
}

func connectionInfoOf(c *trafficcontrol.TrackerMetadata, closed bool) connectionInfo {
	info := connectionInfo{
		ID:        c.ID.String(),
		CreatedAt: c.CreatedAt.UnixMilli(),
		Upload:    c.Upload.Load(),
		Download:  c.Download.Load(),
		Network:   c.Metadata.Network,
		Dest:      c.Metadata.Destination.String(),
		Domain:    c.Metadata.Domain,
		Protocol:  c.Metadata.Protocol,
		Outbound:  c.Outbound,
		Chain:     c.Chain,
		UID:       -1,
	}
	if closed && !c.ClosedAt.IsZero() {
		info.ClosedAt = c.ClosedAt.UnixMilli()
	}
	if c.Rule != nil {
		info.Rule = c.Rule.String()
	}
	if owner := c.Metadata.ProcessInfo; owner != nil {
		info.UID = owner.UserId
		info.Packages = owner.AndroidPackageNames
	}
	return info
}

func QueryConnections(closedSinceMs int64) (string, error) {
	out := struct {
		Active []connectionInfo `json:"active"`
		Closed []connectionInfo `json:"closed"`
	}{Active: []connectionInfo{}, Closed: []connectionInfo{}}
	if tm := trafficManager(); tm != nil {
		for _, c := range tm.Connections() {
			out.Active = append(out.Active, connectionInfoOf(c, false))
		}
		if closedSinceMs >= 0 {
			for _, c := range tm.ClosedConnections() {
				if c.ClosedAt.UnixMilli() > closedSinceMs {
					out.Closed = append(out.Closed, connectionInfoOf(c, true))
				}
			}
		}
	}
	raw, err := json.Marshal(out)
	return string(raw), err
}

func autoSelectors() map[string]group.AutoSelectorGroup {
	found := map[string]group.AutoSelectorGroup{}
	box := currentBox()
	if box == nil {
		return found
	}
	outbounds := service.FromContext[adapter.OutboundManager](box.Context())
	if outbounds == nil {
		return found
	}
	for _, ob := range outbounds.Outbounds() {
		if sel, ok := ob.(group.AutoSelectorGroup); ok {
			found[ob.Tag()] = sel
		}
	}
	return found
}

func QueryAutoSelectors() (string, error) {
	sels := autoSelectors()
	tags := make([]string, 0, len(sels))
	for t := range sels {
		tags = append(tags, t)
	}
	sort.Strings(tags)
	statuses := make([]group.AutoSelectorStatus, 0, len(tags))
	for _, t := range tags {
		statuses = append(statuses, sels[t].Status())
	}
	raw, err := json.Marshal(statuses)
	return string(raw), err
}

func AutoSelectorAction(tag string, action string, member string) error {
	sels := autoSelectors()
	if len(sels) == 0 {
		return errors.New("no auto selector is running")
	}
	var targets []group.AutoSelectorGroup
	if tag != "" {
		s, ok := sels[tag]
		if !ok {
			return errors.New("no auto selector with tag " + tag)
		}
		targets = append(targets, s)
	} else {
		for _, s := range sels {
			targets = append(targets, s)
		}
	}
	switch action {
	case "recheck":
		for _, s := range targets {
			s.CheckOutbounds()
		}
	case "select":
		for _, s := range targets {
			if !s.SelectOutbound(member) {
				return errors.New("no member " + member + " in group " + s.Tag())
			}
		}
	default:
		return errors.New("unknown auto selector action: " + action)
	}
	return nil
}

func GenWgKeyPair() (string, error) {
	priv, err := wg.GeneratePrivateKey()
	if err != nil {
		return "", err
	}
	return priv.String() + "\n" + priv.PublicKey().String(), nil
}
