/* SPDX-License-Identifier: MIT
 *
 * Copyright (C) 2017-2025 WireGuard LLC. All Rights Reserved.
 */

package device

import (
	"log"
	"os"
)

type Logger struct {
	Verbosef func(format string, args ...any)
	Errorf   func(format string, args ...any)
}

const (
	LogLevelSilent = iota
	LogLevelError
	LogLevelVerbose
)

func DiscardLogf(format string, args ...any) {}

func NewLogger(level int, prepend string) *Logger {
	logger := &Logger{DiscardLogf, DiscardLogf}
	logf := func(prefix string) func(string, ...any) {
		return log.New(os.Stdout, prefix+": "+prepend, log.Ldate|log.Ltime).Printf
	}
	if level >= LogLevelVerbose {
		logger.Verbosef = logf("DEBUG")
	}
	if level >= LogLevelError {
		logger.Errorf = logf("ERROR")
	}
	return logger
}
