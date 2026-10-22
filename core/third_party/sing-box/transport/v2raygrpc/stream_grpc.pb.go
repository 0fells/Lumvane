package v2raygrpc

import (
	context "context"

	grpc "google.golang.org/grpc"
	codes "google.golang.org/grpc/codes"
	status "google.golang.org/grpc/status"
)

const _ = grpc.SupportPackageIsVersion9

const (
	GunService_Tun_FullMethodName = "/transport.v2raygrpc.GunService/Tun"
)

type GunServiceClient interface {
	Tun(ctx context.Context, opts ...grpc.CallOption) (grpc.BidiStreamingClient[Hunk, Hunk], error)
}

type gunServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewGunServiceClient(cc grpc.ClientConnInterface) GunServiceClient {
	return &gunServiceClient{cc}
}

func (c *gunServiceClient) Tun(ctx context.Context, opts ...grpc.CallOption) (grpc.BidiStreamingClient[Hunk, Hunk], error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	stream, err := c.cc.NewStream(ctx, &GunService_ServiceDesc.Streams[0], GunService_Tun_FullMethodName, cOpts...)
	if err != nil {
		return nil, err
	}
	x := &grpc.GenericClientStream[Hunk, Hunk]{ClientStream: stream}
	return x, nil
}

type GunService_TunClient = grpc.BidiStreamingClient[Hunk, Hunk]

type GunServiceServer interface {
	Tun(grpc.BidiStreamingServer[Hunk, Hunk]) error
	mustEmbedUnimplementedGunServiceServer()
}

type UnimplementedGunServiceServer struct{}

func (UnimplementedGunServiceServer) Tun(grpc.BidiStreamingServer[Hunk, Hunk]) error {
	return status.Error(codes.Unimplemented, "method Tun not implemented")
}
func (UnimplementedGunServiceServer) mustEmbedUnimplementedGunServiceServer() {}
func (UnimplementedGunServiceServer) testEmbeddedByValue()                    {}

type UnsafeGunServiceServer interface {
	mustEmbedUnimplementedGunServiceServer()
}

func RegisterGunServiceServer(s grpc.ServiceRegistrar, srv GunServiceServer) {
	if t, ok := srv.(interface{ testEmbeddedByValue() }); ok {
		t.testEmbeddedByValue()
	}
	s.RegisterService(&GunService_ServiceDesc, srv)
}

func _GunService_Tun_Handler(srv interface{}, stream grpc.ServerStream) error {
	return srv.(GunServiceServer).Tun(&grpc.GenericServerStream[Hunk, Hunk]{ServerStream: stream})
}

type GunService_TunServer = grpc.BidiStreamingServer[Hunk, Hunk]

var GunService_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "transport.v2raygrpc.GunService",
	HandlerType: (*GunServiceServer)(nil),
	Methods:     []grpc.MethodDesc{},
	Streams: []grpc.StreamDesc{
		{
			StreamName:    "Tun",
			Handler:       _GunService_Tun_Handler,
			ServerStreams: true,
			ClientStreams: true,
		},
	},
	Metadata: "transport/v2raygrpc/stream.proto",
}
