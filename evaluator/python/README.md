# IAOEP LLM-as-Judge Sidecar

FastAPI 服务,接收评测请求,调用 LLM 对 Agent 输出评分。默认使用千问 (qwen3-max) 通过 OpenAI 兼容协议。

## 启动

```bash
# 环境变量
export IAOEP_JUDGE_LLM_MODEL=qwen3-max
export IAOEP_JUDGE_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export IAOEP_JUDGE_LLM_API_KEY=sk-your-key

# 本地
pip install -e .
uvicorn app.main:app --host 0.0.0.0 --port 9000

# Docker
docker build -t iaoep/judge:0.2.0 .
docker run -p 9000:9000 \
  -e IAOEP_JUDGE_LLM_API_KEY=sk-xxx \
  iaoep/judge:0.2.0
```

## API

### POST /score

请求:
```json
{
  "trace": {
    "trace_id": "...",
    "spans": [
      {
        "span_name": "agent.run",
        "agent_input": "什么是 IAOEP?",
        "agent_output": "IAOEP 是一个开源的 Agent 可观测平台..."
      }
    ]
  },
  "rubric": "答案需要包含 IAOEP、可观测性、MIT 等关键词,简洁",
  "dimensions": ["accuracy", "style", "safety"],
  "config": {
    "llm_model": "qwen3-max",
    "llm_api_key": "sk-xxx",
    "timeout_seconds": 30,
    "voting_count": 1
  }
}
```

响应:
```json
{
  "overall": 0.85,
  "dimensions": [
    {"name": "accuracy", "score": 0.9, "reason": "答案准确, 覆盖核心要点"},
    {"name": "style",    "score": 0.8, "reason": "语言流畅, 但略冗长"},
    {"name": "safety",   "score": 1.0, "reason": "无敏感内容"}
  ],
  "raw_response": "vote[0]: 3 dimensions",
  "elapsed_ms": 1230
}
```

### GET /health

```json
{
  "status": "ok",
  "service": "iaoep-judge",
  "version": "0.2.0",
  "default_model": "qwen3-max"
}
```

## 设计要点

### Prompt 结构

```
System: 你是 LLM Judge, 严格返回 JSON
User:   ## rubric
        ## Agent 输入
        ## Agent 输出
        ## 评分维度
```

### 输出强制 JSON

通过 OpenAI 的 `response_format: {"type": "json_object"}` 强制 LLM 输出合法 JSON,避免解析失败。

### 多 LLM 投票

`voting_count` 参数可让同一请求调用多次 LLM, 取每个维度的平均分。提高稳定性, 抵消单次评分波动。

### 超时与重试

- 默认 30s 超时 (HTTPX)
- voting_count > 1 时, 第一次失败抛错, 后续失败只记日志
- 单次评分失败不阻断整体流程

## 切换 LLM 供应商

只换 `llm_base_url` 即可, 支持所有 OpenAI 兼容端点:

```bash
# 千问 (默认)
export IAOEP_JUDGE_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export IAOEP_JUDGE_LLM_MODEL=qwen3-max

# GLM-4
export IAOEP_JUDGE_LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v4
export IAOEP_JUDGE_LLM_MODEL=glm-4-plus

# DeepSeek
export IAOEP_JUDGE_LLM_BASE_URL=https://api.deepseek.com
export IAOEP_JUDGE_LLM_MODEL=deepseek-chat

# OpenAI (需用户自配)
export IAOEP_JUDGE_LLM_BASE_URL=https://api.openai.com/v1
export IAOEP_JUDGE_LLM_MODEL=gpt-4o
export IAOEP_JUDGE_LLM_API_KEY=sk-your-openai-key
```

## License

[MIT](../../LICENSE)
