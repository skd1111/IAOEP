// Package iaoep 提供 IAOEP 的 Go SDK: 声明式 OTLP tracing + A/B Test 集成.
//
// 5 分钟接入:
//
//	import (
//	    "context"
//	    "github.com/iaoep/sdk-go"
//	)
//
//	func init() {
//	    iaoep.Configure(iaoep.Config{
//	        Endpoint:   "http://localhost:4318",
//	        ServiceName: "my-agent",
//	        TenantID:   "acme-corp",
//	    })
//	}
//
//	func Chat(ctx context.Context, query string) (string, error) {
//	    return iaoep.ObservedAgent(ctx, "chat", "general", func(ctx context.Context) (string, error) {
//	        return callLLM(ctx, query)
//	    })
//	}
//
// 三个装饰器 (对齐 Java/Python/TypeScript SDK):
//   - ObservedAgent: 顶层 Agent, 产生 agent.run Span
//   - ObservedLLMCall: LLM 调用, 产生 llm.call Span, 含 token
//   - ObservedTool:   工具调用, 产生 tool.execute Span
//
// 一个 A/B Test 装饰器:
//   - ObservedABTest: 按 trace_id 分配 baseline/candidate, 下游 SDK 通过 context 读 group
package iaoep

import (
	"context"
	"fmt"
	"os"
	"sync"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Config 是 IAOEP SDK 的配置.
type Config struct {
	Endpoint    string  // OTLP/HTTP 端点 (默认 http://localhost:4318)
	ServiceName string  // 服务名 (必填, 对应 service.name)
	TenantID    string  // 租户 ID (默认 "default")
	Insecure    bool    // HTTP 是否 insecure (默认 true, 生产 false)
}

var (
	initOnce sync.Once
	tracerP  *sdktrace.TracerProvider
)

// Configure 初始化 IAOEP SDK (只初始化一次).
//
// 自动创建 OTel SDK + OTLP HTTP exporter + 注册 TracerProvider.
// 配置通过环境变量 (IAOEP_EXPORTER_OTLP_ENDPOINT / IAOEP_SERVICE_NAME / IAOEP_TENANT_ID).
func Configure(cfg Config) error {
	var initErr error
	initOnce.Do(func() {
		endpoint := cfg.Endpoint
		if endpoint == "" {
			endpoint = os.Getenv("IAOEP_EXPORTER_OTLP_ENDPOINT")
		}
		if endpoint == "" {
			endpoint = "http://localhost:4318"
		}

		serviceName := cfg.ServiceName
		if serviceName == "" {
			serviceName = os.Getenv("IAOEP_SERVICE_NAME")
		}
		if serviceName == "" {
			serviceName = "iaoep-go-agent"
		}

		tenantID := cfg.TenantID
		if tenantID == "" {
			tenantID = os.Getenv("IAOEP_TENANT_ID")
		}
		if tenantID == "" {
			tenantID = "default"
		}

		// OTLP HTTP exporter
		ctx := context.Background()
		exporter, err := otlptracehttp.New(ctx,
			otlptracehttp.WithEndpoint(endpoint),
			otlptracehttp.WithInsecure())
		if err != nil {
			initErr = fmt.Errorf("create OTLP exporter: %w", err)
			return
		}

		// Resource attributes
		res, err := resource.Merge(
			resource.Default(),
			resource.NewWithAttributes(
				semconv.SchemaURL,
				semconv.ServiceName(serviceName),
				semconv.DeploymentEnvironmentName("iaoep-sdk-go"),
				semconv.AttributeKey("iaoep.tenant_id").String(tenantID),
			),
		)
		if err != nil {
			initErr = fmt.Errorf("create resource: %w", err)
			return
		}

		// TracerProvider
		tracerP = sdktrace.NewTracerProvider(
			sdktrace.WithBatcher(exporter),
			sdktrace.WithResource(res),
		)
		otel.SetTracerProvider(tracerP)
		otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		))
	})

	return initErr
}

// Shutdown 关闭 TracerProvider (应用退出时调用).
func Shutdown(ctx context.Context) error {
	if tracerP == nil {
		return nil
	}
	return tracerP.Shutdown(ctx)
}

// Tracer 返回 IAOEP SDK 的 Tracer (懒加载 Initialize).
func Tracer() interface{ /* trace.Tracer */ } {
	if tracerP == nil {
		_ = Configure(Config{})
	}
	return otel.Tracer("io.iaoep.sdk")
}
