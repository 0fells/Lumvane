package main

import (
	"LumvaneCore/gen"
	"LumvaneCore/internal/boxdns"
	"context"
)

func (s *server) GetDefaultInterface(ctx context.Context, in *gen.EmptyReq) (*gen.GetDefaultInterfaceResponse, error) {
	ifc := boxdns.DefaultInterface()
	if ifc == nil {
		return &gen.GetDefaultInterfaceResponse{Name: To(""), Index: To(int32(0))}, nil
	}
	return &gen.GetDefaultInterfaceResponse{
		Name:  To(ifc.Name),
		Index: To(int32(ifc.Index)),
	}, nil
}
