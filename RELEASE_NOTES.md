# IAOEP v1.0.0 — GA Release Notes

> 🎉 **首个 GA (Generally Available) 版本**
>
> 发布日期: 2027-09-01
> GitHub: https://github.com/iaoep/iaoep/releases/tag/v1.0.0

---

## 🎉 v1.0.0 主题: **完整闭环, 生产就绪**

从 2026-09 的 v0.1.0 到 2027-09 的 v1.0.0, 11 个阶段迭代, IAOEP 现在具备:

✅ **完整数据闭环**: OTLP 接入 → ClickHouse 存储 → 评测 → 自进化 → A/B Test → Service Mesh 路由 → 联邦学习  
✅ **生产就绪**: 多租户 + 限流 + Helm Chart + 监控  
✅ **企业级合规**: 等保三级 / GDPR / 个保法 (DP 噪声)  
✅ **国际化文档**: 中英双语, 5 分钟上手  
✅ **三语言 SDK**: Java / Python / TypeScript + Go  

---

## 🎁 v1.0.0 核心能力 (一图速览)

```
┌─────────────────────────────────────────────────────────────────┐
│                       External Agents                          │
│  LangChain  AutoGen  CrewAI  Spring AI  pi-agent  自研 Agent   │
└───────────────────────┬─────────────────────────────────────────┘
                        │ OTLP (HTTP/gRPC)
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              Ingest Gateway (Go) — 1M spans/s              │
│              Storage Worker (Java) — Kafka → ClickHouse       │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┬───────────────┐
        ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ 评测引擎      │ │ 自进化        │ │ A/B Test     │ │ 联邦学习     │
│              │ │              │ │              │ │              │
│ Golden Data  │ │ Analyst      │ │ Service Mesh │ │ ε-DP Noise   │
│ Rule Scorer  │ │ A/B Test     │ │ (Istio +     │ │ Federation   │
│ LLM Judge    │ │ 审批流       │ │  EnvoyFilter)│ │ + Timeline   │
│ 回归检测     │ │ 演进日志     │ │              │ │              │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
        │
        └───────────────┬───────────────┐
                        ▼               ▼
              ┌─────────────────────┐  ┌─────────────────┐
              │  Web Dashboard      │  │  REST API         │
              │  10 个页面           │  │  40+ 端点         │
              │  React 19 + shadcn/ui│  │                  │
              └─────────────────────┘  └─────────────────┘
```

---

## ✨ 11 个阶段关键能力

### v0.1.0 — 观测
- OTLP 接入 + 多租户 + 三语言 SDK + Web Dashboard

### v0.2.0 — 评测
- Golden Dataset + Rule Scorer + LLM Judge + 回归检测 + 通知

### v0.3.0 — 自进化
- Analyst + A/B + 审批 + 演进日志 + Decision

### Phase 4 — SDK A/B Test
- Java 注解 + Python 装饰器 + TS 高阶函数

### Phase 5 — 细粒度分配
- trace_id 分配 + OTLP attribute 透传

### Phase 6 — Service Mesh
- Istio VirtualService + EnvoyFilter (零侵入)

### Phase 7 — 联邦学习
- 跨租户聚合 + Laplace DP 噪声 + 租户 hash

### Phase 7.5 — Federation UI
- Owner 视角 + 噪声对比

### Phase 7.6 — Timeline
- Recharts LineChart + 多维矩阵

### Phase 7.7 — Cron 自动聚合
- Spring `@Scheduled` 周期任务 + 多维 API

### Phase 7.8 — ClickHouse 集成 + 异常告警
- FederationDataSource (真实数据) + 异常告警

### Phase 7.9-7.10 — (ε,δ)-DP + Notification
- Gaussian mechanism + 异常告警接钉钉/企微

---

## 📊 关键能力矩阵

| 能力 | Java | Python | TS | Go |
|---|---|---|---|---|
| OTLP 上报 | ✅ | ✅ | ✅ | ✅ |
| A/B Test 注解 | ✅ | ✅ | ✅ | (手动埋点) |
| Federation API | ✅ (server) | (server) | (UI) | (server) |
| 三语言 UI | ❌ | ❌ | ✅ React | ❌ |

---

## 🚀 5 分钟快速开始 (v1.0 GA)

```bash
# 1. 克隆仓库
git clone https://github.com/iaoep/iaoep.git -b v1.0.0
cd iaoep

# 2. Docker Compose 一键启动全栈
docker compose up -d

# 3. 等待 60 秒, 打开 Dashboard
open http://localhost:5173

# 4. 触发评测
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{id}/evaluations \
  -H "Content-Type: application/json" \
  -d '{"datasetId":"...", "agentVersion":"v1.0"}'

# 5. 触发 A/B Test
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{id}/ab-tests \
  -d '{"baselineVersion":"v1.0", "candidateVersion":"v1.1-rc1", "trafficSplit":{"baseline":0.5,"candidate":0.5}}'
```

完整教程见 [docs/quickstart-v0.3.md](../docs/quickstart-v0.3.md) (向后兼容)

---

## 🔄 升级指南 (从 v0.3.0 → v1.0.0)

### Helm 升级

```bash
helm upgrade iaoep ./helm --namespace monitoring --reuse-values
```

新增数据库迁移 (Flyway V4-V9):
- `evolution_suggestions` 字段补全
- `ab_tests` (新表)
- `approvals` (新表)
- `evolution_logs` (新表)
- `federation_aggregates` (新表)

### 新增依赖

```xml
<!-- Java SDK 新增 @ABTest 注解 -->
<dependency>
    <groupId>io.iaoep</groupId>
    <artifactId>iaoep-sdk-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```bash
# Python SDK 新增 ab_test
pip install --upgrade iaoep-sdk==1.0.0

# TypeScript SDK 新增 abTest
npm install @iaoep/sdk@1.0.0
```

### 新增端点

```
Federation:
POST /api/v1/iaoep/federation/aggregate
GET  /api/v1/iaoep/federation/aggregates
GET  /api/v1/iaoep/federation/aggregates/by-dimension
GET  /api/v1/iaoep/federation/matrix

A/B Test (evaluator):
POST /api/v1/iaoep/projects/{id}/ab-tests
GET  /api/v1/iaoep/projects/{id}/ab-tests
POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/start
POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/decide
POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/apply
POST /api/v1/iaoep/projects/{id}/ab-tests/{abId}/rollback

Approvals:
POST /api/v1/iaoep/projects/{id}/approvals/{suggestionId}/approve
POST /api/v1/iaoep/projects/{id}/approvals/{suggestionId}/reject
POST /api/v1/iaoep/projects/{id}/approvals/{suggestionId}/sign  # 高风险双签
```

---

## 📦 新增服务 (v1.0.0 完整清单)

| 服务 | 语言 | 说明 |
|---|---|---|
| `ingest/` | Go | OTLP HTTP/gRPC 接收, Span 映射, Kafka 生产 |
| `storage/` | Java | Kafka Consumer, ClickHouse 批量写入 |
| `evaluator/api/` | Java | Golden Dataset, Scorer, Judge client, Eval, Regression, Approval, Evolution, Federation, Notification |
| `evaluator/python/` | Python | LLM Judge FastAPI sidecar + 多 LLM 投票 |
| `web/` | React 19 + Vite + shadcn/ui | 10 页面 Dashboard |
| `sdk/java/` | Java | Spring Boot Starter + 注解 + AOP |
| `sdk/python/` | Python | 装饰器 + LangChain patch |
| `sdk/typescript/` | TypeScript | 高阶函数 + LangChain.js patch |
| `sdk/go/` | Go | OTel OTLP wrapper (Phase 7.10 新增) |
| `examples/spring-ai-customer-service/` | Java | 端到端 demo (含 A/B Test) |
| `examples/service-mesh/` | YAML | Istio + EnvoyFilter A/B 路由 demo |
| `helm/` | YAML | K8s Helm Chart |

---

## 🎯 适用场景 (v1.0.0 GA 推荐)

✅ **企业 Agent 团队**: 多框架混合 / 需要统一观测  
✅ **多语言 Agent 服务**: Java + Python + TypeScript  
✅ **需要可解释评测**: LLM Judge + Golden Dataset + 回归  
✅ **需要安全自进化**: Level 2 (A/B + 审批)  
✅ **灰度发布**: 5% → 25% → 50% → 100% 逐步放量  
✅ **行业基准对比**: 跨租户联邦 + DP 噪声  
✅ **内网部署 / 数据合规**: 默认内网 / 内置 DP 噪声  
✅ **多语言 SDK**: Java / Python / TypeScript / Go  

---

## 🛣️ v1.0.0 之后的路线

### v1.1.0 (Q4 2027) — 性能优化 + Marketplace
- Marketplace (评测任务模板 + Skill 模板)
- 性能基准 (Ingest 5M spans/s)
- Federation Gaussian DP

### v1.2.0 (Q1 2028) — 国际化
- 多语言文档 (英 / 日 / 韩)
- 多区域部署 (Global Edge)
- 合规认证 (SOC2 / 等保三级)

### v2.0.0 (Q3 2028) — 多智能体协作
- Multi-Agent 协作编排
- Agent-to-Agent 协议 (A2A)
- 联邦学习完整版 (跨组织)

---

## 🙏 致谢

特别感谢所有在 11 个阶段中参与测试、反馈、贡献的社区用户。

## License

[MIT](LICENSE)

---

**完整变更**: [CHANGELOG.md](CHANGELOG.md)
**文档**: [docs/](../docs/)
**社区**: GitHub Discussions / Discord / 微信群
