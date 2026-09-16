# IAOEP Go SDK

声明式 OTLP tracing + A/B Test 集成 for Go (1.22+)。

## 5 分钟接入

### 1. 安装

```bash
go get github.com/iaoep/sdk-go
```

### 2. 初始化 (应用启动)

```go
package main

import (
    "github.com/iaoep/sdk-go"
)

func init() {
    iaoep.Configure(iaoep.Config{
        Endpoint:   "http://localhost:4318",
        ServiceName: "my-agent",
        TenantID:   "acme-corp",
    })
}
```

或用环境变量:
```bash
export IAOEP_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export IAOEP_SERVICE_NAME=my-agent
export IAOEP_TENANT_ID=acme-corp
```

### 3. 装饰 Agent 方法

```go
import (
    "context"
    "github.com/iaoep/sdk-go"
)

func Chat(ctx context.Context, query string) (string, error) {
    return iaoep.ObservedAgent(ctx, "customer-service", "general",
        func(ctx context.Context) (string, error) {
            return callLLM(ctx, query)
        })
}

func callLLM(ctx context.Context, query string) (string, error) {
    return iaoep.ObservedLLMCall(ctx, "openai", "qwen3-turbo", 120, 80,
        func(ctx context.Context) (string, error) {
            // 实际 LLM 调用
            return "response", nil
        })
}

func getTime(ctx context.Context) (string, error) {
    return iaoep.ObservedTool(ctx, "get_current_time",
        func(ctx context.Context) (string, error) {
            return time.Now().String(), nil
        })
}
```

### 4. A/B Test (Phase 4-5)

```go
func ChatWithAB(ctx context.Context, query string) (string, error) {
    return iaoep.ObservedABTest(ctx, "customer-service-ab",
        func(ctx context.Context) (string, error) {
            group := iaoep.ABTestGroupFromContext(ctx)  // "baseline" / "candidate"
            model := "qwen3-turbo"
            if group == "candidate" {
                model = "qwen3-max"
            }
            return callLLMWithModel(ctx, model, query)
        })
}
```

## API

### 函数签名 (Go 1.18+ 泛型)

```go
func ObservedAgent[T any](ctx, name, skill string, fn func(context.Context) (T, error)) (T, error)
func ObservedLLMCall[T any](ctx, system, model string, inputTokens, outputTokens int, fn func(context.Context) (T, error)) (T, error)
func ObservedTool[T any](ctx, name string, fn func(context.Context) (T, error)) (T, error)
func ObservedABTest[T any](ctx, abTestName string, fn func(context.Context) (T, error)) (T, error)
```

### Span 属性

| 函数 | Span 名 | 必填属性 |
|---|---|---|
| `ObservedAgent` | `agent.run` | `agent.name`, `agent.skill.name` |
| `ObservedLLMCall` | `llm.call` | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens` |
| `ObservedTool` | `tool.execute` | `tool.name`, `tool.call.id` |
| `ObservedABTest` | `ab.test` | `ab_test_name`, `ab_test_group` |

## 性能

| 操作 | 开销 |
|---|---|
| `ObservedAgent` | ~50-100μs |
| `ObservedLLMCall` | ~100-200μs |
| `ObservedTool` | ~50μs |
| OTLP 导出 | 异步,不影响主流程 |

## 测试

```bash
cd sdk/go
go test -v ./...
```

## License

[MIT](../../LICENSE)
