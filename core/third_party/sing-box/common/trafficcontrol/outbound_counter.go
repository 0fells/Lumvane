package trafficcontrol

import (
	"sync/atomic"

	"github.com/sagernet/sing-box/common/compatible"
)

type outboundCounter struct {
	upload   atomic.Int64
	download atomic.Int64
}

type outboundCounters struct {
	counters compatible.Map[string, *outboundCounter]
}

func (c *outboundCounters) load(tag string) *outboundCounter {
	if counter, loaded := c.counters.Load(tag); loaded {
		return counter
	}
	counter, _ := c.counters.LoadOrStore(tag, new(outboundCounter))
	return counter
}

func (m *Manager) TotalOutbound(tag string) (uplink int64, downlink int64) {
	counter, loaded := m.outboundCounters.counters.Load(tag)
	if !loaded {
		return
	}
	return counter.upload.Swap(0), counter.download.Swap(0)
}
