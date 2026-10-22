package adapter

import (
	"context"
	"net"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

type (
	LegacyConnectionHandlerFunc = func(ctx context.Context, conn net.Conn, metadata InboundContext) error

	LegacyPacketConnectionHandlerFunc = func(ctx context.Context, conn N.PacketConn, metadata InboundContext) error
)

type LegacyUpstreamHandlerAdapter interface {
	N.TCPConnectionHandler
	N.UDPConnectionHandler
	E.Handler
}

func NewLegacyUpstreamHandler(
	metadata InboundContext,
	connectionHandler LegacyConnectionHandlerFunc,
	packetHandler LegacyPacketConnectionHandlerFunc,
	errorHandler E.Handler,
) LegacyUpstreamHandlerAdapter {
	return &legacyUpstreamHandlerWrapper{
		metadata:          metadata,
		connectionHandler: connectionHandler,
		packetHandler:     packetHandler,
		errorHandler:      errorHandler,
	}
}

var _ LegacyUpstreamHandlerAdapter = (*legacyUpstreamHandlerWrapper)(nil)

type legacyUpstreamHandlerWrapper struct {
	metadata          InboundContext
	connectionHandler LegacyConnectionHandlerFunc
	packetHandler     LegacyPacketConnectionHandlerFunc
	errorHandler      E.Handler
}

func (w *legacyUpstreamHandlerWrapper) NewConnection(ctx context.Context, conn net.Conn, metadata M.Metadata) error {
	myMetadata := w.metadata
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.connectionHandler(ctx, conn, myMetadata)
}

func (w *legacyUpstreamHandlerWrapper) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata M.Metadata) error {
	myMetadata := w.metadata
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.packetHandler(ctx, conn, myMetadata)
}

func (w *legacyUpstreamHandlerWrapper) NewError(ctx context.Context, err error) {
	w.errorHandler.NewError(ctx, err)
}

func UpstreamMetadata(metadata InboundContext) M.Metadata {
	return M.Metadata{
		Source:      metadata.Source.Unwrap(),
		Destination: metadata.Destination.Unwrap(),
	}
}

type legacyUpstreamContextHandlerWrapper struct {
	connectionHandler LegacyConnectionHandlerFunc
	packetHandler     LegacyPacketConnectionHandlerFunc
	errorHandler      E.Handler
}

func NewLegacyUpstreamContextHandler(
	connectionHandler LegacyConnectionHandlerFunc,
	packetHandler LegacyPacketConnectionHandlerFunc,
	errorHandler E.Handler,
) LegacyUpstreamHandlerAdapter {
	return &legacyUpstreamContextHandlerWrapper{
		connectionHandler: connectionHandler,
		packetHandler:     packetHandler,
		errorHandler:      errorHandler,
	}
}

func (w *legacyUpstreamContextHandlerWrapper) NewConnection(ctx context.Context, conn net.Conn, metadata M.Metadata) error {
	myMetadata := ContextFrom(ctx)
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.connectionHandler(ctx, conn, *myMetadata)
}

func (w *legacyUpstreamContextHandlerWrapper) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata M.Metadata) error {
	myMetadata := ContextFrom(ctx)
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.packetHandler(ctx, conn, *myMetadata)
}

func (w *legacyUpstreamContextHandlerWrapper) NewError(ctx context.Context, err error) {
	w.errorHandler.NewError(ctx, err)
}

func NewLegacyRouteHandler(
	metadata InboundContext,
	router ConnectionRouter,
	logger logger.ContextLogger,
) LegacyUpstreamHandlerAdapter {
	return &legacyRouteHandlerWrapper{
		metadata: metadata,
		router:   router,
		logger:   logger,
	}
}

func NewLegacyRouteContextHandler(
	router ConnectionRouter,
	logger logger.ContextLogger,
) LegacyUpstreamHandlerAdapter {
	return &legacyRouteContextHandlerWrapper{
		router: router,
		logger: logger,
	}
}

var _ LegacyUpstreamHandlerAdapter = (*legacyRouteHandlerWrapper)(nil)

type legacyRouteHandlerWrapper struct {
	metadata InboundContext
	router   ConnectionRouter
	logger   logger.ContextLogger
}

func (w *legacyRouteHandlerWrapper) NewConnection(ctx context.Context, conn net.Conn, metadata M.Metadata) error {
	myMetadata := w.metadata
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.router.RouteConnection(ctx, conn, myMetadata)
}

func (w *legacyRouteHandlerWrapper) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata M.Metadata) error {
	myMetadata := w.metadata
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.router.RoutePacketConnection(ctx, conn, myMetadata)
}

func (w *legacyRouteHandlerWrapper) NewError(ctx context.Context, err error) {
	w.logger.ErrorContext(ctx, err)
}

var _ LegacyUpstreamHandlerAdapter = (*legacyRouteContextHandlerWrapper)(nil)

type legacyRouteContextHandlerWrapper struct {
	router ConnectionRouter
	logger logger.ContextLogger
}

func (w *legacyRouteContextHandlerWrapper) NewConnection(ctx context.Context, conn net.Conn, metadata M.Metadata) error {
	myMetadata := ContextFrom(ctx)
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.router.RouteConnection(ctx, conn, *myMetadata)
}

func (w *legacyRouteContextHandlerWrapper) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata M.Metadata) error {
	myMetadata := ContextFrom(ctx)
	if metadata.Source.IsValid() {
		myMetadata.Source = metadata.Source
	}
	if metadata.Destination.IsValid() {
		myMetadata.Destination = metadata.Destination
	}
	return w.router.RoutePacketConnection(ctx, conn, *myMetadata)
}

func (w *legacyRouteContextHandlerWrapper) NewError(ctx context.Context, err error) {
	w.logger.ErrorContext(ctx, err)
}
