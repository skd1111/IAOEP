# Spring AI Customer Service Demo

端到端示例:**Spring AI Agent + IAOEP SDK → IAOEP Ingest → Kafka → Storage → ClickHouse**

5 分钟跑通完整链路,验证 IAOEP 端到端可用。

## 一键启动

```bash
# 在本目录
docker compose up --build

# 等待 ~60 秒 (ClickHouse + Kafka 启动)
# 查看启动日志
docker compose logs -f demo-agent
```

启动完成后,所有这些服务都在运行:
| 服务 | 端口 | 说明 |
|---|---|---|
| Demo Agent | http://localhost:8080 | Spring AI Agent,接收 `/chat` 请求 |
| Ingest Gateway | http://localhost:4318 | OTLP/HTTP 接收 |
| Storage | http://localhost:8081 | Kafka → ClickHouse |
| ClickHouse | http://localhost:8123 | 数据存储 |
| Kafka | localhost:29092 | 消息队列 |

## 发送测试请求

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "你好,介绍下你自己", "userId": "alice"}'
```

返回:
```json
{
  "query": "你好,介绍下你自己",
  "userId": "alice",
  "response": "你好,我是 IAOEP Demo Agent...",
  "latencyMs": 87
}
```

## 验证 trace

### 1. Ingest Gateway 收到 span

```bash
curl http://localhost:4318/health
# {"status":"ok", "spans_received":3, "spans_sent":3, ...}
```

### 2. Storage Worker 写入 ClickHouse

```bash
sleep 5  # 等待 flush
curl "http://iaoep:iaoep_dev_pwd@localhost:8123/?query=SELECT+count(*)+FROM+iaoep.traces"
# 3
```

### 3. 查看具体 trace

```bash
curl "http://iaoep:iaoep_dev_pwd@localhost:8123/?query=SELECT+span_name,+agent_name,+duration_ms+FROM+iaoep.traces+ORDER+BY+start_time_unix_nano+DESC+LIMIT+10+FORMAT+Vertical"
```

应该看到:
```
agent.run       customer-service-demo    87
llm.call        customer-service-demo    45
tool.execute    customer-service-demo    12
```

## 代码解读

### Agent 类

```java
@Service
public class CustomerServiceAgent {

    @ObservedAgent(name = "customer-service", skill = "general")
    public String chat(String query, String userId) {
        String prompt = "..." + dateTimeTools.getCurrentDateTime();  // 自动产生 tool.execute Span
        return callLlm("qwen3-turbo", prompt, 80, 200);              // 自动产生 llm.call Span
    }

    @ObservedLLMCall(system = "openai",
                      modelParam = "#modelName",
                      inputTokensParam = "#inputTokens",
                      outputTokensParam = "#outputTokens")
    public String callLlm(String modelName, String prompt,
                          Integer inputTokens, Integer outputTokens) {
        // 真实 LLM 调用
    }
}
```

### Tool 类

```java
@Component
public class DateTimeTools {
    @ObservedTool(name = "get_current_time")
    public String getCurrentDateTime() {
        return LocalDateTime.now().toString();
    }
}
```

## trace 在 IAOEP Dashboard 上的展示 (Phase 1 PR 8 后)

```
agent.run [customer-service, skill=general]     87ms
  ├─ llm.call [system=openai, model=qwen3-turbo]  45ms
  │   tokens: in=80, out=200, cost=¥0.002
  └─ tool.execute [name=get_current_time]          12ms
```

## 切换真实 LLM

当前 demo 直接返回 mock 响应。要切换到真实千问,修改 `CustomerServiceAgent.callLlm`:

```java
@ObservedLLMCall(system = "openai", modelParam = "#modelName", ...)
public String callLlm(String modelName, String prompt, Integer inputTokens, Integer outputTokens) {
    // 设置环境变量 AI_API_KEY=your-qwen-key
    ChatResponse response = chatClient.prompt()
            .user(prompt)
            .call()
            .chatResponse();
    return response.getResult().getOutput().getText();
}
```

并 `application.yml` 中配置:
```yaml
spring:
  ai:
    openai:
      api-key: ${AI_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
```

## 下一步

- 接入 Web Dashboard (PR 8) → 可视化 trace
- 接入 AI 评测 (Phase 2) → 自动评分 Agent 表现
- 接入更多 Agent 框架 (LangChain / AutoGen / 自研)
