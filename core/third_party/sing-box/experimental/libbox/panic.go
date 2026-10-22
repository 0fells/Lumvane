package libbox

type StringBox struct {
	Value string
}

func wrapString(value string) *StringBox {
	return &StringBox{Value: value}
}
