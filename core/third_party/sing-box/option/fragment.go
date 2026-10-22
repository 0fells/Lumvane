package option

type TLSFragmentOptions struct {
	Enabled bool   `json:"enabled,omitempty"`
	Size    string `json:"size,omitempty"`
	Sleep   string `json:"sleep,omitempty"`
	Method  string `json:"method,omitempty"`
	Range   string `json:"range,omitempty"`
}
