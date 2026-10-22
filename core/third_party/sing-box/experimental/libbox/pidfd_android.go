package libbox

import (
	"os"
	_ "unsafe"
)

//go:linkname checkPidfdOnce os.checkPidfdOnce
var checkPidfdOnce func() error

func init() {
	checkPidfdOnce = func() error {
		return os.ErrInvalid
	}
}
