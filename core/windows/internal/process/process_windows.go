//go:build windows

package process

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"sync"
	"unsafe"

	"golang.org/x/sys/windows"
)

func startChild(path string, args []string, noOut bool) (running, error) {
	self, err := selfToken()
	if err != nil {
		return nil, fmt.Errorf("cannot open process token: %w", err)
	}
	defer self.Close()

	if !self.IsElevated() {
		return startCmd(newCmd(path, args, noOut))
	}

	tok, err := unprivilegedToken(self)
	if err != nil {
		return nil, fmt.Errorf("refusing to start extra process: cannot obtain an unprivileged user token: %w", err)
	}
	defer tok.Close()

	return startWithToken(path, args, noOut, tok)
}

var procCreateProcessWithTokenW = windows.NewLazySystemDLL("advapi32.dll").NewProc("CreateProcessWithTokenW")

func startWithToken(path string, args []string, noOut bool, tok windows.Token) (running, error) {
	exe, err := exec.LookPath(path)
	if err != nil {
		return nil, err
	}

	outR, outW, err := os.Pipe()
	if err != nil {
		return nil, err
	}
	errR, errW, err := os.Pipe()
	if err != nil {
		closeAll(outR, outW)
		return nil, err
	}
	nul, err := os.OpenFile("NUL", os.O_RDONLY, 0)
	if err != nil {
		closeAll(outR, outW, errR, errW)
		return nil, err
	}
	for _, f := range []*os.File{outW, errW, nul} {
		if err = makeInheritable(f); err != nil {
			closeAll(outR, outW, errR, errW, nul)
			return nil, err
		}
	}

	si := &windows.StartupInfo{}
	si.Cb = uint32(unsafe.Sizeof(*si))
	si.Flags = windows.STARTF_USESTDHANDLES | windows.STARTF_USESHOWWINDOW
	si.ShowWindow = windows.SW_HIDE
	si.StdInput = windows.Handle(nul.Fd())
	si.StdOutput = windows.Handle(outW.Fd())
	si.StdErr = windows.Handle(errW.Fd())

	appName, err := windows.UTF16PtrFromString(exe)
	if err != nil {
		closeAll(outR, outW, errR, errW, nul)
		return nil, err
	}
	cmdLine, err := windows.UTF16PtrFromString(windows.ComposeCommandLine(append([]string{exe}, args...)))
	if err != nil {
		closeAll(outR, outW, errR, errW, nul)
		return nil, err
	}
	envBlock, err := makeEnvBlock(childEnv())
	if err != nil {
		closeAll(outR, outW, errR, errW, nul)
		return nil, err
	}

	const createFlags = windows.CREATE_UNICODE_ENVIRONMENT | windows.CREATE_NO_WINDOW
	var pi windows.ProcessInformation
	r1, _, e1 := procCreateProcessWithTokenW.Call(
		uintptr(tok),
		0,
		uintptr(unsafe.Pointer(appName)),
		uintptr(unsafe.Pointer(cmdLine)),
		uintptr(createFlags),
		uintptr(unsafe.Pointer(&envBlock[0])),
		0,
		uintptr(unsafe.Pointer(si)),
		uintptr(unsafe.Pointer(&pi)),
	)
	runtime.KeepAlive(si)
	runtime.KeepAlive(appName)
	runtime.KeepAlive(cmdLine)
	runtime.KeepAlive(envBlock)

	closeAll(outW, errW, nul)
	if r1 == 0 {
		closeAll(outR, errR)
		return nil, fmt.Errorf("CreateProcessWithTokenW %s: %w", exe, e1)
	}
	_ = windows.CloseHandle(pi.Thread)

	done := make(chan struct{}, 2)
	pump := func(r *os.File) {
		_, _ = io.Copy(&pipeLogger{prefix: extraCorePrefix, noOut: noOut}, r)
		_ = r.Close()
		done <- struct{}{}
	}
	go pump(outR)
	go pump(errR)

	return &tokenRunner{hProcess: pi.Process, done: done}, nil
}

type tokenRunner struct {
	mu       sync.Mutex
	hProcess windows.Handle
	done     chan struct{}
}

func (t *tokenRunner) Wait() error {
	t.mu.Lock()
	h := t.hProcess
	t.mu.Unlock()
	if h == 0 {
		return nil
	}
	_, err := windows.WaitForSingleObject(h, windows.INFINITE)
	<-t.done
	<-t.done
	t.mu.Lock()
	if t.hProcess != 0 {
		_ = windows.CloseHandle(t.hProcess)
		t.hProcess = 0
	}
	t.mu.Unlock()
	return err
}

func (t *tokenRunner) Kill() error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.hProcess == 0 {
		return nil
	}
	return windows.TerminateProcess(t.hProcess, 1)
}

func makeInheritable(f *os.File) error {
	return windows.SetHandleInformation(windows.Handle(f.Fd()),
		windows.HANDLE_FLAG_INHERIT, windows.HANDLE_FLAG_INHERIT)
}

func closeAll(files ...*os.File) {
	for _, f := range files {
		_ = f.Close()
	}
}

func makeEnvBlock(env []string) ([]uint16, error) {
	var block []uint16
	for _, e := range env {
		if e == "" {
			continue
		}
		u, err := windows.UTF16FromString(e)
		if err != nil {
			return nil, err
		}
		block = append(block, u...)
	}
	block = append(block, 0)
	if len(block) == 1 {
		block = append(block, 0)
	}
	return block, nil
}

func selfToken() (windows.Token, error) {
	var tok windows.Token
	err := windows.OpenProcessToken(windows.CurrentProcess(),
		windows.TOKEN_QUERY|windows.TOKEN_DUPLICATE, &tok)
	return tok, err
}

func unprivilegedToken(self windows.Token) (windows.Token, error) {
	if t, err := linkedToken(self); err == nil {
		return t, nil
	}
	if t, err := consoleSessionToken(); err == nil {
		return t, nil
	}
	if t, err := shellToken(); err == nil {
		return t, nil
	}
	return 0, errors.New("no linked, console-session, or shell token available")
}

func linkedToken(self windows.Token) (windows.Token, error) {
	linked, err := self.GetLinkedToken()
	if err != nil {
		return 0, err
	}
	defer linked.Close()
	if linked.IsElevated() {
		return 0, errors.New("linked token is still elevated")
	}
	return primaryToken(linked)
}

func consoleSessionToken() (windows.Token, error) {
	session := windows.WTSGetActiveConsoleSessionId()
	if session == 0xFFFFFFFF {
		return 0, errors.New("no active console session")
	}
	var tok windows.Token
	if err := windows.WTSQueryUserToken(session, &tok); err != nil {
		return 0, err
	}
	defer tok.Close()
	return primaryToken(tok)
}

func shellToken() (windows.Token, error) {
	pid, err := findProcess("explorer.exe")
	if err != nil {
		return 0, err
	}
	proc, err := windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, pid)
	if err != nil {
		return 0, err
	}
	defer windows.CloseHandle(proc)

	var tok windows.Token
	if err := windows.OpenProcessToken(proc, windows.TOKEN_DUPLICATE|windows.TOKEN_QUERY, &tok); err != nil {
		return 0, err
	}
	defer tok.Close()
	return primaryToken(tok)
}

func primaryToken(src windows.Token) (windows.Token, error) {
	var dup windows.Token
	err := windows.DuplicateTokenEx(
		src,
		windows.TOKEN_ASSIGN_PRIMARY|windows.TOKEN_DUPLICATE|windows.TOKEN_QUERY|
			windows.TOKEN_ADJUST_DEFAULT|windows.TOKEN_ADJUST_SESSIONID,
		nil,
		windows.SecurityImpersonation,
		windows.TokenPrimary,
		&dup,
	)
	if err != nil {
		return 0, err
	}
	return dup, nil
}

func findProcess(name string) (uint32, error) {
	snap, err := windows.CreateToolhelp32Snapshot(windows.TH32CS_SNAPPROCESS, 0)
	if err != nil {
		return 0, err
	}
	defer windows.CloseHandle(snap)

	var entry windows.ProcessEntry32
	entry.Size = uint32(unsafe.Sizeof(entry))
	for err = windows.Process32First(snap, &entry); err == nil; err = windows.Process32Next(snap, &entry) {
		if strings.EqualFold(windows.UTF16ToString(entry.ExeFile[:]), name) {
			return entry.ProcessID, nil
		}
	}
	return 0, fmt.Errorf("process %q not found", name)
}

func createSecureConfigFile() (*os.File, string, error) {
	f, err := os.CreateTemp("", "lumvane-extra-*.conf")
	if err != nil {
		return nil, "", err
	}
	return f, f.Name(), nil
}

func makeConfigReadable(f *os.File) error {
	usersSid, err := windows.CreateWellKnownSid(windows.WinBuiltinUsersSid)
	if err != nil {
		return nil
	}
	h, err := reopenSameObject(f)
	if err != nil {
		return nil
	}
	defer windows.CloseHandle(h)

	sd, err := windows.GetSecurityInfo(h, windows.SE_FILE_OBJECT, windows.DACL_SECURITY_INFORMATION)
	if err != nil {
		return nil
	}
	dacl, _, err := sd.DACL()
	if err != nil {
		return nil
	}
	entries := []windows.EXPLICIT_ACCESS{{
		AccessPermissions: windows.GENERIC_READ,
		AccessMode:        windows.GRANT_ACCESS,
		Inheritance:       windows.NO_INHERITANCE,
		Trustee: windows.TRUSTEE{
			TrusteeForm:  windows.TRUSTEE_IS_SID,
			TrusteeType:  windows.TRUSTEE_IS_GROUP,
			TrusteeValue: windows.TrusteeValueFromSID(usersSid),
		},
	}}
	merged, err := windows.ACLFromEntries(entries, dacl)
	if err != nil {
		return nil
	}
	_ = windows.SetSecurityInfo(h, windows.SE_FILE_OBJECT,
		windows.DACL_SECURITY_INFORMATION, nil, nil, merged, nil)
	return nil
}

func reopenSameObject(f *os.File) (windows.Handle, error) {
	namep, err := windows.UTF16PtrFromString(f.Name())
	if err != nil {
		return 0, err
	}
	h, err := windows.CreateFile(
		namep,
		windows.WRITE_DAC|windows.READ_CONTROL,
		windows.FILE_SHARE_READ|windows.FILE_SHARE_WRITE|windows.FILE_SHARE_DELETE,
		nil,
		windows.OPEN_EXISTING,
		windows.FILE_FLAG_OPEN_REPARSE_POINT|windows.FILE_FLAG_BACKUP_SEMANTICS,
		0,
	)
	if err != nil {
		return 0, err
	}
	var reopened, original windows.ByHandleFileInformation
	if err = windows.GetFileInformationByHandle(h, &reopened); err != nil {
		_ = windows.CloseHandle(h)
		return 0, err
	}
	if err = windows.GetFileInformationByHandle(windows.Handle(f.Fd()), &original); err != nil {
		_ = windows.CloseHandle(h)
		return 0, err
	}
	if reopened.VolumeSerialNumber != original.VolumeSerialNumber ||
		reopened.FileIndexHigh != original.FileIndexHigh ||
		reopened.FileIndexLow != original.FileIndexLow ||
		reopened.FileAttributes&windows.FILE_ATTRIBUTE_REPARSE_POINT != 0 {
		_ = windows.CloseHandle(h)
		return 0, errors.New("config file identity mismatch (possible path swap)")
	}
	return h, nil
}
