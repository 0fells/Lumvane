package daemon

import (
	context "context"

	grpc "google.golang.org/grpc"
	codes "google.golang.org/grpc/codes"
	status "google.golang.org/grpc/status"
	emptypb "google.golang.org/protobuf/types/known/emptypb"
)

const _ = grpc.SupportPackageIsVersion9

const (
	ManagedService_StopService_FullMethodName           = "/daemon.ManagedService/StopService"
	ManagedService_ReloadService_FullMethodName         = "/daemon.ManagedService/ReloadService"
	ManagedService_GetSystemProxyStatus_FullMethodName  = "/daemon.ManagedService/GetSystemProxyStatus"
	ManagedService_SetSystemProxyEnabled_FullMethodName = "/daemon.ManagedService/SetSystemProxyEnabled"
	ManagedService_TriggerDebugCrash_FullMethodName     = "/daemon.ManagedService/TriggerDebugCrash"
	ManagedService_TriggerOOMReport_FullMethodName      = "/daemon.ManagedService/TriggerOOMReport"
)

type ManagedServiceClient interface {
	StopService(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error)
	ReloadService(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error)
	GetSystemProxyStatus(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*SystemProxyStatus, error)
	SetSystemProxyEnabled(ctx context.Context, in *SetSystemProxyEnabledRequest, opts ...grpc.CallOption) (*emptypb.Empty, error)
	TriggerDebugCrash(ctx context.Context, in *DebugCrashRequest, opts ...grpc.CallOption) (*emptypb.Empty, error)
	TriggerOOMReport(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error)
}

type managedServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewManagedServiceClient(cc grpc.ClientConnInterface) ManagedServiceClient {
	return &managedServiceClient{cc}
}

func (c *managedServiceClient) StopService(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(emptypb.Empty)
	err := c.cc.Invoke(ctx, ManagedService_StopService_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *managedServiceClient) ReloadService(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(emptypb.Empty)
	err := c.cc.Invoke(ctx, ManagedService_ReloadService_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *managedServiceClient) GetSystemProxyStatus(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*SystemProxyStatus, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(SystemProxyStatus)
	err := c.cc.Invoke(ctx, ManagedService_GetSystemProxyStatus_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *managedServiceClient) SetSystemProxyEnabled(ctx context.Context, in *SetSystemProxyEnabledRequest, opts ...grpc.CallOption) (*emptypb.Empty, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(emptypb.Empty)
	err := c.cc.Invoke(ctx, ManagedService_SetSystemProxyEnabled_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *managedServiceClient) TriggerDebugCrash(ctx context.Context, in *DebugCrashRequest, opts ...grpc.CallOption) (*emptypb.Empty, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(emptypb.Empty)
	err := c.cc.Invoke(ctx, ManagedService_TriggerDebugCrash_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *managedServiceClient) TriggerOOMReport(ctx context.Context, in *emptypb.Empty, opts ...grpc.CallOption) (*emptypb.Empty, error) {
	cOpts := append([]grpc.CallOption{grpc.StaticMethod()}, opts...)
	out := new(emptypb.Empty)
	err := c.cc.Invoke(ctx, ManagedService_TriggerOOMReport_FullMethodName, in, out, cOpts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

type ManagedServiceServer interface {
	StopService(context.Context, *emptypb.Empty) (*emptypb.Empty, error)
	ReloadService(context.Context, *emptypb.Empty) (*emptypb.Empty, error)
	GetSystemProxyStatus(context.Context, *emptypb.Empty) (*SystemProxyStatus, error)
	SetSystemProxyEnabled(context.Context, *SetSystemProxyEnabledRequest) (*emptypb.Empty, error)
	TriggerDebugCrash(context.Context, *DebugCrashRequest) (*emptypb.Empty, error)
	TriggerOOMReport(context.Context, *emptypb.Empty) (*emptypb.Empty, error)
	mustEmbedUnimplementedManagedServiceServer()
}

type UnimplementedManagedServiceServer struct{}

func (UnimplementedManagedServiceServer) StopService(context.Context, *emptypb.Empty) (*emptypb.Empty, error) {
	return nil, status.Error(codes.Unimplemented, "method StopService not implemented")
}

func (UnimplementedManagedServiceServer) ReloadService(context.Context, *emptypb.Empty) (*emptypb.Empty, error) {
	return nil, status.Error(codes.Unimplemented, "method ReloadService not implemented")
}

func (UnimplementedManagedServiceServer) GetSystemProxyStatus(context.Context, *emptypb.Empty) (*SystemProxyStatus, error) {
	return nil, status.Error(codes.Unimplemented, "method GetSystemProxyStatus not implemented")
}

func (UnimplementedManagedServiceServer) SetSystemProxyEnabled(context.Context, *SetSystemProxyEnabledRequest) (*emptypb.Empty, error) {
	return nil, status.Error(codes.Unimplemented, "method SetSystemProxyEnabled not implemented")
}

func (UnimplementedManagedServiceServer) TriggerDebugCrash(context.Context, *DebugCrashRequest) (*emptypb.Empty, error) {
	return nil, status.Error(codes.Unimplemented, "method TriggerDebugCrash not implemented")
}

func (UnimplementedManagedServiceServer) TriggerOOMReport(context.Context, *emptypb.Empty) (*emptypb.Empty, error) {
	return nil, status.Error(codes.Unimplemented, "method TriggerOOMReport not implemented")
}
func (UnimplementedManagedServiceServer) mustEmbedUnimplementedManagedServiceServer() {}
func (UnimplementedManagedServiceServer) testEmbeddedByValue()                        {}

type UnsafeManagedServiceServer interface {
	mustEmbedUnimplementedManagedServiceServer()
}

func RegisterManagedServiceServer(s grpc.ServiceRegistrar, srv ManagedServiceServer) {
	if t, ok := srv.(interface{ testEmbeddedByValue() }); ok {
		t.testEmbeddedByValue()
	}
	s.RegisterService(&ManagedService_ServiceDesc, srv)
}

func _ManagedService_StopService_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(emptypb.Empty)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).StopService(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_StopService_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).StopService(ctx, req.(*emptypb.Empty))
	}
	return interceptor(ctx, in, info, handler)
}

func _ManagedService_ReloadService_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(emptypb.Empty)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).ReloadService(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_ReloadService_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).ReloadService(ctx, req.(*emptypb.Empty))
	}
	return interceptor(ctx, in, info, handler)
}

func _ManagedService_GetSystemProxyStatus_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(emptypb.Empty)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).GetSystemProxyStatus(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_GetSystemProxyStatus_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).GetSystemProxyStatus(ctx, req.(*emptypb.Empty))
	}
	return interceptor(ctx, in, info, handler)
}

func _ManagedService_SetSystemProxyEnabled_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(SetSystemProxyEnabledRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).SetSystemProxyEnabled(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_SetSystemProxyEnabled_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).SetSystemProxyEnabled(ctx, req.(*SetSystemProxyEnabledRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _ManagedService_TriggerDebugCrash_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(DebugCrashRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).TriggerDebugCrash(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_TriggerDebugCrash_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).TriggerDebugCrash(ctx, req.(*DebugCrashRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _ManagedService_TriggerOOMReport_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(emptypb.Empty)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(ManagedServiceServer).TriggerOOMReport(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: ManagedService_TriggerOOMReport_FullMethodName,
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(ManagedServiceServer).TriggerOOMReport(ctx, req.(*emptypb.Empty))
	}
	return interceptor(ctx, in, info, handler)
}

var ManagedService_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "daemon.ManagedService",
	HandlerType: (*ManagedServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "StopService",
			Handler:    _ManagedService_StopService_Handler,
		},
		{
			MethodName: "ReloadService",
			Handler:    _ManagedService_ReloadService_Handler,
		},
		{
			MethodName: "GetSystemProxyStatus",
			Handler:    _ManagedService_GetSystemProxyStatus_Handler,
		},
		{
			MethodName: "SetSystemProxyEnabled",
			Handler:    _ManagedService_SetSystemProxyEnabled_Handler,
		},
		{
			MethodName: "TriggerDebugCrash",
			Handler:    _ManagedService_TriggerDebugCrash_Handler,
		},
		{
			MethodName: "TriggerOOMReport",
			Handler:    _ManagedService_TriggerOOMReport_Handler,
		},
	},
	Streams:  []grpc.StreamDesc{},
	Metadata: "daemon/managed_service.proto",
}
