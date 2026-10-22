package lumcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"runtime"
	"runtime/debug"
	"strings"
	"sync"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	sjson "github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/filemanager"
	"github.com/xtls/xray-core/core"

	"lumshared/boxbox"
	"lumshared/xray"
)

type LogListener interface {
	OnLog(level int32, message string)
}

const logRingSize = 1000

var (
	platformMu   sync.RWMutex
	platform     Platform
	hub          *monitorHub
	workDir      string
	tempDir      string
	logListener  LogListener
	logRing      []string
	logRingMutex sync.Mutex
)

func Init(workPath string, tempPath string, p Platform, logs LogListener) {
	platformMu.Lock()
	defer platformMu.Unlock()
	workDir, tempDir = workPath, tempPath
	platform = p
	logListener = logs
	if p != nil {
		hub = &monitorHub{platform: p}
		xray.SetProtect(func(fd int) bool { return p.Protect(int32(fd)) })
	} else {
		hub = nil
		xray.SetProtect(nil)
	}
	if workPath != "" {
		_ = os.MkdirAll(workPath, 0o777)
	}
	if tempPath != "" {
		_ = os.MkdirAll(tempPath, 0o777)
	}
	debug.SetPanicOnFault(true)
}

func Version() string {
	return C.Version + " (" + runtime.Version() + " " + runtime.GOOS + "/" + runtime.GOARCH + ")"
}

func RecentLogs() string {
	logRingMutex.Lock()
	defer logRingMutex.Unlock()
	return strings.Join(logRing, "\n")
}

type logWriter struct{}

func (logWriter) WriteMessage(level log.Level, message string) {
	logRingMutex.Lock()
	logRing = append(logRing, message)
	if len(logRing) > logRingSize {
		logRing = append([]string(nil), logRing[len(logRing)-logRingSize:]...)
	}
	logRingMutex.Unlock()
	platformMu.RLock()
	l := logListener
	platformMu.RUnlock()
	if l != nil {
		l.OnLog(int32(level), message)
	}
}

func newBoxContext() context.Context {
	platformMu.RLock()
	p, h, work, temp := platform, hub, workDir, tempDir
	platformMu.RUnlock()
	ctx := context.Background()
	ctx = filemanager.WithDefault(ctx, work, temp, os.Getuid(), os.Getgid())
	ctx = service.ContextWith(ctx, deprecated.NewStderrManager(log.StdLogger()))
	ctx = boxbox.Context(ctx, include.InboundRegistry(), include.OutboundRegistry(), include.EndpointRegistry(),
		include.DNSTransportRegistry(), include.ServiceRegistry(), include.CertificateProviderRegistry())
	if p != nil {
		service.MustRegister[adapter.PlatformInterface](ctx, &platformWrapper{iif: p, hub: h})
	}
	return ctx
}

func parseConfig(ctx context.Context, content []byte) (*option.Options, error) {
	options, err := sjson.UnmarshalExtendedContext[option.Options](ctx, content)
	if err != nil {
		return nil, E.Cause(err, "decode config")
	}
	return &options, nil
}

func createBox(content []byte, withLogWriter bool) (*boxbox.Box, context.CancelFunc, error) {
	ctx := newBoxContext()
	options, err := parseConfig(ctx, content)
	if err != nil {
		return nil, nil, err
	}
	ctx, cancel := context.WithCancel(ctx)
	bo := boxbox.Options{Context: ctx, Options: *options}
	if withLogWriter {
		bo.PlatformLogWriter = logWriter{}
	}
	instance, err := boxbox.New(bo)
	if err != nil {
		cancel()
		return nil, nil, E.Cause(err, "create service")
	}
	if err = instance.Start(); err != nil {
		cancel()
		_ = instance.Close()
		return nil, nil, E.Cause(err, "start service")
	}
	return instance, cancel, nil
}

func CheckConfig(configJSON string) (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("CheckConfig panic: %v", r)
		}
	}()
	ctx := newBoxContext()
	options, err := parseConfig(ctx, []byte(configJSON))
	if err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	instance, err := boxbox.New(boxbox.Options{Context: ctx, Options: *options})
	if err == nil {
		_ = instance.Close()
	}
	return err
}

func CheckXrayConfig(configJSON string) (err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("CheckXrayConfig panic: %v", r)
		}
	}()
	return xray.CheckXrayConfig(configJSON)
}

type StartOptions struct {
	CoreConfig string

	XrayConfig string

	XrayLazyStart   bool
	XrayIdleSeconds int32

	XrayFullConfigsJSON string
	XrayFullIdleSeconds int32

	XrayOutboundDNSAddress  string
	XrayOutboundDNSStrategy string
}

type xrayStack struct {
	instance  *core.Instance
	gate      *xray.Gate
	fullGates []*xray.Gate
}

func (x *xrayStack) close() {
	if x == nil {
		return
	}
	if x.gate != nil {
		x.gate.Close()
	}
	for _, g := range x.fullGates {
		g.Close()
	}
	if x.instance != nil {
		_ = x.instance.Close()
	}
	xray.ClearProfileResolver()
}

type xrayInstances struct {
	list []*core.Instance
}

func (x *xrayInstances) close() {
	for _, i := range x.list {
		_ = i.Close()
	}
}

func parseFullConfigs(raw string) ([]string, error) {
	if strings.TrimSpace(raw) == "" {
		return nil, nil
	}
	var out []string
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		return nil, E.Cause(err, "xray full configs must be a JSON array of strings")
	}
	return out, nil
}

func startXray(o *StartOptions) (stack *xrayStack, err error) {
	stack = &xrayStack{}
	defer func() {
		if err != nil {
			stack.close()
			stack = nil
		}
	}()
	prepare := func(instance *core.Instance) error {
		if o.XrayOutboundDNSAddress == "" {
			xray.BindEgress(instance, nil, 0)
			return nil
		}
		resolver, e := xray.NewResolver(o.XrayOutboundDNSAddress)
		if e != nil {
			return E.Cause(e, "failed to create Xray outbound DNS resolver")
		}
		xray.BindEgress(instance, resolver, xray.ParseDomainStrategy(o.XrayOutboundDNSStrategy))
		return nil
	}
	if o.XrayConfig != "" {
		if o.XrayLazyStart {
			gate, e := xray.StartGate(o.XrayConfig, time.Duration(o.XrayIdleSeconds)*time.Second, prepare)
			if e != nil {
				return stack, e
			}
			stack.gate = gate
		} else {
			instance, e := xray.CreateXrayInstance(o.XrayConfig)
			if e != nil {
				return stack, e
			}
			if e = prepare(instance); e == nil {
				e = instance.Start()
			}
			if e != nil {
				_ = instance.Close()
				return stack, e
			}
			stack.instance = instance
		}
	}
	fulls, err := parseFullConfigs(o.XrayFullConfigsJSON)
	if err != nil {
		return stack, err
	}
	idle := time.Duration(o.XrayFullIdleSeconds) * time.Second
	for _, cfg := range fulls {
		gate, e := xray.StartGate(cfg, idle, prepare)
		if e != nil {
			return stack, e
		}
		stack.fullGates = append(stack.fullGates, gate)
	}
	return stack, nil
}

var (
	lifecycleMu sync.Mutex

	stateMu     sync.RWMutex
	curBox      *boxbox.Box
	curCancel   context.CancelFunc
	curXray     *xrayStack
	errNotStart = errors.New("instance is not running")
)

func currentBox() *boxbox.Box {
	stateMu.RLock()
	defer stateMu.RUnlock()
	return curBox
}

func Start(o *StartOptions) (err error) {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("start panic: %v", r)
		}
	}()
	if currentBox() != nil {
		return errors.New("instance already started")
	}
	if o == nil || o.CoreConfig == "" {
		return errors.New("empty core config")
	}
	stack, err := startXray(o)
	if err != nil {
		return err
	}
	box, cancel, err := createBox([]byte(o.CoreConfig), true)
	if err != nil {
		stack.close()
		return err
	}
	stateMu.Lock()
	curBox, curCancel, curXray = box, cancel, stack
	stateMu.Unlock()
	return nil
}

func Stop() error {
	lifecycleMu.Lock()
	defer lifecycleMu.Unlock()
	stateMu.Lock()
	box, cancel, stack := curBox, curCancel, curXray
	curBox, curCancel, curXray = nil, nil, nil
	stateMu.Unlock()
	if box == nil {
		return nil
	}
	box.CloseWithTimeout(cancel, 2*time.Second, func(v ...any) { fmt.Fprintln(os.Stderr, v...) }, true)
	stack.close()
	return nil
}

func IsRunning() bool { return currentBox() != nil }

func TotalUp() int64   { up, _ := totals(); return up }
func TotalDown() int64 { _, down := totals(); return down }
