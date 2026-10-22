package option

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"strconv"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
)

type IntRange struct {
	Min uint64
	Max uint64
}

func Parse2IntRange(str string) (IntRange, error) {
	var err error
	result := IntRange{}

	splitString := strings.Split(str, "-")
	if len(splitString) == 2 {
		result.Min, err = strconv.ParseUint(splitString[0], 10, 64)
		if err != nil {
			return result, E.Cause(err, "error parsing string to integer")
		}
		result.Max, err = strconv.ParseUint(splitString[1], 10, 64)
		if err != nil {
			return result, E.Cause(err, "error parsing string to integer")
		}

		if result.Max < result.Min {
			return result, E.Cause(E.New(fmt.Sprintf("upper bound value (%d) must be greater than or equal to lower bound value (%d)", result.Max, result.Min)), "invalid range")
		}
	} else {
		result.Min, err = strconv.ParseUint(splitString[0], 10, 64)
		if err != nil {
			return result, E.Cause(err, "error parsing string to integer")
		}
		result.Max = result.Min
	}

	return result, err
}

func (r IntRange) UniformRand() int64 {
	if r.Max == 0 {
		return 0
	}
	if r.Min == r.Max {
		return int64(r.Min)
	}
	randomInt, _ := rand.Int(rand.Reader, big.NewInt(int64(r.Max-r.Min)+1))
	return int64(r.Min) + randomInt.Int64()
}

func GetRandomIntFromRange(min uint64, max uint64) int64 {
	if max == 0 {
		return 0
	}
	if min == max {
		return int64(min)
	}
	randomInt, _ := rand.Int(rand.Reader, big.NewInt(int64(max-min)+1))
	return int64(min) + randomInt.Int64()
}
