# IAOEP Evaluator API (Phase 2 + Phase 3 早期)

评测 + 自进化 控制平面服务。

## 模块结构

```
evaluator/api/src/main/java/io/iaoep/evaluator/
├── EvaluatorApplication.java
├── dataset/                              (PR 12)
│   ├── GoldenDataset.java
│   ├── GoldenDatasetRepository.java
│   ├── GoldenDatasetService.java
│   ├── GoldenDatasetController.java
│   └── dto/{Create,Update,Dataset}{Request,Response}.java
├── scorer/                                (PR 13)
│   ├── Scorer.java + ScorerException.java
│   ├── ScorerRegistry.java + ScorerExecutor.java
│   └── builtin/
│       ├── LatencyScorer.java
│       ├── CostScorer.java
│       ├── TokenScorer.java
│       ├── KeywordScorer.java
│       └── JsonFormatScorer.java
├── judge/                                 (PR 14)
│   └── LLMJudgeClient.java
├── evaluation/                            (PR 15)
│   ├── EvaluationJob.java
│   ├── EvaluationJobRepository.java
│   ├── EvaluationService.java
│   ├── EvaluationController.java
│   ├── EvaluationExecutor.java
│   └── dto/{Create,EvaluationJob}{Request,Response}.java
├── regression/                            (PR 16)
│   ├── RegressionReport.java
│   ├── RegressionReportRepository.java
│   ├── RegressionService.java
│   ├── RegressionController.java
│   └── dto/{CreateRegression,RegressionReport}{Request,Response}.java
├── notification/                          (PR 17)
│   ├── Notifier.java + GenericWebhookNotifier.java
│   ├── WeChatWorkNotifier.java + DingTalkNotifier.java + FeishuNotifier.java
│   └── NotificationService.java
└── evolution/                             (Phase 3 PR 18)
    ├── EvolutionSuggestion.java
    ├── EvolutionSuggestionRepository.java
    ├── AnalystService.java
    ├── AnalystScheduler.java
    ├── EvolutionService.java
    ├── EvolutionController.java
    └── dto/EvolutionSuggestionResponse.java
```

## REST API

### Golden Dataset (PR 12)
```
GET    /api/v1/iaoep/projects/{id}/datasets
POST   /api/v1/iaoep/projects/{id}/datasets
GET    /api/v1/iaoep/projects/{id}/datasets/{dsId}
PUT    /api/v1/iaoep/projects/{id}/datasets/{dsId}
DELETE /api/v1/iaoep/projects/{id}/datasets/{dsId}
```

### Evaluation (PR 15)
```
GET    /api/v1/iaoep/projects/{id}/evaluations
POST   /api/v1/iaoep/projects/{id}/evaluations
GET    /api/v1/iaoep/projects/{id}/evaluations/{jobId}
DELETE /api/v1/iaoep/projects/{id}/evaluations/{jobId}    # 取消
```

### Regression (PR 16)
```
POST   /api/v1/iaoep/projects/{id}/regression
GET    /api/v1/iaoep/projects/{id}/regression
GET    /api/v1/iaoep/projects/{id}/regression/{regId}
```

### Evolution Suggestions (Phase 3 PR 18)
```
GET    /api/v1/iaoep/projects/{id}/suggestions[?status=PROPOSED]
GET    /api/v1/iaoep/projects/{id}/suggestions/{sid}
POST   /api/v1/iaoep/projects/{id}/suggestions/{sid}/approve
POST   /api/v1/iaoep/projects/{id}/suggestions/{sid}/reject
```

## 配置 (环境变量)

| 变量 | 默认 | 说明 |
|---|---|---|
| `IAOEP_EVALUATOR_DB_URL` | `jdbc:postgresql://localhost:5432/iaoep_evaluator` | PostgreSQL |
| `IAOEP_JUDGE_BASE_URL` | `http://iaoep-judge:9000` | Python Judge sidecar |
| `IAOEP_JUDGE_LLM_MODEL` | `qwen3-max` | LLM Judge 模型 |
| `IAOEP_JUDGE_LLM_API_KEY` | (空) | LLM API Key |
| `IAOEP_ANALYST_ENABLED` | `true` | 启用 Analyst Agent |
| `IAOEP_ANALYST_LOOKBACK` | `20` | 分析最近多少个 job |
| `IAOEP_EVALUATOR_NOTIFICATION_EVALUATION_WEBHOOK_URL` | (空) | 评测完成通知 webhook |

## 自进化 (Phase 3) 流程

```
Analyst (PR 18)
  │ 周期跑 (默认每天 02:00)
  ▼
EvolutionSuggestion 列表 (proposed)
  │ 风险分级 (low/medium/high)
  ▼
人工审批 (PR 20)
  │ low → 自动通过
  │ medium → 单审
  │ high → 双审 SOP
  ▼
A/B 测试 (PR 19) — low/medium 风险自动跑
  │ 流量 50/50 分流
  │ 自动跑评测 + 回归检测
  ▼
通过 → 应用 (PR 21 写 EvolutionLog)
  失败 → 关闭 / 回滚
```

## 本地启动

```bash
# 启动 PostgreSQL + Python Judge sidecar
docker compose up -d postgres iaoep-judge

# 启动 Java API
cd evaluator/api
mvn spring-boot:run
```

## License

[MIT](../../../LICENSE)
