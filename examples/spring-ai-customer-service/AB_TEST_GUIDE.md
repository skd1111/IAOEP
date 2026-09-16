# Demo Agent A/B Test 使用指南

本 Demo 集成 IAOEP SDK 的 A/B Test 功能,展示如何在真实 Agent 中分流 baseline / candidate 版本。

## 流程

```
1. 在 IAOEP Evaluator 创建 AB Test
2. Demo Agent 自动调 /assign 决定 group (baseline / candidate)
3. 根据 group 选择不同模型调用
4. 所有 trace 自动带 group 属性 (MDC + OTLP attribute)
5. 在 IAOEP Web Dashboard 按 group 过滤 / 对比
```

## 1. 创建 AB Test (via Evaluator API)

```bash
# 在项目里创建 A/B 测试
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{projectId}/ab-tests \
  -H "Content-Type: application/json" \
  -d '{
    "name": "customer-service-ab",
    "baselineVersion": "qwen3-turbo",
    "candidateVersion": "qwen3-max",
    "trafficSplit": {"baseline": 0.5, "candidate": 0.5},
    "suggestionId": null
  }'
```

返回 `abTestId`,Demo Agent 自动用它调 `/assign`。

## 2. 启动 Demo Agent

```bash
# 配置 evaluator 地址
export IAOEP_EVALUATOR_BASE_URL=http://iaoep-evaluator:8082

# 启动 (Demo Agent 会自动调 evaluator /assign)
docker compose up customer-service
```

## 3. 触发请求, 观察 group 分流

```bash
# 多次请求, 不同 user 会分到不同 group
for i in 1 2 3 4 5; do
  curl -X POST http://localhost:8080/chat \
    -H "Content-Type: application/json" \
    -d "{\"query\":\"你好\", \"userId\":\"user-$i\"}"
done
```

## 4. 查看 group 分流结果

### ClickHouse 查询 (按 group 聚合)

```sql
SELECT
    ab_test_group,
    count() as n_traces,
    avg(duration_ms) as avg_duration_ms,
    sum(llm_input_tokens + llm_output_tokens) / count() as avg_tokens
FROM iaoep.traces
WHERE agent_name = 'customer-service'
  AND start_time > now() - INTERVAL 1 HOUR
GROUP BY ab_test_group;
```

应看到:
```
baseline     247    185.3    180
candidate   253    612.7    180
```

(candidate 用 qwen3-max, 慢但更准; baseline 用 qwen3-turbo, 快但简单)

### Web Dashboard

`http://localhost:5173/traces` → 按 `group=baseline` / `group=candidate` 过滤。

## 5. 决策 — 哪个版本胜出

跑评测任务 (用 Golden Dataset),对比 baseline / candidate 评分:

```bash
# 跑 baseline 评测
curl -X POST /api/v1/iaoep/projects/{id}/evaluations \
  -d '{"agentVersion": "qwen3-turbo", "datasetId": "..."}'

# 跑 candidate 评测
curl -X POST /api/v1/iaoep/projects/{id}/evaluations \
  -d '{"agentVersion": "qwen3-max", "datasetId": "..."}'

# 对比 (Regression)
curl -X POST /api/v1/iaoep/projects/{id}/regression \
  -d '{"baselineJobId": "...", "candidateJobId": "..."}'
```

## 6. 应用决策

```bash
# candidate 胜出 → 应用
curl -X POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/apply

# candidate 劣化 → 回滚
curl -X POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/rollback
```

应用后, EvolutionLog 留痕 (合规审计), 演进历史 Web UI (`/evolution`) 可看。

## 关键 Span 属性

启用 A/B Test 后, 每个 trace 自动带这些属性:

| 属性 | 值 | 来源 |
|---|---|---|
| `ab_test_name` | "customer-service-ab" | @ABTest.name() |
| `ab_test_group` | "baseline" / "candidate" | evaluator /assign 响应 |
| `gen_ai.request.model` | "qwen3-turbo" / "qwen3-max" | 根据 group 选择 |

可在 ClickHouse 查询中按 group 聚合, 对比两个版本的延迟 / token / 错误率。

## 故障排查

| 问题 | 排查 |
|---|---|
| 始终走 baseline | 检查 evaluator /assign 端点是否可达, ab test name 是否正确 |
| group 在 trace 里看不到 | 检查 ABTestContext 是否被 @ABTest 装饰器正确设置 |
| 用户始终分到同一 group | 检查 ABTestClient 缓存 TTL (默认 30s) |
| evaluator 不可用 | SDK 兜底: 默认 baseline (不会抛错) |
