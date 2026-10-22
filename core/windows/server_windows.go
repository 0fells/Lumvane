package main

import (
	"LumvaneCore/gen"
	"LumvaneCore/internal/boxdns"
	"context"
)

func (s *server) SetSystemDNS(ctx context.Context, in *gen.SetSystemDNSRequest) (*gen.EmptyResp, error) {
	err := boxdns.DnsManagerInstance.SetSystemDNS(nil, in.GetClear())
	if err != nil {
		return nil, err
	}

	return &gen.EmptyResp{}, nil
}
