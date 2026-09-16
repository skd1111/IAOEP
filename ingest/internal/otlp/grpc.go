// Package otlp 的 gRPC server 实现 OTLP/gRPC 协议 (port 4317).
//
// 鉴权: gRPC metadata 传入 X-IAOEP-API-Key, 验证后透传 tenant_id.
// 限流: 同 HTTP, 令牌桶按 tenant 限流.
package otlp

import (
	"context"
	"encoding/json"
	"log"
	"net"

	coltracepb "go.opentelemetry.io/proto/otlp/collector/trace/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"io.iaoep/ingest/internal/auth"
	"io.iaoep/ingest/internal/ratelimit"
)

type traceServiceServer struct {
	coltracepb.UnimplementedTraceServiceServer
	receiver *Receiver
}

// Export 实现 OTLP TraceService.Export gRPC 方法
func (s *traceServiceServer) Export(ctx context.Context, req *coltracepb.ExportTraceServiceRequest) (*coltracepb.ExportTraceServiceResponse, error) {
	// 1. 鉴权 (gRPC metadata)
	tenantID, err := s.receiver.registry.AuthenticateGRPC(ctx)
	if err != nil {
		s.receiver.authRejected++
		log.Printf("[otlp-grpc] auth failed: %v", err)
		return nil, status.Error(codes.Unauthenticated, err.Error())
	}

	// 2. 限流
	if !s.receiver.limiter.Allow(tenantID) {
		s.receiver.rateRejected++
		log.Printf("[otlp-grpc] rate limited: tenant=%s", tenantID)
		return nil, status.Error(codes.ResourceExhausted, "rate limit exceeded")
	}

	spans := MapSpans(req, tenantID)
	s.receiver.spansReceived += uint64(len(spans))

	sent := 0
	for _, span := range spans {
		spanData, err := json.Marshal(span)
		if err != nil {
			s.receiver.spansFailed++
			continue
		}
		if err := s.receiver.sender.Send(ctx, span.TraceID, spanData); err != nil {
			log.Printf("[otlp-grpc] kafka send error: %v", err)
			s.receiver.spansFailed++
			continue
		}
		sent++
	}
	s.receiver.spansSent += uint64(sent)

	return &coltracepb.ExportTraceServiceResponse{}, nil
}

// RunGRPCServer 启动 OTLP/gRPC server
func RunGRPCServer(addr string, receiver *Receiver) error {
	lis, err := net.Listen("tcp", addr)
	if err != nil {
		return err
	}
	s := grpc.NewServer()
	coltracepb.RegisterTraceServiceServer(s, &traceServiceServer{receiver: receiver})
	log.Printf("[otlp-grpc] listening on %s", addr)
	return s.Serve(lis)
}

// 兼容旧 API (无认证/限流的简化版本), 暂留接口稳定
var _ = auth.HeaderName
var _ = ratelimit.NewLimiter
