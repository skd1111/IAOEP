# IAOEP Storage Worker (Java)

Kafka Consumer → ClickHouse 批量写入服务。

## 职责

1. 订阅 Kafka topic `iaoep.traces`
2. 反序列化 IAOEP `StandardSpan` JSON
3. 批量写入 ClickHouse 表 `iaoep.traces`
4. 暴露 Prometheus 指标 + 健康检查

## 配置 (环境变量)

| 变量 | 默认 | 说明 |
|---|---|---|
| `IAOEP_STORAGE_KAFKA_BROKERS` | `localhost:29092` | Kafka brokers |
| `IAOEP_STORAGE_KAFKA_TOPIC` | `iaoep.traces` | 订阅 topic |
| `IAOEP_STORAGE_CLICKHOUSE_URL` | `jdbc:clickhouse://localhost:8123/iaoep` | ClickHouse JDBC URL |
| `IAOEP_STORAGE_CLICKHOUSE_USER` | `iaoep` | ClickHouse 用户 |
| `IAOEP_STORAGE_CLICKHOUSE_PASSWORD` | `iaoep_dev_pwd` | ClickHouse 密码 |
| `SERVER_PORT` | `8081` | HTTP 端口 |

## 批量策略

| 配置 | 默认 | 说明 |
|---|---|---|
| `iaoep.storage.batch.size` | 1000 | 累积 N 条 flush |
| `iaoep.storage.batch.flush-interval-ms` | 5000 | 或每 X ms flush |

两者取先到者。例如:流量高峰时按 size 触发 flush;流量低时按 interval 触发 flush 兜底。

## Prometheus 指标

- `iaoep_spans_written_total` — 累计成功写入 ClickHouse 的 span 数
- `iaoep_spans_write_errors_total` — 累计写入失败数
- `iaoep_spans_write_latency_seconds_*` — 批量写入延迟分布

## 健康检查

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP", ...}
```

## 本地开发

```bash
# 启动 ClickHouse + Kafka
cd ..
docker compose up -d clickhouse kafka

# 启动 Storage
cd storage
mvn spring-boot:run

# 启动 Ingest Gateway (另一个进程)
cd ../ingest
go run ./cmd/ingest

# 发送测试 trace
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data-binary @../ingest/testdata/sample-trace.json

# 5 秒后查 ClickHouse
curl "http://iaoep:iaoep_dev_pwd@localhost:8123/?query=SELECT+count(*)+FROM+iaoep.traces"
```

## Docker

```bash
docker build -t iaoep/storage:0.1.0 .
docker run -p 8081:8081 \
  -e IAOEP_STORAGE_KAFKA_BROKERS=kafka:9092 \
  -e IAOEP_STORAGE_CLICKHOUSE_URL=jdbc:clickhouse://clickhouse:8123/iaoep \
  iaoep/storage:0.1.0
```

## 数据模型

`StandardSpan` 字段 (JSON) 与 ClickHouse `iaoep.traces` 表字段一一对应,详见 [`../docker-compose/clickhouse/init.sql`](../docker-compose/clickhouse/init.sql)。
