// Package auth 实现 API Key 鉴权.
//
// 简化方案 (Phase 1):
//   - API Key 通过 HTTP Header `X-IAOEP-API-Key` 或 gRPC metadata 传入
//   - 静态配置: 通过环境变量 `IAOEP_INGEST_API_KEYS` 配置 "tenant1:key1,tenant2:key2"
//   - 验证后,把 tenant_id 放入 context 供下游使用
//
// Phase 2 可扩展:
//   - 动态密钥管理 (从 PostgreSQL 加载)
//   - 密钥轮换 + 过期时间
//   - 速率限制 (按 tenant / API Key)
package auth

import (
	"context"
	"errors"
	"net/http"
	"os"
	"strings"

	"google.golang.org/grpc/metadata"
)

// HeaderName HTTP Header 名 (gRPC metadata key 也用此名, 大小写不敏感)
const HeaderName = "x-iaoep-api-key"

// ContextKey 用于从 context 取 tenant_id
type ContextKey string

const TenantIDKey ContextKey = "tenant_id"

// TenantRegistry 保存 tenant_id 与 API Key 的映射
type TenantRegistry struct {
	// tenant_id → api key
	keys map[string]string
}

// NewTenantRegistry 从环境变量加载 tenant → api key 映射.
//
// 格式: "tenant1:key1,tenant2:key2"
// 如果未配置, 允许任何 key 通过 (dev mode, 警告日志).
func NewTenantRegistry() *TenantRegistry {
	r := &TenantRegistry{keys: make(map[string]string)}
	raw := os.Getenv("IAOEP_INGEST_API_KEYS")
	if raw == "" {
		return r // dev mode
	}
	for _, entry := range strings.Split(raw, ",") {
		parts := strings.SplitN(strings.TrimSpace(entry), ":", 2)
		if len(parts) != 2 {
			continue
		}
		r.keys[parts[0]] = parts[1]
	}
	return r
}

// Validate 检查 API Key, 返回对应 tenant_id.
func (r *TenantRegistry) Validate(apiKey string) (string, error) {
	if apiKey == "" {
		return "", errors.New("missing api key")
	}
	// dev mode: 无配置时接受任何非空 key, 落到 default tenant
	if len(r.keys) == 0 {
		return "default", nil
	}
	for tenant, key := range r.keys {
		if key == apiKey {
			return tenant, nil
		}
	}
	return "", errors.New("invalid api key")
}

// AuthenticateHTTP 从 HTTP Request 提取 API Key 并验证, 返回 tenant_id.
func (r *TenantRegistry) AuthenticateHTTP(req *http.Request) (string, error) {
	apiKey := req.Header.Get(HeaderName)
	if apiKey == "" {
		apiKey = req.URL.Query().Get("api_key") // 备选: query string
	}
	return r.Validate(apiKey)
}

// AuthenticateGRPC 从 gRPC metadata 提取 API Key 并验证, 返回 tenant_id.
func (r *TenantRegistry) AuthenticateGRPC(ctx context.Context) (string, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return "", errors.New("missing metadata")
	}
	values := md.Get(HeaderName)
	if len(values) == 0 {
		return "", errors.New("missing api key")
	}
	return r.Validate(values[0])
}

// WithTenant 把 tenant_id 放入 context.
func WithTenant(ctx context.Context, tenantID string) context.Context {
	return context.WithValue(ctx, TenantIDKey, tenantID)
}

// TenantFromContext 从 context 取 tenant_id (供下游 Kafka 生产者使用).
func TenantFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(TenantIDKey).(string); ok {
		return v
	}
	return ""
}
