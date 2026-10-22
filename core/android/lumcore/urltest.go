package lumcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common/metadata"
	"github.com/xtls/xray-core/core"

	"lumshared/boxbox"
	"lumshared/xray"
)

type TestOptions struct {
	Current             bool
	CoreConfig          string
	XrayConfig          string
	XrayFullConfigsJSON string

	Tags               string
	UseDefaultOutbound bool
	URL                string
	TimeoutMs          int32
	Concurrency        int32
}

const defaultURLTestTimeout = 3 * time.Second

var (
	testMu     sync.Mutex
	testCtx    context.Context
	testCancel context.CancelFunc
)

func testContext() context.Context {
	testMu.Lock()
	defer testMu.Unlock()
	if testCtx == nil || testCtx.Err() != nil {
		testCtx, testCancel = context.WithCancel(context.Background())
	}
	return testCtx
}

func StopTests() {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
}

type urlTestResult struct {
	Tag       string `json:"tag"`
	LatencyMs int32  `json:"latency_ms"`
	Error     string `json:"error"`
}

func URLTest(o *TestOptions) (string, error) {
	if o == nil || o.URL == "" {
		return "", errors.New("empty url")
	}
	timeout := time.Duration(o.TimeoutMs) * time.Millisecond
	if timeout <= 0 {
		timeout = defaultURLTestTimeout
	}
	var box *boxbox.Box
	var tags []string
	for _, t := range strings.Split(o.Tags, "\n") {
		if t = strings.TrimSpace(t); t != "" {
			tags = append(tags, t)
		}
	}
	useDefault := o.UseDefaultOutbound || len(tags) == 0

	if o.Current {
		box = currentBox()
		if box == nil {
			return "", errNotStart
		}
	} else {
		var closeEnv func()
		var err error
		box, closeEnv, err = buildTestEnv(o)
		if err != nil {
			return "", err
		}
		defer closeEnv()
	}
	if useDefault {
		tags = []string{box.Outbound().Default().Tag()}
	}

	results := make([]urlTestResult, len(tags))
	conc := int(o.Concurrency)
	if conc <= 0 {
		conc = 8
	}
	sem := make(chan struct{}, conc)
	ctx := testContext()
	var wg sync.WaitGroup
	for i, tag := range tags {
		wg.Add(1)
		go func() {
			defer wg.Done()
			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			case <-ctx.Done():
				results[i] = urlTestResult{Tag: tag, Error: "test aborted"}
				return
			}
			results[i] = probeOne(ctx, box, tag, o.URL, timeout, !o.Current)
		}()
	}
	wg.Wait()
	raw, err := json.Marshal(results)
	return string(raw), err
}

func probeOne(ctx context.Context, box *boxbox.Box, tag, url string, timeout time.Duration, twice bool) (res urlTestResult) {
	res.Tag = tag
	defer func() {
		if r := recover(); r != nil {
			res.Error = fmt.Sprint("panic: ", r)
		}
	}()
	outbound, ok := box.Outbound().Outbound(tag)
	if !ok {
		res.Error = "no outbound with tag " + tag + " found"
		return
	}
	client := outboundHTTPClient(outbound, timeout)
	d, err := getOnce(ctx, client, url)
	if err == nil && twice {
		d, err = getOnce(ctx, client, url)
	}
	if err != nil {
		res.Error = err.Error()
		return
	}
	res.LatencyMs = int32(d.Milliseconds())
	return
}

func outboundHTTPClient(outbound adapter.Outbound, timeout time.Duration) *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return outbound.DialContext(ctx, "tcp", metadata.ParseSocksaddr(addr))
			},
			DisableKeepAlives:   false,
			TLSHandshakeTimeout: timeout,
		},
		Timeout: timeout,
		CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
}

func getOnce(ctx context.Context, client *http.Client, url string) (time.Duration, error) {
	begin := time.Now()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return 0, err
	}
	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	_ = resp.Body.Close()
	return time.Since(begin), nil
}

func buildTestEnv(o *TestOptions) (*boxbox.Box, func(), error) {
	var cleanups []func()
	unwind := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}
	if o.XrayConfig != "" {
		inst, err := xray.CreateXrayInstance(o.XrayConfig)
		if err != nil {
			return nil, nil, err
		}
		xray.BindEgress(inst, nil, 0)
		if err = inst.Start(); err != nil {
			_ = inst.Close()
			return nil, nil, err
		}
		cleanups = append(cleanups, func() { _ = inst.Close() })
	}
	fulls, err := parseFullConfigs(o.XrayFullConfigsJSON)
	if err != nil {
		unwind()
		return nil, nil, err
	}
	var started []*core.Instance
	for _, cfg := range fulls {
		inst, e := xray.CreateXrayInstance(cfg)
		if e == nil {
			xray.BindEgress(inst, nil, 0)
			e = inst.Start()
			if e != nil {
				_ = inst.Close()
			}
		}
		if e != nil {
			for _, s := range started {
				_ = s.Close()
			}
			unwind()
			return nil, nil, e
		}
		started = append(started, inst)
	}
	cleanups = append(cleanups, func() {
		for _, s := range started {
			_ = s.Close()
		}
	})
	box, cancel, err := createBox([]byte(o.CoreConfig), false)
	if err != nil {
		unwind()
		return nil, nil, err
	}
	cleanups = append(cleanups, func() { box.CloseWithTimeout(cancel, 2*time.Second, func(...any) {}, false) })
	return box, unwind, nil
}
