package lumcore

import (
	"encoding/json"
	"errors"
	"sort"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/service"
)

func urlTestGroups() map[string]*group.URLTest {
	found := map[string]*group.URLTest{}
	box := currentBox()
	if box == nil {
		return found
	}
	outbounds := service.FromContext[adapter.OutboundManager](box.Context())
	if outbounds == nil {
		return found
	}
	for _, ob := range outbounds.Outbounds() {
		if g, ok := ob.(*group.URLTest); ok {
			found[ob.Tag()] = g
		}
	}
	return found
}

type urlTestMemberJSON struct {
	Tag string `json:"tag"`

	DelayMs   int   `json:"delay_ms"`
	Alive     bool  `json:"alive"`
	CheckedAt int64 `json:"checked_at"`
}

type urlTestGroupJSON struct {
	Tag     string              `json:"tag"`
	Now     string              `json:"now"`
	Members []urlTestMemberJSON `json:"members"`
}

func urlTestNow(g *group.URLTest) (now string) {
	defer func() {
		if recover() != nil {
			now = ""
		}
	}()
	return g.Now()
}

func QueryUrlTestGroups() (string, error) {
	groups := urlTestGroups()
	tags := make([]string, 0, len(groups))
	for t := range groups {
		tags = append(tags, t)
	}
	sort.Strings(tags)
	var history *urltest.HistoryStorage
	if box := currentBox(); box != nil {
		history = service.PtrFromContext[urltest.HistoryStorage](box.Context())
	}
	out := make([]urlTestGroupJSON, 0, len(tags))
	for _, t := range tags {
		g := groups[t]
		item := urlTestGroupJSON{Tag: t, Now: urlTestNow(g), Members: []urlTestMemberJSON{}}
		for _, m := range g.All() {
			member := urlTestMemberJSON{Tag: m}
			if h := history.LoadURLTestHistory(m); h != nil {
				member.Alive = true
				member.DelayMs = int(h.Delay)
				member.CheckedAt = h.Time.UnixMilli()
			}
			item.Members = append(item.Members, member)
		}
		out = append(out, item)
	}
	raw, err := json.Marshal(out)
	return string(raw), err
}

func UrlTestRecheck(tag string) error {
	groups := urlTestGroups()
	if len(groups) == 0 {
		return errors.New("no urltest group is running")
	}
	if tag != "" {
		g, ok := groups[tag]
		if !ok {
			return errors.New("no urltest group with tag " + tag)
		}
		go g.CheckOutbounds()
		return nil
	}
	for _, g := range groups {
		go g.CheckOutbounds()
	}
	return nil
}
