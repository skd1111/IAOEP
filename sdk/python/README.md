# IAOEP Python SDK

声明式 OTLP tracing for AI agents。基于 [OpenTelemetry Python SDK](https://opentelemetry.io/docs/languages/python/),提供装饰器 API + LangChain 自动 patch。

## 5 分钟接入

### 1. 安装

```bash
pip install iaoep-sdk

# 可选: LangChain 自动 trace
pip install 'iaoep-sdk[langchain]'
```

### 2. 配置环境变量

```bash
export IAOEP_EXPORTER_OTLP_ENDPOINT=http://iaoep-ingest:4318
export IAOEP_SERVICE_NAME=customer-service-agent
export IAOEP_TENANT_ID=acme-corp
export IAOEP_SAMPLING_RATIO=1.0
```

### 3. 标注 Agent 方法

```python
from iaoep_sdk import observed_agent, observed_llm_call, observed_tool

@observed_agent(name="customer-service", skill="general")
def chat(query: str) -> str:
    return call_llm(query)

@observed_llm_call(
    system="openai",
    model_param="#model",
    input_tokens_param="#result.usage.prompt_tokens",
    output_tokens_param="#result.usage.completion_tokens"
)
def call_llm(model: str, query: str):
    response = openai_client.chat(model=model, messages=[{"role": "user", "content": query}])
    return response

@observed_tool(name="get_current_time")
def get_time() -> str:
    from datetime import datetime
    return datetime.now().isoformat()
```

### 4. LangChain 一键启用 (可选)

```python
from iaoep_sdk import patch_langchain
patch_langchain()  # 全局 patch, 之后所有 LangChain 调用都自动 trace
```

## API

### 装饰器

| 装饰器 | Span 名称 | 关键属性 |
|---|---|---|
| `@observed_agent(name, skill="", capture_input=False, capture_output=False)` | `agent.run` | `agent.name`, `agent.skill.name` |
| `@observed_llm_call(system, model_param="", input_tokens_param="", output_tokens_param="")` | `llm.call` | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens` |
| `@observed_tool(name, capture_arguments=False)` | `tool.execute` | `tool.name`, `tool.call.id` |

### 参数路径 (SpEL-lite)

`model_param`, `input_tokens_param`, `output_tokens_param` 支持 `#.field.subfield` 路径:
- `#model` → 函数参数 `model`
- `#result.usage.prompt_tokens` → 返回值的 `usage.prompt_tokens` 属性
- `#result.tokens.input` → 返回值的 `tokens.input` 属性

### 函数式 API

```python
from iaoep_sdk import observed_agent, observed_llm_call

# 装饰器写法
@observed_agent(name="x")
def my_agent(query): ...

# 显式写法 (装饰器不适用时)
def my_agent(query):
    with tracer.start_as_current_span("agent.run") as span:
        span.set_attribute("agent.name", "x")
        ...
```

## 接入 LangChain

```python
from langchain_openai import ChatOpenAI
from langchain_core.tools import tool
from iaoep_sdk import patch_langchain

patch_langchain()  # 一行启用

llm = ChatOpenAI(model="qwen3-turbo")
result = llm.invoke("Hello")  # 自动产生 llm.call Span, 含 token 数

@tool
def get_time() -> str:
    """Get current time."""
    from datetime import datetime
    return datetime.now().isoformat()
# get_time.invoke(...) 也会自动 trace
```

## 调试

```bash
export IAOEP_LOG_LEVEL=DEBUG
```

或程序内:

```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

启用后,SDK 会打印每次 Span 创建和导出,方便排查。

## 性能

| 操作 | 开销 |
|---|---|
| `@observed_agent` | ~50-100μs |
| `@observed_llm_call` | ~100-200μs |
| `@observed_tool` | ~50μs |
| OTLP 导出 | 异步, 不影响主流程 |

## License

[MIT](../../LICENSE)
