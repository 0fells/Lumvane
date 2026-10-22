package schannel

import (
	"syscall"
	"unsafe"
)

//go:generate go run golang.org/x/sys/windows/mkwinsyscall -output zsyscall_windows.go syscall_windows.go

var procCompleteAuthToken = modsecur32.NewProc("CompleteAuthToken")

func sspiCompleteAuthToken(context *secHandle, token *secBufferDesc) (ret syscall.Errno) {
	r0, _, _ := syscall.SyscallN(procCompleteAuthToken.Addr(), uintptr(unsafe.Pointer(context)), uintptr(unsafe.Pointer(token)))
	ret = syscall.Errno(r0)
	return
}
