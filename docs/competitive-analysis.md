# IAOEP 竞品分析报告

> 调研时间：2026-09-17 | 覆盖市场：Agent / LLM 可观测性 + 评测平台

---

## 一、2026 年市场格局

Agent 可观测性市场已形成四个清晰梯队：

| 梯队 | 代表产品 | 特点 |
|---|---|---|
| **AI 原生可观测平台** | Langfuse、LangSmith、Braintrust、Arize Phoenix、Opik | Trace + Eval 为核心，完整开发闭环 |
| **开源评估库** | DeepEval、RAGAS、TruLens | 只打分不追踪，适合已有监控的团队 |
| **AI 网关** | Helicone、Portkey、LiteLLM | 代理转发 + 日志，轻量但无深度 |
| **传统 APM 扩展** | Datadog LLM Obs、Signoz | 已有生态优势，LLM 是附加功能 |

---

## 二、头号竞品：Langfuse 深度剖析

### 基本信息

| 项目 | 数据 |
|---|---|
| GitHub Stars | 27,000+ |
| 协议 | MIT（`ee/` 目录企业功能除外） |
| 技术栈 | TypeScript/Next.js 全栈 + ClickHouse + PostgreSQL + Redis |
| 背景 | YC W23，德国柏林团队，**2026 年 1 月被 ClickHouse 收购**（D 轮 $4 亿同步） |
| SDK 月安装 | 2600 万次 |
| 自托管 | 支持（Docker Compose / K8s） |

### 定价

| 方案 | 价格 | 内容 |
|---|---|---|
| Hobby | 免费 | 5 万 units/月，30 天保留，2 用户 |
| Core | $29/月 | 10 万 units/月，90 天保留，无限用户 |
| Pro | $199/月 | 无限保留，SOC2/ISO27001 |
| Enterprise | $2,499/月 | 自定义用量，专属支持，审计日志 |
| 自托管 | 免费 | 完全控制，需自行维护 |

### 核心功能

- **分布式 Trace** —— LLM 调用、工具调用、检索、Agent 步骤的完整执行树
- **Prompt 管理** —— 版本控制、A/B 测试、缓存、团队协作
- **评估系统** —— LLM-as-Judge（MIT 开源）、人工标注、用户反馈、自定义 Pipeline
- **数据集与实验** —— 生产 Trace 转测试集、跨版本 Benchmark
- **成本追踪** —— 按 model / user / feature 维度聚合 cost、latency P50/P95/P99
- **OTel 兼容** —— 支持 OTLP 端点，任何 OTel SDK 可直接接入
- **100+ 框架集成** —— LangChain、Vercel AI、LiteLLM、CrewAI、Google ADK 等

### SDK 覆盖

| 语言 | 支持方式 |
|---|---|
| Python / TypeScript | **原生 SDK**（完整功能） |
| Go / Java / .NET / Ruby / PHP / Swift | 通过通用 OTel SDK 接入（仅 Trace，无 Prompt/Score 等高级功能） |

### Langfuse 的真实痛点（来自 GitHub Discussions / 用户反馈）

1. **Dataset Run 对比 UI 极差**
   > "impossible to use the UI to either generate or compare dataset runs or scores"
   > "the dataset run comparison is very weak... we're completely into custom python to do both of these tasks"

2. **非 Python/TS 语言体验断裂**
   - Java/Go 等语言只有通用 OTel 接入，缺少 prompt management、scoring 等 SDK 级支持
   - 无法编程方式获取 prompt 列表、无法获取 prompt 编译变量

3. **UI 浏览体验差**
   > "A pretty bad UI for browsing traces and scores"

4. **Prompt 管理鸡肋**
   > "UI-only prompt experiments thing feels like a gimmick; in reality most users need BAML or custom vendor structured data"

5. **自托管组件多、运维重**
   - 需要 PostgreSQL + ClickHouse + Redis + S3 四个组件
   - 数据迁移功能不完善，换服务器容易丢数据

6. **被收购后的不确定性**
   - 社区担忧：ClickHouse 是商业公司，收购后开源承诺能维持多久？

7. **没有控制平面能力**
   - 只做观测和评估，不做 A/B 测试、不做自动优化、不做审批流

---

## 三、其他竞品概况

### LangSmith（LangChain 官方）

| 项目 | 数据 |
|---|---|
| 定价 | $39/席/月 + trace 计费（20 万 traces ≈ $1,200/月） |
| 协议 | 闭源 SaaS（企业版可自托管） |
| 优势 | LangChain/LangGraph 深度集成、ADLC 闭环理念、逐节点状态差异与执行图回放 |
| 短板 | 闭源、不能自托管（需企业许可）、贵、深度绑定 LangChain |

### Arize Phoenix

| 项目 | 数据 |
|---|---|
| 定价 | 自托管免费 / Cloud 免费 10GiB / AX 企业版合同制 |
| 协议 | Elastic-2.0（商业友好，源码公开） |
| GitHub | 10,600+ Stars |
| 优势 | OTel 原生、UMAP 嵌入可视化（调试 RAG 召回质量）、17+ LLM 框架集成、单命令启动 |
| 短板 | 需自运维、偏调试不偏生产监控、业务报表需自建 |

### Braintrust

| 项目 | 数据 |
|---|---|
| 定价 | Starter 免费，Pro $249/月 |
| 融资 | 2026 年 B 轮 $8,000 万 |
| 优势 | 评测驱动开发、CI 回归测试、A/B test、自动 trace 打分 |
| 短板 | 学习曲线陡峭、商业版贵、不做网关 |

### Helicone

| 项目 | 数据 |
|---|---|
| 定价 | Team $799/月 |
| 优势 | 代理转发零侵入、成本追踪、缓存、Provider 路由 |
| 短板 | 只做 API 级日志，无 Agent 深度追踪 |

### 横向对比矩阵

| 维度 | Langfuse | LangSmith | Arize Phoenix | Braintrust | Helicone | **IAOEP** |
|---|---|---|---|---|---|---|
| 开源 | MIT | 闭源 | Elastic-2.0 | 部分开源 | Apache-2.0 | MIT |
| 自托管 | ✅ 免费 | ❌ 企业版 | ✅ 免费 | ❌ | ✅ | ✅ 免费 |
| OTLP 原生 | 兼容 | 部分 | **原生** | 部分 | ❌ | **唯一协议** |
| 多语言 SDK | Py/TS 原生 | Py/TS | Py/TS | Py/TS | 代理 | **Py/TS/Java/Go** |
| 评测 | ✅ | ✅ | ✅ | **核心** | ❌ | ✅ |
| A/B 测试 | ❌ | ❌ | ❌ | ✅ | ❌ | **✅** |
| 自进化 | ❌ | ❌ | ❌ | ❌ | ❌ | **✅** |
| 多租户 | 弱 | 弱 | 弱 | 弱 | 弱 | **✅** |
| 联邦学习 | ❌ | ❌ | ❌ | ❌ | ❌ | **✅** |
| 20 万 traces/月成本 | 免费(自托管) | ~$1,200 | 免费(自托管) | ~$600 | ~$799 | 免费(自托管) |
| Trace 写入延迟 | ~300ms | ~800ms | ~250ms | ~250ms | N/A | 目标 <200ms |

---

## 四、IAOEP 差异化机会

### 机会 1：OTLP-First 做到极致

Langfuse 的 OTLP 是"兼容"，核心仍依赖自有 SDK。IAOEP 从第一天 **只暴露 OTLP**：
- 真正的零 SDK 依赖：任何语言只要能发 OTLP 就能接入
- 与已有 OTel 基础设施（Grafana Tempo、Jaeger、Datadog）无缝共存
- 不被 IAOEP SDK 绑定——换掉 IAOEP，OTel 代码一行不用改

**落地要求：**
- Ingest Gateway 必须同时支持 OTLP/HTTP 和 OTLP/gRPC
- Span 语义映射必须严格对齐 OTel GenAI SemConv 1.0+
- 文档提供"无 SDK 直接发 OTLP"的完整示例

### 机会 2：观测 → 评测 → 自进化闭环

所有竞品只做"观测 + 评测"，没有一个做"自进化控制平面"：
- **Analyst Agent** —— 周期 AI 分析 + 风险分级
- **A/B 测试框架** —— 流量分流 + 自动评测对比 + 决策
- **审批流** —— 三级风险（low 自动 / medium 单审 / high 双签 SOP + 7 天 SLA）
- **Decision Agent** —— 在线异常检测 + 自动回滚建议

这是从 **"行车记录仪"** 到 **"自动驾驶辅助系统"** 的跨越。

### 机会 3：多租户 + 联邦学习 + 差分隐私

没有任何竞品提供跨租户联邦学习能力：
- 按 tenant 隔离数据
- 跨租户聚合分析（加 DP 噪声，不泄露明文）
- 满足 GDPR / 数据安全合规要求

**目标客户：** SaaS 平台型公司（一个平台上有多个客户的 Agent）

### 机会 4：Dataset Run 对比做到极致

这是 Langfuse 被吐槽最多的点，也是最快能出效果的差异化：
- 多维度对比（model × prompt × skill 交叉表）
- 可视化差异高亮
- 一键生成对比报告
- 支持自定义维度和评分器

---

## 五、竞争策略

```
                    功能深度
                      ↑
                      │
    Braintrust ●      │      ● IAOEP (目标位置)
    (评测驱动)        │      (观测+评测+自进化)
                      │
  LangSmith ●         │         ● Arize Phoenix
  (框架绑定)          │         (OTel 原生)
                      │
         Langfuse ●   │   ● Opik
         (全功能平台)  │   (轻量开源)
                      │
    ──────────────────┼──────────────────→ 接入开放性
         封闭         │         开放
```

| 策略 | 行动 | 理由 |
|---|---|---|
| **不打正面** | 不在 Trace UI 上和 Langfuse 拼功能完整度 | 它 27k Stars + ClickHouse 团队，拼不过 |
| **打差异化** | 主打 "观测 → 评测 → A/B → 自进化" 闭环 | 所有竞品的空白区 |
| **打接入** | 死磕 OTLP-only + 多语言 SDK 装饰器 | Langfuse 的 Java/Go SDK 是空的 |
| **打场景** | 瞄准企业级多租户 Agent 平台 | SaaS 公司需要多租户隔离 + 联邦学习 |
| **打成本** | 强调 ClickHouse + Kafka 高性能低成本 | 对比 LangSmith 20 万 traces = $1,200/月 |

---

## 六、Phase 1 聚焦建议

基于竞品分析，Phase 1 必须做到 **让 Langfuse 用户迁移过来时感到惊艳** 的最小功能集：

| 优先级 | 功能 | 对标痛点 |
|---|---|---|
| **P0** | Trace 采集 + 可视化（UI 必须比 Langfuse 好用） | Langfuse UI 被吐槽最多 |
| **P0** | 成本核算（tenant × provider × model 维度） | 基础功能，必须有 |
| **P0** | 多租户隔离 | 竞品普遍薄弱 |
| **P1** | Golden Dataset + 规则评分 + LLM-as-Judge | 和 Langfuse 功能对齐 |
| **P1** | Dataset Run 对比（比 Langfuse 做得好） | Langfuse 最大 UI 痛点 |
| **P2** | A/B 测试 + 审批流（自进化 MVP） | 差异化杀手锏 |

---

## 七、关键结论

1. **赛道正确** —— Agent 可观测性是刚需，市场在快速增长
2. **竞争激烈但有空白** —— 观测+评测已红海，但"自进化控制平面"无人触及
3. **OTLP-only 是正确赌注** —— Langfuse 也在向 OTel 靠拢，证明协议标准化是趋势
4. **不要做"中国版 Langfuse"** —— 必须有清晰的差异化叙事
5. **UI 是门面** —— Phase 1 的 Web Console 必须做到第一眼就让人觉得专业、好用
6. **社区是生死线** —— 技术再好没有社区等于闭门造车，需要英文文档 + 社区运营
