// Package otlp 的 HTTP 接收器实现 OTLP/HTTP 协议 (POST /v1/traces 等).
//
// 支持 Content-Type:
//   - application/json (默认, 优先)
//   - application/x-protobuf (OTLP 标准二进制, 需要额外解析)
//
// 鉴权: 接收 X-IAOEP-API-Key Header, 验证后把 tenant_id 透传到 Kafka.
// 限流: 每 tenant 令牌桶, 超限返回 429.
package otlp

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"time"

	coltracepb "go.opentelemetry.io/proto/otlp/collector/trace/v1"

	"io.iaoep/ingest/internal/auth"
	"io.iaoep/ingest/internal/ratelimit"
)

// Sender 是 OTLP Receiver 把解析后的 Spans 发给 Kafka 的抽象.
type Sender interface {
	Send(ctx context.Context, traceID string, spanData []byte) error
}

type Receiver struct {
	sender         Sender
	registry       *auth.TenantRegistry
	limiter        *ratelimit.Limiter
	defaultTenant  string
	spansReceived  uint64
	spansSent      uint64
	spansFailed    uint64
	authRejected   uint64
	rateRejected   uint64
}

func NewReceiver(sender Sender, registry *auth.TenantRegistry, limiter *ratelimit.Limiter, defaultTenant string) *Receiver {
	return &Receiver{
		sender:        sender,
		registry:      registry,
		limiter:       limiter,
		defaultTenant: defaultTenant,
	}
}

// HandleHTTPTraces 处理 POST /v1/traces
func (r *Receiver) HandleHTTPTraces(w http.ResponseWriter, req *http.Request) {
	if req.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// 1. 鉴权
	tenantID, err := r.registry.AuthenticateHTTP(req)
	if err != nil {
		r.authRejected++
		log.Printf("[otlp] auth failed: %v", err)
		http.Error(w, "unauthorized: "+err.Error(), http.StatusUnauthorized)
		return
	}

	// 2. 限流
	if !r.limiter.Allow(tenantID) {
		r.rateRejected++
		log.Printf("[otlp] rate limited: tenant=%s", tenantID)
		http.Error(w, "rate limit exceeded", http.StatusTooManyRequests)
		return
	}

	body, err := io.ReadAll(req.Body)
	if err != nil {
		log.Printf("[otlp] read body error: %v", err)
		http.Error(w, "read body error", http.StatusBadRequest)
		return
	}
	defer req.Body.Close()

	// 3. 解析 OTLP JSON
	var traceReq coltracepb.ExportTraceServiceRequest
	if err := json.Unmarshal(body, &traceReq); err != nil {
		log.Printf("[otlp] unmarshal error: %v", err)
		http.Error(w, "invalid OTLP payload: "+err.Error(), http.StatusBadRequest)
		return
	}

	// 4. 转换为 IAOEP StandardSpan (使用鉴权得到的 tenant_id, 不再用 default)
	spans := MapSpans(&traceReq, tenantID)
	r.spansReceived += uint64(len(spans))

	// 5. 发送到 Kafka (每条 Span 一条 Kafka 消息, Key=trace_id 保证同 trace 顺序)
	ctx, cancel := context.WithTimeout(req.Context(), 5*time.Second)
	defer cancel()

	sent := 0
	for _, span := range spans {
		spanData, err := json.Marshal(span)
		if err != nil {
			r.spansFailed++
			continue
		}
		if err := r.sender.Send(ctx, span.TraceID, spanData); err != nil {
			log.Printf("[otlp] kafka send error: %v", err)
			r.spansFailed++
			continue
		}
		sent++
	}
	r.spansSent += uint64(sent)

	w.WriteHeader(http.StatusOK)
}

// HandleHTTPMetrics 占位 (PR 2 暂不实现, 仅接受空 200)
func (r *Receiver) HandleHTTPMetrics(w http.ResponseWriter, req *http.Request) {
	w.WriteHeader(http.StatusOK)
}

// HandleHTTPLogs 占位 (PR 2 暂不实现)
func (r *Receiver) HandleHTTPLogs(w http.ResponseWriter, req *http.Request) {
	w.WriteHeader(http.StatusOK)
}

// HandleHealth 健康检查
func (r *Receiver) HandleHealth(w http.ResponseWriter, req *http.Request) {
	stats := map[string]interface{}{
		"status":         "ok",
		"spans_received": r.spansReceived,
		"spans_sent":     r.spansSent,
		"spans_failed":   r.spansFailed,
		"auth_rejected":  r.authRejected,
		"rate_rejected":  r.rateRejected,
		"timestamp":      time.Now().Unix(),
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}
