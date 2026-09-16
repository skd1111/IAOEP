# Changelog

所有 IAOEP 的显著变更都记录在此文件中.

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/),
本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/).

## [Unreleased]

## [1.0.0] - 2027-09-01 🎉

### 🎉 首个 GA 版本

经过 11 个阶段迭代, IAOEP 达到 GA 状态: 完整闭环 + 生产就绪 + 国际化文档。

### ✨ 新增 (Added) — 11 个阶段全量汇总

**观测基础 (v0.1.0)**
- OTLP/HTTP + OTLP/gRPC 接入 (Go Ingest Gateway)
- 多租户 + API Key 鉴权 + 令牌桶限流
- ClickHouse Schema + Storage Worker (Java Kafka 批量写入)
- Web Dashboard (React 19 + Vite + Tailwind + shadcn/ui)
- 三语言 SDK: Java (Spring Boot Starter) / Python / TypeScript

**AI 评测引擎 (v0.2.0)**
- Golden Dataset 管理 (PostgreSQL JSONB)
- Rule Scorer 引擎 (5 个内置: Latency/Cost/Token/Keyword/JSON Schema)
- LLM-as-Judge Python sidecar (FastAPI + OpenAI 兼容协议)
- 评测任务调度 + 异步执行 + 进度跟踪
- 回归检测 (维度差异 + 阈值阻断)
- 通知集成 (企业微信 / 钉钉 / 飞书)

**自进化控制平面 (v0.3.0)**
- Analyst Agent (周期 AI 分析 + 风险分级)
- A/B 测试框架 (流量分流 + 自动评测对比 + 决策)
- 审批流 (三级风险: low 自动 / medium 单审 / high 双签 SOP + 7 天 SLA)
- 演进日志 (全量变更审计)
- Decision Agent (在线异常检测 + 自动回滚建议)
- Web UI 新页面: Approvals + Evolution

**Phase 4: SDK A/B Test 集成**
- Java `@ABTest` 注解 + AOP 拦截器
- Python `@ab_test` 装饰器
- TypeScript `abTest()` 高阶函数
- 三语言 SDK 一致 API

**Phase 5: 细粒度分配 + Header 透传**
- 按 `trace_id` 分配 (同一 trace 所有 span 一致)
- OTLP attribute 自动透传 `ab_test_name` / `ab_test_group`
- ClickHouse `iaoep.traces` 新增 A/B 字段 + 物化视图

**Phase 6: Service Mesh 集成 (零侵入)**
- Istio VirtualService (50/50 流量分流)
- DestinationRule (baseline / candidate subset)
- EnvoyFilter (Lua 脚本注入 `X-IAOEP-AB-Test-Group` header)
- 完整 demo (kind + Istio + agent-v1 + agent-v1.1)

**Phase 7: 联邦学习 + 差分隐私 (Laplace DP)**
- FederationService (跨租户聚合 + DP 噪声)
- DifferentialPrivacy 工具 (Laplace mechanism)
- 租户 ID SHA-256 hash (不暴露明文)
- REST API: `/aggregate` + `/aggregates` + `/matrix`

**Phase 7.5-7.8: Federation 增强**
- Federation Web UI (Owner 视角 + 噪声对比)
- Timeline 时间线图 (Recharts LineChart)
- 多维矩阵 (model × skill 交叉表)
- Cron 自动聚合 (Spring `@Scheduled`)
- 多维 API (`/aggregates/by-dimension` + `/matrix`)
- **ClickHouse 真实数据源** (替换模拟数据)
- **异常告警** (某 dimension noisy 突然飙升/暴跌)

**Phase 7.9: (ε,δ)-DP + Gaussian mechanism**
- `DifferentialPrivacy.addGaussianNoise()` (高精度场景)
- `(ε, δ)-DP` 保证, 比纯 ε-DP 更细粒度隐私控制

**Phase 7.10: 异常告警接 Notification**
- FederationScheduler.detectAnomalies() 通过 NotificationService 发钉钉/企微

### 🆕 新增服务 (v1.0.0)

- **ingest** (Go) — OTLP 接收 + Span 映射 + Kafka 生产
- **storage** (Java) — Kafka Consumer + ClickHouse 批量写入
- **evaluator-api** (Java) — Golden Dataset + Scorer + Judge client + Evaluation + Regression + Approval + Evolution + Federation + Notification
- **evaluator-python** (FastAPI) — LLM Judge sidecar + 多 LLM 投票
- **web** (React 19) — 10 个页面 (Dashboard / Traces / Datasets / Evaluations / Regression / Approvals / Evolution / Federation / Timeline)
- **sdk/java** — 注解 + AOP + Spring Boot Starter
- **sdk/python** — 装饰器 + LangChain patch
- **sdk/typescript** — 高阶函数 + LangChain.js patch
- **sdk/go** — OTel OTLP wrapper (基于现有 ingest 抽取)
- **examples/spring-ai-customer-service** — 端到端 demo (含 A/B Test)
- **examples/service-mesh** — Istio + EnvoyFilter A/B 路由 demo
- **helm/** — K8s Helm Chart (Bitnami Kafka + ClickHouse 依赖)

### 📊 量化指标 (从 v0.1.0 到 v1.0.0)

| 指标 | 数据 |
|---|---|
| 阶段数 | 11 (v0.1.0 → Phase 7.10) |
| 代码文件 | 150+ |
| 新增 Java 类 | 50+ |
| 新增 Python 模块 | 5 |
| 新增 TS/React 文件 | 30+ |
| 新增 SQL 表 | 8 |
| 新增 Flyway 迁移 | V4-V9 |
| 新增 REST 端点 | 40+ |
| 新增 Web 页面 | 10 |
| 文档 (Markdown) | 15+ |

### 🔒 安全 / 合规

- ✅ 多租户数据隔离 (Phase 1)
- ✅ ClickHouse 按 tenant_id 过滤
- ✅ API Key + 限流 (Phase 1)
- ✅ 跨租户联邦学习 + DP 噪声 (Phase 7)
- ✅ 演进日志审计 (Phase 3)
- ✅ 双签 SOP + 7 天 SLA (Phase 3)

### 📦 部署资源 (K8s + Helm)

| 服务 | CPU | Memory | 存储 |
|---|---|---|---|
| ingest | 1 | 512Mi | - |
| storage | 2 | 1Gi | - |
| evaluator-api | 2 | 1Gi | - |
| evaluator-python (judge) | 1 | 512Mi | - |
| web | 1 | 256Mi | - |
| api (Phase 2) | 1 | 512Mi | - |
| PostgreSQL | 4 | 4Gi | 20Gi |
| ClickHouse | 4 | 8Gi | 100Gi |
| Kafka | 2 | 4Gi | 50Gi |

---

## [0.3.0] - 2027-04-15

### ✨ 自进化控制平面 — Phase 3 核心

**Analyst Agent (PR 18)** / **A/B 测试框架 (PR 19)** / **审批流 (PR 20)** / **演进日志 + Decision Agent (PR 21)** / **v0.3.0 Release (PR 22)**

(完整变更见 [docs/phase-3-tasks.md](../docs/phase-3-tasks.md))

---

## [0.2.0] - 2027-01-15

### ✨ AI 评测引擎 — Phase 2 核心

**Golden Dataset 管理 (PR 12)** / **Rule Scorer 引擎 (PR 13)** / **LLM-as-Judge 服务 (PR 14)** / **评测任务调度 (PR 15)** / **回归检测 (PR 16)** / **评测 UI + 通知 (PR 17)**

---

## [0.1.0] - 2026-09-16

### ✨ 首个公开版本

Ingest Gateway (Go) + Storage Worker (Java) + 三语言 SDK + Web Dashboard + 多租户 + Helm Chart。
