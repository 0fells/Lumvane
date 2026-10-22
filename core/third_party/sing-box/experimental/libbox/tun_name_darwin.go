package libbox

import "golang.org/x/sys/unix"

func getTunnelName(fd int32) (string, error) {
	return unix.GetsockoptString(
		int(fd),
		2,
		2,
	)
}
