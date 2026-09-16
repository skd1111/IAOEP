# IAOEP TypeScript SDK

声明式 OTLP tracing for AI agents (Node.js >= 18)。基于 [OpenTelemetry JS SDK](https://opentelemetry.io/docs/languages/js/),提供高阶函数 API + LangChain.js 自动 patch。

## 5 分钟接入

### 1. 安装

```bash
npm install @iaoep/sdk

# 可选: LangChain.js 自动 trace
npm install @langchain/core
```

### 2. 配置环境变量

```bash
export IAOEP_EXPORTER_OTLP_ENDPOINT=http://iaoep-ingest:4318
export IAOEP_SERVICE_NAME=customer-service-agent
export IAOEP_TENANT_ID=acme-corp
export IAOEP_SAMPLING_RATIO=1.0
```

或代码内:

```ts
import { configure } from '@iaoep/sdk';

configure({
  serviceName: 'customer-service-agent',
  endpoint: 'http://iaoep-ingest:4318',
  tenantId: 'acme-corp',
  samplingRatio: 1.0,
});
```

### 3. 包装 Agent 函数

```ts
import { observedAgent, observedLLMCall, observedTool } from '@iaoep/sdk';

const chat = observedAgent(
  { name: 'customer-service', skill: 'general' },
  async (query: string) => {
    return callLLM('qwen3-turbo', query);
  },
);

const callLLM = observedLLMCall(
  {
    system: 'openai',
    modelPath: 'modelName',
  },
  async (modelName: string, query: string) => {
    // 真实 LLM 调用
    return `mock response for ${query}`;
  },
);

const getTime = observedTool(
  { name: 'get_current_time' },
  async () => new Date().toISOString(),
);
```

### 4. LangChain.js 一键启用 (可选)

```ts
import { patchLangChainJS } from '@iaoep/sdk';
patchLangChainJS();

// 之后所有 LangChain 调用都自动 trace
const llm = new ChatOpenAI({ model: 'qwen3-turbo' });
const result = await llm.invoke('Hello');  // 自动产生 llm.call Span
```

## API

### 高阶函数

| 函数 | Span 名称 | 关键属性 |
|---|---|---|
| `observedAgent(opts, fn)` | `agent.run` | `agent.name`, `agent.skill.name` |
| `observedLLMCall(opts, fn)` | `llm.call` | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens` |
| `observedTool(opts, fn)` | `tool.execute` | `tool.name`, `tool.call.id` |

### 参数路径 (类似 SpEL-lite)

`modelPath`, `inputTokensPath` 等支持 `.` 分隔的属性访问:

```ts
observedLLMCall(
  {
    system: 'openai',
    modelPath: 'options.model',  // 从 args[0].options.model 取
    inputTokensResultPath: 'usage.prompt_tokens',  // 从返回值 usage.prompt_tokens 取
  },
  llmFunc,
);
```

### 类型定义

```ts
interface ObservedAgentOptions {
  name: string;
  skill?: string;
  captureInput?: boolean;
  captureOutput?: boolean;
  maxPayloadLength?: number;
}

interface ObservedLLMOptions {
  system: string;
  modelPath?: string;
  inputTokensPath?: string;
  outputTokensPath?: string;
  inputTokensResultPath?: string;
  outputTokensResultPath?: string;
}

interface ObservedToolOptions {
  name: string;
  captureArguments?: boolean;
}
```

## 调试

```bash
export DEBUG=opentelemetry*
```

或代码内:

```ts
import { diag, DiagConsoleLogger, DiagLogLevel } from '@opentelemetry/api';
diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.DEBUG);
```

## 性能

| 操作 | 开销 |
|---|---|
| `observedAgent` | ~50-100μs |
| `observedLLMCall` | ~100-200μs |
| `observedTool` | ~50μs |
| OTLP 导出 | 异步,不影响主流程 |

## License

[MIT](../../LICENSE)
