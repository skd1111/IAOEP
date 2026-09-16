# IAOEP Ingest Gateway (Go)

OTLP (gRPC + HTTP) 接收服务,把 Span 树映射为 IAOEP 标准格式,推送到 Kafka。

## 架构

```
External Agent ──OTLP/HTTP──>  ┌──────────────────┐
                          ──>  │ Ingest Gateway  │ ──> Kafka topic `iaoep.traces`
External Agent ──OTLP/gRPC──>  │   (Go)         │
                                └──────────────────┘
```

## 配置 (环境变量)

| 变量 | 默认 | 说明 |
|---|---|---|
| `IAOEP_INGEST_HTTP_PORT` | 4318 | OTLP/HTTP 端口 (POST /v1/traces) |
| `IAOEP_INGEST_GRPC_PORT` | 4317 | OTLP/gRPC 端口 |
| `IAOEP_INGEST_KAFKA_BROKERS` | localhost:29092 | Kafka brokers (逗号分隔) |
| `IAOEP_INGEST_KAFKA_TOPIC` | iaoep.traces | Kafka topic |
| `IAOEP_INGEST_TENANT_ID` | default | 默认 tenant id |

## 本地开发

```bash
# 启动 ClickHouse + Kafka
cd ..
docker compose up -d clickhouse kafka

# 启动 Ingest Gateway
cd ingest
go run ./cmd/ingest

# 发送测试 trace
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data-binary @testdata/sample-trace.json
```

## Docker 构建

```bash
docker build -t iaoep/ingest:0.1.0 .
docker run -p 4317:4317 -p 4318:4318 \
  -e IAOEP_INGEST_KAFKA_BROKERS=kafka:9092 \
  iaoep/ingest:0.1.0
```

## 端点

| 端点 | 方法 | 说明 |
|---|---|---|
| `/v1/traces` | POST | OTLP/HTTP traces (protobuf JSON) |
| `/v1/metrics` | POST | OTLP/HTTP metrics (Phase 3) |
| `/v1/logs` | POST | OTLP/HTTP logs (Phase 3) |
| `/health` | GET | 健康检查 + 统计 |

## 关键 Span 属性 (对齐 OTel GenAI SemConv 1.0+)

| 属性 | 适用 Span | 说明 |
|---|---|---|
| `agent.name` | agent.run | Agent 标识 |
| `session.id` / `gen_ai.conversation.id` | agent.run | 会话 ID |
| `user.id` | agent.run | 用户 ID |
| `agent.skill.name` | agent.run | Skill 名称 |
| `agent.turns.total` | agent.run | 循环 turn 数 |
| `gen_ai.system` | llm.call | openai / dashscope / glm / deepseek |
| `gen_ai.request.model` | llm.call | 模型名 |
| `gen_ai.usage.input_tokens` / `output_tokens` | llm.call | Token 数 |
| `gen_ai.cost.cny` | llm.call | 成本 (CNY) |
| `tool.name` | tool.execute | 工具名 |
| `tool.call.id` | tool.execute | 工具调用 ID |
| `tool.error.type` | tool.execute | 错误类型 |

详见 [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) 和 IAOEP 设计文档 § 6。
