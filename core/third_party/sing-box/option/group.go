package option

import "github.com/sagernet/sing/common/json/badoption"

type SelectorOutboundOptions struct {
	Outbounds                 []string `json:"outbounds" reference:"outbound"`
	Default                   string   `json:"default,omitempty" reference:"outbound"`
	InterruptExistConnections bool     `json:"interrupt_exist_connections,omitempty"`
}

type URLTestOutboundOptions struct {
	Outbounds                 []string           `json:"outbounds" reference:"outbound"`
	URL                       string             `json:"url,omitempty"`
	Interval                  badoption.Duration `json:"interval,omitempty"`
	Tolerance                 uint16             `json:"tolerance,omitempty"`
	IdleTimeout               badoption.Duration `json:"idle_timeout,omitempty"`
	InterruptExistConnections bool               `json:"interrupt_exist_connections,omitempty"`

	PreferFirst bool `json:"prefer_first,omitempty"`
}

type AutoSelectorOutboundOptions struct {
	Outbounds []string `json:"outbounds"`

	URL string `json:"url,omitempty"`

	ConnectivityURL string `json:"connectivity_url,omitempty"`

	Interval      badoption.Duration `json:"interval,omitempty"`
	BenchInterval badoption.Duration `json:"bench_interval,omitempty"`
	ActiveSize    int                `json:"active_size,omitempty"`
	Sampling      int                `json:"sampling,omitempty"`
	Timeout       badoption.Duration `json:"timeout,omitempty"`
	Concurrency   int                `json:"concurrency,omitempty"`

	WatchInterval badoption.Duration `json:"watch_interval,omitempty"`

	Warm []AutoSelectorWarmEntry `json:"warm,omitempty"`

	Pinned string `json:"pinned,omitempty"`

	Tolerance     uint16               `json:"tolerance,omitempty"`
	MaxRTT        badoption.Duration   `json:"max_rtt,omitempty"`
	FailTolerance float64              `json:"fail_tolerance,omitempty"`
	Baselines     []badoption.Duration `json:"baselines,omitempty"`
	Expected      int                  `json:"expected,omitempty"`

	Balance         bool               `json:"balance,omitempty"`
	BalanceMode     string             `json:"balance_mode,omitempty"`
	BalanceInterval badoption.Duration `json:"balance_interval,omitempty"`

	DialRetries               int  `json:"dial_retries,omitempty"`
	InterruptExistConnections bool `json:"interrupt_exist_connections,omitempty"`
}

type AutoSelectorWarmEntry struct {
	Tag string `json:"tag"`

	RTT uint16 `json:"rtt"`

	Age uint32 `json:"age,omitempty"`
}
