package lumcore

import "sync"

type monitorHub struct {
	platform Platform

	mu        sync.Mutex
	subs      []*defaultInterfaceMonitor
	started   bool
	hasState  bool
	name      string
	index     int32
	expensive bool
	limited   bool
}

func (h *monitorHub) subscribe(m *defaultInterfaceMonitor) error {
	h.mu.Lock()
	h.subs = append(h.subs, m)
	startPlatform := !h.started
	h.started = true
	hasState, name, index, exp, lim := h.hasState, h.name, h.index, h.expensive, h.limited
	h.mu.Unlock()

	if startPlatform {
		if err := h.platform.StartDefaultInterfaceMonitor(&InterfaceListener{hub: h}); err != nil {
			h.mu.Lock()
			h.started = false
			h.removeLocked(m)
			h.mu.Unlock()
			return err
		}
	} else if hasState {
		m.update(name, index, exp, lim)
	}
	return nil
}

func (h *monitorHub) unsubscribe(m *defaultInterfaceMonitor) error {
	h.mu.Lock()
	h.removeLocked(m)
	stop := h.started && len(h.subs) == 0
	if stop {
		h.started = false
		h.hasState = false
	}
	h.mu.Unlock()
	if stop {
		return h.platform.CloseDefaultInterfaceMonitor()
	}
	return nil
}

func (h *monitorHub) removeLocked(m *defaultInterfaceMonitor) {
	for i, s := range h.subs {
		if s == m {
			h.subs = append(h.subs[:i], h.subs[i+1:]...)
			return
		}
	}
}

func (h *monitorHub) push(name string, index int32, expensive, limited bool) {
	h.mu.Lock()
	h.hasState, h.name, h.index, h.expensive, h.limited = true, name, index, expensive, limited
	subs := append([]*defaultInterfaceMonitor(nil), h.subs...)
	h.mu.Unlock()
	for _, m := range subs {
		m.update(name, index, expensive, limited)
	}
}
