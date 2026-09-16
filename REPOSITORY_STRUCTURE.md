# 仓库结构

## 当前结构 (Phase 1 准备中)

```
iaoep/
├── README.md                          # 仓库入口
├── LICENSE                            # MIT
├── CONTRIBUTING.md                    # 贡献指南
├── REPOSITORY_STRUCTURE.md            # 本文件
├── .gitignore
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── PULL_REQUEST_TEMPLATE.md
└── docker-compose.yml                 # 数据层最小启动 (ClickHouse + Kafka)
```

## Phase 1 完成后结构

```
iaoep/
├── ingest/                            # Go 服务 (OTLP 接入)
│   ├── cmd/
│   │   └── ingest/
│   │       └── main.go
│   ├── internal/
│   │   ├── otlp/                     # OTLP 接收 / 解析
│   │   ├── span/                     # Span 语义映射 (→ IAOEP 标准)
│   │   ├── kafka/                    # Kafka 生产者
│   │   ├── auth/                     # API Key 鉴权
│   │   └── ratelimit/                # 限流
│   ├── go.mod
│   ├── go.sum
│   └── Dockerfile
│
├── storage/                           # Java 服务 (Kafka → ClickHouse)
│   ├── pom.xml
│   ├── src/main/java/io/iaoep/storage/
│   │   ├── StorageApplication.java
│   │   ├── consumer/                  # Kafka Consumer
│   │   ├── sink/
│   │   │   ├── ClickhouseSink.java
│   │   │   ├── PrometheusSink.java
│   │   │   └── LokiSink.java
│   │   ├── schema/                    # ClickHouse Schema + 物化视图
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/              # Flyway / 自管 DDL
│   └── Dockerfile
│
├── evaluator/                         # Java + Python (评测引擎)
│   ├── java/                          # 主框架
│   │   ├── pom.xml
│   │   └── src/main/java/io/iaoep/evaluator/
│   └── python/                        # 评测脚本 (LLM 生态成熟)
│       ├── pyproject.toml
│       ├── iaoep_eval/
│       │   ├── judges/                # LLM-as-Judge 适配器
│       │   ├── scorers/               # 规则评分器
│       │   └── reporters/             # 报告生成
│       └── tests/
│
├── evolution/                         # Java 服务 (A/B + 审批)
│   ├── pom.xml
│   └── src/main/java/io/iaoep/evolution/
│       ├── EvolutionApplication.java
│       ├── analyst/                  # 离线分析 Agent
│       ├── ab/                       # A/B 测试引擎
│       ├── approval/                 # 审批流引擎
│       └── decision/                 # 在线决策
│
├── api/                               # Java 服务 (REST API)
│   ├── pom.xml
│   └── src/main/java/io/iaoep/api/
│       ├── ApiApplication.java
│       ├── controller/
│       ├── service/
│       └── repository/                # PostgreSQL
│
├── web/                               # React 19 + Vite + Tailwind + shadcn/ui
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── components/
│   │   │   ├── ui/                  # shadcn/ui 组件
│   │   │   ├── trace/               # trace 树
│   │   │   ├── dashboard/           # dashboard
│   │   │   └── approval/            # 审批控制台
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── lib/
│   │   └── types/
│   └── tailwind.config.js
│
├── helm/                              # K8s Helm Chart
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── ingest/
│       ├── storage/
│       ├── evaluator/
│       ├── evolution/
│       ├── api/
│       ├── web/
│       └── dependencies/             # ClickHouse / Kafka / PostgreSQL
│
├── docker-compose/                    # Docker Compose (开发 / 小规模)
│   ├── docker-compose.yml            # 全栈一键启动
│   ├── docker-compose.dev.yml        # 开发模式 (热重载)
│   └── docker-compose.prod.yml       # 生产模式
│
├── sdk/                               # OTLP SDK 多语言
│   ├── java/                          # Spring Boot Starter
│   │   ├── pom.xml
│   │   └── src/main/java/io/iaoep/sdk/
│   │       ├── annotation/           # @ObservedAgent
│   │       ├── aop/                  # AOP 拦截
│   │       └── autoconfigure/        # Spring Boot Auto-Configuration
│   ├── python/                        # Python OTLP SDK
│   │   ├── pyproject.toml
│   │   └── iaoep_sdk/
│   └── typescript/                    # TypeScript OTLP SDK
│       ├── package.json
│       └── src/
│
├── docs/                              # 设计文档
│   ├── IAOEP-README.md
│   ├── agentops-platform-design.md
│   ├── sdk-design.md                  # SDK 详细 API (Phase 1 写)
│   ├── phase-1-tasks.md              # 任务拆解 (本仓库根目录)
│   └── CHANGELOG.md
│
├── examples/                          # 示例 Agent
│   ├── spring-ai-customer-service/    # Spring AI + IAOEP SDK
│   ├── langchain-research-agent/      # Python LangChain
│   └── autogen-multi-agent/           # Microsoft AutoGen
│
├── docker-compose.yml                 # 当前 (Phase 1 准备): 只启动数据层
├── docker-compose.override.yml.example
├── CONTRIBUTING.md
├── LICENSE
├── README.md
└── REPOSITORY_STRUCTURE.md
```

## 子模块依赖关系

```
              ┌─────────────────────────────────┐
              │      External Agent              │
              │  (Spring AI / LangChain / ...)   │
              └──────────────┬──────────────────┘
                             │ OTLP
                             ▼
              ┌─────────────────────────────┐
              │       ingest/  (Go)         │
              └──────────────┬──────────────┘
                             │ Kafka
              ┌──────────────┴──────────────┐
              ▼                             ▼
    ┌──────────────────┐         ┌──────────────────┐
    │  storage/ (Java) │         │ evaluator/ (J+P) │
    └────────┬─────────┘         └────────┬─────────┘
             │                            │
             ▼                            ▼
    ┌──────────────────┐         ┌──────────────────┐
    │  evolution/ (J)  │◄────────│   api/ (Java)     │
    └────────┬─────────┘         └────────┬─────────┘
             │                            │
             └─────────────┬──────────────┘
                           ▼
                   ┌──────────────┐
                   │   web/ (React)│
                   └──────────────┘
```

## 构建依赖顺序

```
ingest ─→ storage ─→ api ─→ web
                 ↓
            evaluator ─→ evolution
                 ↓
                sdk ─→ examples
```

## 开发优先级

| 优先级 | 子模块 | 状态 |
|---|---|---|
| P0 | docker-compose (ClickHouse + Kafka) | ✅ Phase 1 PR 1 |
| P0 | ingest | ⏳ Phase 1 PR 2 |
| P0 | storage | ⏳ Phase 1 PR 3 |
| P0 | sdk/java | ⏳ Phase 1 PR 4 |
| P0 | web (最小) | ⏳ Phase 1 PR 5 |
| P0 | examples/spring-ai | ⏳ Phase 1 PR 6 |
| P1 | evaluator | Phase 2 |
| P1 | evolution | Phase 3 |
| P1 | sdk/python / sdk/typescript | Phase 1 (后续) |
| P2 | helm | Phase 1 (后续) |

---

如需修改结构,请在 GitHub Issue 发起讨论,标记 `area: structure`。
