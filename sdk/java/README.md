# IAOEP SDK for Spring Boot (Java)

Spring Boot Starter,通过声明式注解自动产生 OTLP trace,发送到 IAOEP Ingest Gateway。

## 5 分钟接入

### 1. 加依赖

```xml
<dependency>
    <groupId>io.iaoep</groupId>
    <artifactId>iaoep-sdk-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. 配置 (application.yml)

```yaml
iaoep:
  sdk:
    enabled: true
    otlp-endpoint: http://localhost:4318
    service-name: customer-service-agent
    tenant-id: acme-corp
    sampling-ratio: 1.0
```

或环境变量:
```bash
export IAOEP_EXPORTER_OTLP_ENDPOINT=http://iaoep-ingest:4318
export IAOEP_SERVICE_NAME=customer-service-agent
export IAOEP_TENANT_ID=acme-corp
export IAOEP_SAMPLING_RATIO=1.0
```

### 3. 标注 Agent 方法

```java
import io.iaoep.sdk.annotation.ObservedAgent;
import io.iaoep.sdk.annotation.ObservedLLMCall;
import io.iaoep.sdk.annotation.ObservedTool;

@Service
public class CustomerServiceAgent {

    @ObservedAgent(name = "customer-service", skill = "general")
    public String chat(String query) {
        String response = callLlm(query);  // 内层会被 @ObservedLLMCall 自动 trace
        return response;
    }

    @ObservedLLMCall(system = "dashscope", modelParam = "#modelName",
                      inputTokensParam = "#inputTokens",
                      outputTokensParam = "#outputTokens")
    public String callLlm(String modelName, String query,
                          Integer inputTokens, Integer outputTokens) {
        // 实际 LLM 调用
        return "你好,有什么可以帮你?";
    }
}

@Component
public class DateTimeTools {

    @ObservedTool(name = "get_current_time")
    public String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }
}
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

所有带 `@ObservedAgent` / `@ObservedLLMCall` / `@ObservedTool` 注解的方法调用都会自动产生 OTLP Span,发送到 Ingest Gateway。

## 注解 API

### `@ObservedAgent`

标注一个 Agent 方法,产生 `agent.run` Span。

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `name` | ✅ | - | Agent 名 (会作为 `agent.name` 属性) |
| `skill` | ❌ | `""` | 关联 Skill 名 |
| `captureInput` | ❌ | `false` | 是否捕获方法参数 (PII 风险) |
| `captureOutput` | ❌ | `false` | 是否捕获返回值 (PII 风险) |
| `maxPayloadLength` | ❌ | 4096 | payload 最大字符数 |

### `@ObservedLLMCall`

标注一个 LLM 调用方法,产生 `llm.call` Span。

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `system` | ✅ | - | LLM 供应商 (openai / dashscope / glm / deepseek) |
| `modelParam` | ❌ | `""` | SpEL, 从哪个方法参数取模型名 |
| `inputTokensParam` | ❌ | `""` | SpEL, 从哪个方法参数取 input tokens |
| `outputTokensParam` | ❌ | `""` | SpEL, 从哪个方法参数取 output tokens |

SpEL 示例: `#modelName` 取名为 `modelName` 的参数值。

### `@ObservedTool`

标注一个工具方法,产生 `tool.execute` Span。

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `name` | ✅ | - | 工具名 |
| `captureArguments` | ❌ | `false` | 是否捕获参数 |
| `maxPayloadLength` | ❌ | 2048 | payload 最大字符数 |

## 接入示例

完整示例见 [`examples/spring-ai-customer-service/`](../../examples/spring-ai-customer-service)。

## 兼容性

| IAOEP SDK | Spring Boot | Java |
|---|---|---|
| 0.1.x | 4.0.x | 17+ |
| 0.1.x (LTS) | 3.5.x | 17+ |

## 调试

```yaml
logging:
  level:
    io.iaoep.sdk: DEBUG
    io.opentelemetry: INFO
```

启用 debug 后,SDK 会打印每个 Span 的创建/结束,方便排查。

## 性能影响

| 操作 | 开销 |
|---|---|
| `@ObservedAgent` | ~50-100μs (AOP + Span 创建) |
| `@ObservedLLMCall` | ~100-200μs (含属性解析) |
| `@ObservedTool` | ~50μs |
| OTLP 批量发送 | 异步,不影响主流程 |

## License

[MIT](../../LICENSE)
