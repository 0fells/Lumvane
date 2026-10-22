package main

import (
	"LumvaneCore/gen"
	"LumvaneCore/internal/boxdns"
	"LumvaneCore/internal/boxmain"
	"LumvaneCore/internal/process"
	"LumvaneCore/internal/sys"
	"LumvaneCore/test_utils"
	"context"
	"errors"
	"fmt"
	"log"
	"lumshared/boxbox"
	"lumshared/wg"
	"lumshared/xray"
	"net/netip"
	"os"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/google/shlex"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/trafficcontrol"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"
	"github.com/xtls/xray-core/core"
)

var lifecycleMu sync.Mutex

var stateMu sync.RWMutex

var boxInstance *boxbox.Box
var instanceCancel context.CancelFunc

var xrayInstance *core.Instance
var xrayGate *xray.Gate

var xrayFullGates []*xray.Gate

var extraProcess *process.Process
var needUnsetDNS bool
var debug bool

var errInstanceNotRunning = errors.New("Instance is not running")

func currentBox() *boxbox.Box {
	stateMu.RLock()
	defer stateMu.RUnlock()
	return boxInstance
}

func currentInstance() (*boxbox.Box, context.CancelFunc) {
	stateMu.RLock()
	defer stateMu.RUnlock()
	return boxInstance, instanceCancel
}

func setBoxInstance(box *boxbox.Box, cancel context.CancelFunc) {
	stateMu.Lock()
	defer stateMu.Unlock()
	boxInstance, instanceCancel = box, cancel
}

func setXray(instance *core.Instance, gate *xray.Gate) {
	stateMu.Lock()
	defer stateMu.Unlock()
	xrayInstance, xrayGate = instance, gate
}

func setXrayFullGates(gates []*xray.Gate) {
	stateMu.Lock()
	defer stateMu.Unlock()
	xrayFullGates = gates
}

type server struct {
	gen.UnimplementedLibcoreServiceServer
}

func To[T any](v T) *T {
	return &v
}

func init() {
	m := boxdns.DnsManagerInstance
	if m == nil || m.Monitor == nil {
		return
	}
	m.Monitor.RegisterCallback(func(ifc *control.Interface, _ int) {
		name := ""
		if ifc != nil {
			name = ifc.Name
		}
		xray.SetEgressInterface(name)
	})
}

func startXrayFullConfigs(configs []string) ([]*core.Instance, error) {
	instances := make([]*core.Instance, 0, len(configs))
	for _, cfg := range configs {
		inst, err := xray.CreateXrayInstance(cfg)
		if err != nil {
			closeXrayInstances(instances)
			return nil, err
		}
		bindXrayEgress(inst, nil, 0)
		if err := inst.Start(); err != nil {
			_ = inst.Close()
			closeXrayInstances(instances)
			return nil, err
		}
		instances = append(instances, inst)
	}
	return instances, nil
}

func closeXray() {
	stateMu.Lock()
	instance, gate := xrayInstance, xrayGate
	fullGates := xrayFullGates
	xrayInstance, xrayGate, xrayFullGates = nil, nil, nil
	stateMu.Unlock()

	if gate != nil {
		gate.Close()
	}
	for _, fullGate := range fullGates {
		fullGate.Close()
	}
	if instance != nil {
		instance.Close()
	}
	xray.ClearProfileResolver()
}

func startXrayFullGates(configs []string, idle time.Duration, prepare func(*core.Instance) error) ([]*xray.Gate, error) {
	if len(configs) == 0 {
		return nil, nil
	}
	gates := make([]*xray.Gate, len(configs))
	errs := make([]error, len(configs))

	unwind := func() {
		for _, opened := range gates {
			if opened != nil {
				opened.Close()
			}
		}
	}

	gates[0], errs[0] = xray.StartGate(configs[0], idle, prepare)
	if errs[0] != nil {
		return nil, errs[0]
	}

	workers := runtime.NumCPU()
	if workers < 1 {
		workers = 1
	}
	slots := make(chan struct{}, workers)
	var wg sync.WaitGroup
	for i := 1; i < len(configs); i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			slots <- struct{}{}
			defer func() { <-slots }()
			gates[i], errs[i] = xray.StartGate(configs[i], idle, prepare)
		}()
	}
	wg.Wait()

	for _, err := range errs {
		if err == nil {
			continue
		}
		unwind()
		return nil, err
	}
	return gates, nil
}

func closeXrayInstances(instances []*core.Instance) {
	for _, inst := range instances {
		_ = inst.Close()
	}
}

type testEnv struct {
	box   *boxbox.Box
	tags  []string
	close func()
}

func prepareTestEnv(current bool, needXray bool, xrayConfig string, xrayFullConfigs []string,
	coreConfig string, tags []string, useDefaultOutbound bool) (*testEnv, error) {
	if current {
		box := currentBox()
		if box == nil {
			return nil, errInstanceNotRunning
		}

		outTags := tags
		if _, exists := box.Outbound().Outbound("proxy"); exists {
			outTags = []string{"proxy"}
		} else {
			useDefaultOutbound = true
		}
		if useDefaultOutbound {
			outTags = []string{box.Outbound().Default().Tag()}
		}
		return &testEnv{box: box, tags: outTags, close: func() {}}, nil
	}

	var cleanups []func()
	unwind := func() {
		for i := len(cleanups) - 1; i >= 0; i-- {
			cleanups[i]()
		}
	}

	if needXray {
		instance, err := xray.CreateXrayInstance(xrayConfig)
		if err != nil {
			unwind()
			return nil, err
		}

		bindXrayEgress(instance, nil, 0)
		if err = instance.Start(); err != nil {
			_ = instance.Close()
			unwind()
			return nil, err
		}
		cleanups = append(cleanups, func() { _ = instance.Close() })
	}

	fullXray, err := startXrayFullConfigs(xrayFullConfigs)
	if err != nil {
		unwind()
		return nil, err
	}
	cleanups = append(cleanups, func() { closeXrayInstances(fullXray) })

	box, cancel, err := boxmain.Create([]byte(coreConfig))
	if err != nil {
		unwind()
		return nil, err
	}
	cleanups = append(cleanups, func() {
		box.CloseWithTimeout(cancel, 2*time.Second, log.Println, false)
	})

	outTags := tags
	if useDefaultOutbound {
		outTags = []string{box.Outbound().Default().Tag()}
	}
	return &testEnv{box: box, tags: outTags, close: unwind}, nil
}

func speedTestResultToProto(res test_utils.SpeedTestResult) *gen.SpeedTestResult {
	errStr := ""
	if res.Error != nil {
		errStr = res.Error.Error()
	}
	return &gen.SpeedTestResult{
		DlSpeed:       To(res.DlSpeed),
		UlSpeed:       To(res.UlSpeed),
		Latency:       To(res.Latency),
		OutboundTag:   To(res.Tag),
		Error:         To(errStr),
		ServerName:    To(res.ServerName),
		ServerCountry: To(res.ServerCountry),
		Cancelled:     To(res.Cancelled),
		DlBytes:       To(res.DlBytes),
		UlBytes:       To(res.UlBytes),
	}
}

func (s *server) Start(ctx context.Context, in *gen.LoadConfigReq) (out *gen.ErrorResp, _ error) {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()

	if currentBox() != nil {
		return &gen.ErrorResp{Error: To("instance already started")}, nil
	}

	var err error

	defer func() {
		out = &gen.ErrorResp{}
		if err != nil {
			out.Error = To(err.Error())
			setBoxInstance(nil, nil)

			if extraProcess != nil {
				extraProcess.Stop()
				extraProcess = nil
			}
			closeXray()
		}
	}()

	if debug {
		log.Println("Start:", in.GetCoreConfig())
		if in.XrayConfig != nil {
			log.Println("Start Xray:", in.GetXrayConfig())
		}
	}

	if in.GetNeedExtraProcess() {
		args, e := shlex.Split(in.GetExtraProcessArgs())
		if e != nil {
			err = E.Cause(e, "Failed to parse args")
			return
		}
		var extraConfPath, extraCleanupPath string
		if in.ExtraProcessConf != nil {
			extraConfPath, extraCleanupPath, e = process.CreateExtraConfig(in.GetExtraProcessConf())
			if e != nil {
				err = E.Cause(e, "Failed to create extra.conf")
				return
			}
			for idx, arg := range args {
				if strings.Contains(arg, "%s") {
					args[idx] = fmt.Sprintf(arg, extraConfPath)
					break
				}
			}
		}

		extraProcess = process.NewProcess(in.GetExtraProcessPath(), args, in.GetExtraNoOut())
		extraProcess.SetCleanupPath(extraCleanupPath)
		err = extraProcess.Start()
		if err != nil {
			return
		}
	}

	dnsAddr := in.GetXrayOutboundDnsAddress()
	dnsStrategy := in.GetXrayOutboundDnsStrategy()
	prepareXray := func(instance *core.Instance) error {
		if dnsAddr == "" {
			bindXrayEgress(instance, nil, 0)
			return nil
		}
		resolver, e := xray.NewResolver(dnsAddr)
		if e != nil {
			return E.Cause(e, "failed to create Xray outbound DNS resolver")
		}
		bindXrayEgress(instance, resolver, xray.ParseDomainStrategy(dnsStrategy))
		return nil
	}

	if in.GetNeedXray() {
		if in.GetXrayLazyStart() {
			gate, e := xray.StartGate(in.GetXrayConfig(),
				time.Duration(in.GetXrayIdleSeconds())*time.Second, prepareXray)
			if e != nil {
				err = e
				return
			}
			setXray(nil, gate)
		} else {
			instance, e := xray.CreateXrayInstance(in.GetXrayConfig())
			if e != nil {
				err = e
				return
			}
			if e = prepareXray(instance); e != nil {
				instance.Close()
				err = e
				return
			}
			if e = instance.Start(); e != nil {
				instance.Close()
				err = e
				return
			}
			setXray(instance, nil)
		}
	}

	if fullConfigs := in.GetXrayFullConfigs(); len(fullConfigs) > 0 {
		gates, e := startXrayFullGates(fullConfigs,
			time.Duration(in.GetXrayFullIdleSeconds())*time.Second, prepareXray)
		if e != nil {
			closeXray()
			err = e
			return
		}
		setXrayFullGates(gates)
	}

	box, cancel, err := boxmain.Create([]byte(in.GetCoreConfig()))
	if err != nil {
		if extraProcess != nil {
			extraProcess.Stop()
			extraProcess = nil
		}
		closeXray()
		return
	}
	setBoxInstance(box, cancel)

	if runtime.GOOS == "darwin" && in.GetTunIpv4Cidr() != "" {
		stopAllCores := func() {
			box.CloseWithTimeout(cancel, time.Second*2, log.Println, true)
			setBoxInstance(nil, nil)
			if extraProcess != nil {
				extraProcess.Stop()
				extraProcess = nil
			}
			closeXray()
		}

		tunCIDR := in.GetTunIpv4Cidr()
		tunPrefix, parseErr := netip.ParsePrefix(tunCIDR)
		if parseErr != nil || !tunPrefix.Addr().Is4() {
			err = fmt.Errorf("invalid tun_ipv4_cidr %q", tunCIDR)
			stopAllCores()
			return
		}

		tunDNS := tunPrefix.Addr().Next()
		if !tunDNS.IsValid() || !tunDNS.Is4() {
			err = fmt.Errorf("got invalid DNS IP from tun_ipv4_cidr: %s", tunDNS)
			stopAllCores()
			return
		}

		if err := sys.SetSystemDNS(tunDNS.String(), box.Network().InterfaceMonitor()); err != nil {
			log.Println("Failed to set system DNS:", err)
		}

		needUnsetDNS = true
	}

	return
}

func (s *server) Stop(ctx context.Context, in *gen.EmptyReq) (out *gen.ErrorResp, _ error) {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()

	var err error

	defer func() {
		out = &gen.ErrorResp{}
		if err != nil {
			out.Error = To(err.Error())
		}
	}()

	box, cancel := currentInstance()
	if box == nil {
		return
	}

	if needUnsetDNS {
		needUnsetDNS = false
		err := sys.SetSystemDNS("Empty", box.Network().InterfaceMonitor())
		if err != nil {
			log.Println("Failed to unset system DNS:", err)
		}
	}

	setBoxInstance(nil, nil)
	box.CloseWithTimeout(cancel, time.Second*2, log.Println, true)

	if extraProcess != nil {
		extraProcess.Stop()
		extraProcess = nil
	}

	closeXray()

	return
}

func (s *server) CheckConfig(ctx context.Context, in *gen.LoadConfigReq) (out *gen.ErrorResp, _ error) {
	out = &gen.ErrorResp{}

	defer func() {
		if r := recover(); r != nil {
			buf := make([]byte, 4096)
			n := runtime.Stack(buf, false)
			log.Printf("CheckConfig panic: %v\n%s", r, buf[:n])
			out.Error = To(fmt.Sprintf("CheckConfig panic: %v", r))
		}
	}()
	if in.GetNeedXray() {
		if err := xray.CheckXrayConfig(in.GetXrayConfig()); err != nil {
			out.Error = To(err.Error())
		}
		return
	}
	err := boxmain.Check([]byte(in.GetCoreConfig()))
	if err != nil {
		out.Error = To(err.Error())
	}
	return
}

func (s *server) Test(ctx context.Context, in *gen.TestReq) (*gen.TestResp, error) {
	env, err := prepareTestEnv(in.GetTestCurrent(), in.GetNeedXray(), in.GetXrayConfig(),
		in.XrayFullConfigs, in.GetConfig(), in.OutboundTags, in.GetUseDefaultOutbound())
	if err != nil {
		if errors.Is(err, errInstanceNotRunning) {
			return &gen.TestResp{Results: []*gen.URLTestResp{{
				OutboundTag: To("proxy"),
				LatencyMs:   To(int32(0)),
				Error:       To(err.Error()),
			}}}, nil
		}
		return nil, err
	}
	defer env.close()

	twice := !in.GetTestCurrent()
	results := test_utils.BatchURLTest(test_utils.TestContext(), env.box, env.tags, in.GetUrl(),
		int(in.GetMaxConcurrency()), twice, time.Duration(in.GetTestTimeoutMs())*time.Millisecond)

	res := make([]*gen.URLTestResp, 0, len(results))
	for idx, data := range results {
		errStr := ""
		if data.Error != nil {
			errStr = data.Error.Error()
		}
		res = append(res, &gen.URLTestResp{
			OutboundTag: To(env.tags[idx]),
			LatencyMs:   To(int32(data.Duration.Milliseconds())),
			Error:       To(errStr),
		})
	}

	return &gen.TestResp{Results: res}, nil
}

func (s *server) StopTest(ctx context.Context, in *gen.EmptyReq) (*gen.EmptyResp, error) {
	test_utils.CancelTests()

	return &gen.EmptyResp{}, nil
}

func (s *server) QueryURLTest(ctx context.Context, in *gen.EmptyReq) (out *gen.QueryURLTestResponse, _ error) {
	results := test_utils.URLReporter.Results()
	out = &gen.QueryURLTestResponse{}
	for _, r := range results {
		errStr := ""
		if r.Error != nil {
			errStr = r.Error.Error()
		}
		out.Results = append(out.Results, &gen.URLTestResp{
			OutboundTag: To(r.Tag),
			LatencyMs:   To(int32(r.Duration.Milliseconds())),
			Error:       To(errStr),
		})
	}
	return
}

func (s *server) IPTest(ctx context.Context, in *gen.IPTestRequest) (*gen.IPTestResp, error) {
	env, err := prepareTestEnv(false, in.GetNeedXray(), in.GetXrayConfig(),
		in.XrayFullConfigs, in.GetConfig(), in.OutboundTags, in.GetUseDefaultOutbound())
	if err != nil {
		return nil, err
	}
	defer env.close()

	timeout := time.Duration(in.GetTestTimeoutMs()) * time.Millisecond
	results := test_utils.BatchIPTest(test_utils.TestContext(), env.box, env.tags,
		int(in.GetMaxConcurrency()), timeout)

	res := make([]*gen.IPTestRes, 0, len(results))
	for idx, data := range results {
		errStr := ""
		if data.Error != nil {
			errStr = data.Error.Error()
		}
		res = append(res, &gen.IPTestRes{
			OutboundTag: To(env.tags[idx]),
			Ip:          To(data.Result.IP),
			CountryCode: To(data.Result.CountryCode),
			Error:       To(errStr),
		})
	}
	return &gen.IPTestResp{Results: res}, nil
}

func (s *server) QueryIPTest(ctx context.Context, in *gen.EmptyReq) (out *gen.QueryIPTestResponse, _ error) {
	results := test_utils.IPReporter.Results()
	out = &gen.QueryIPTestResponse{}
	for _, r := range results {
		errStr := ""
		if r.Error != nil {
			errStr = r.Error.Error()
		}
		out.Results = append(out.Results, &gen.IPTestRes{
			OutboundTag: To(r.Tag),
			Ip:          To(r.Result.IP),
			CountryCode: To(r.Result.CountryCode),
			Error:       To(errStr),
		})
	}
	return
}

func (s *server) QueryStats(ctx context.Context, in *gen.EmptyReq) (out *gen.QueryStatsResp, err error) {
	out = &gen.QueryStatsResp{}
	out.Ups = make(map[string]int64)
	out.Downs = make(map[string]int64)
	if box := currentBox(); box != nil {
		if tm := service.PtrFromContext[trafficcontrol.Manager](box.Context()); tm != nil {
			outbounds := service.FromContext[adapter.OutboundManager](box.Context())
			if outbounds == nil {
				log.Println("Failed to get outbound manager")
				err = E.New("nil outbound manager")
				return
			}
			endpoints := service.FromContext[adapter.EndpointManager](box.Context())
			if endpoints == nil {
				log.Println("Failed to get endpoint manager")
				err = E.New("nil endpoint manager")
				return
			}
			for _, ob := range outbounds.Outbounds() {
				u, d := tm.TotalOutbound(ob.Tag())
				out.Ups[ob.Tag()] = u
				out.Downs[ob.Tag()] = d
			}
			for _, ep := range endpoints.Endpoints() {
				u, d := tm.TotalOutbound(ep.Tag())
				out.Ups[ep.Tag()] = u
				out.Downs[ep.Tag()] = d
			}
		}
	}
	return
}

func connMetaToProto(c *trafficcontrol.TrackerMetadata) *gen.ConnectionMetaData {
	process := ""
	processPath := ""
	if c.Metadata.ProcessInfo != nil {
		processPath = c.Metadata.ProcessInfo.ProcessPath
		spl := strings.Split(processPath, string(os.PathSeparator))
		process = spl[len(spl)-1]
	}
	var closedAt int64
	if !c.ClosedAt.IsZero() {
		closedAt = c.ClosedAt.UnixMilli()
	}
	return &gen.ConnectionMetaData{
		Id:          To(c.ID.String()),
		CreatedAt:   To(c.CreatedAt.UnixMilli()),
		Upload:      To(c.Upload.Load()),
		Download:    To(c.Download.Load()),
		Outbound:    To(c.Outbound),
		Network:     To(c.Metadata.Network),
		Dest:        To(c.Metadata.Destination.String()),
		Protocol:    To(c.Metadata.Protocol),
		Domain:      To(c.Metadata.Domain),
		Process:     To(process),
		ProcessPath: To(processPath),
		Chain:       c.Chain,
		ClosedAt:    To(closedAt),
	}
}

func (s *server) QueryConnections(ctx context.Context, in *gen.EmptyReq) (*gen.QueryConnectionsResp, error) {
	box := currentBox()
	if box == nil {
		return &gen.QueryConnectionsResp{}, nil
	}
	tm := service.PtrFromContext[trafficcontrol.Manager](box.Context())
	if tm == nil {
		return &gen.QueryConnectionsResp{}, errors.New("no traffic manager found")
	}

	active := make([]*gen.ConnectionMetaData, 0)
	for _, c := range tm.Connections() {
		active = append(active, connMetaToProto(c))
	}
	closed := make([]*gen.ConnectionMetaData, 0)
	for _, c := range tm.ClosedConnections() {
		closed = append(closed, connMetaToProto(c))
	}
	return &gen.QueryConnectionsResp{Active: active, Closed: closed}, nil
}

func (s *server) IsPrivileged(ctx context.Context, _ *gen.EmptyReq) (*gen.IsPrivilegedResponse, error) {
	if runtime.GOOS == "windows" {
		return &gen.IsPrivilegedResponse{
			HasPrivilege: To(false),
		}, nil
	}

	return &gen.IsPrivilegedResponse{HasPrivilege: To(os.Geteuid() == 0)}, nil
}

func (s *server) SpeedTest(ctx context.Context, in *gen.SpeedTestRequest) (*gen.SpeedTestResponse, error) {
	if !in.GetTestDownload() && !in.GetTestUpload() && !in.GetSimpleDownload() && !in.GetOnlyCountry() {
		return nil, errors.New("cannot run empty test")
	}

	env, err := prepareTestEnv(in.GetTestCurrent(), in.GetNeedXray(), in.GetXrayConfig(),
		in.XrayFullConfigs, in.GetConfig(), in.OutboundTags, in.GetUseDefaultOutbound())
	if err != nil {
		if errors.Is(err, errInstanceNotRunning) {
			return &gen.SpeedTestResponse{Results: []*gen.SpeedTestResult{{
				OutboundTag: To("proxy"),
				Error:       To(err.Error()),
			}}}, nil
		}
		return nil, err
	}
	defer env.close()

	results := test_utils.BatchSpeedTest(test_utils.TestContext(), env.box, env.tags,
		in.GetTestDownload(), in.GetTestUpload(), in.GetSimpleDownload(), in.GetSimpleDownloadAddr(),
		time.Duration(in.GetTimeoutMs())*time.Millisecond, in.GetOnlyCountry(), in.GetCountryConcurrency())

	res := make([]*gen.SpeedTestResult, 0, len(results))
	for _, data := range results {
		res = append(res, speedTestResultToProto(*data))
	}

	return &gen.SpeedTestResponse{Results: res}, nil
}

func (s *server) QuerySpeedTest(context.Context, *gen.EmptyReq) (*gen.QuerySpeedTestResponse, error) {
	res, isRunning := test_utils.SpTQuerier.Result()
	return &gen.QuerySpeedTestResponse{
		Result:    speedTestResultToProto(res),
		IsRunning: To(isRunning),
	}, nil
}

func (s *server) QueryCountryTest(ctx context.Context, _ *gen.EmptyReq) (out *gen.QueryCountryTestResponse, _ error) {
	results := test_utils.CountryResults.Results()
	out = &gen.QueryCountryTestResponse{}
	for _, res := range results {
		out.Results = append(out.Results, speedTestResultToProto(*res))
	}
	return
}

func (s *server) GenWgKeyPair(ctx context.Context, _ *gen.EmptyReq) (out *gen.GenWgKeyPairResponse, _ error) {
	var res gen.GenWgKeyPairResponse
	privateKey, err := wg.GeneratePrivateKey()
	if err != nil {
		res.Error = To(err.Error())
		return &res, nil
	}
	res.PrivateKey = To(privateKey.String())
	res.PublicKey = To(privateKey.PublicKey().String())
	return &res, nil
}
