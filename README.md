# IAOEP

> **Intelligent Agent Observation and Evaluation Platform**
>
> 框架无关的 Agent 可观测性 + AI 评测 + 自进化平台

IAOEP 对外只暴露 **OpenTelemetry (OTLP)** 一种协议。任何语言、任何 Agent 框架,只要能发出 OTLP,就能 5 分钟接入 —— 不需要改业务代码,不需要绑死任何 SDK。

---

## 它解决什么问题

- **Trace 统一收集** —— LLM 调用、工具调用、Skill 命中、记忆读写、Agent 循环,全部走 OTLP Span
- **成本核算** —— 按 `tenant × provider × model` 维度统计 token 消耗与 CNY 成本
- **质量评测** —— Golden Dataset + 规则评分器 + LLM-as-Judge + 回归检测
- **多租户** —— 每个接入方按 `tenant_id` 隔离,统一查询与权限边界

## 核心设计:为什么是 OTLP

| 设计选择 | 理由 |
|---|---|
| **只暴露 OTLP,不造协议** | OTel GenAI SemConv 1.0+ 已经是行业事实标准,任何语言都有官方 SDK |
| **Ingest Gateway 是无状态 Go 服务** | 横向扩缩容简单,只负责协议解析 + Kafka 转发 |
| **存储与计算解耦** | Kafka 缓冲 + ClickHouse 列存,trace 与 metric 走不同 sink |
| **SDK 是"装饰器",不是 Agent 框架** | 业务代码可以零侵入,SDK 只负责把方法调用包成 Span |

---

## 架构

```mermaid
flowchart TB
    subgraph EXT["外部 Agent 服务 (任意语言/框架)"]
        A1["Spring AI<br/>(Java 17+)"]
        A2["Python<br/>(LangChain / AutoGen)"]
        A3["Node.js / TS<br/>(LangChain.js / Vercel AI)"]
        A4["Go 1.22+"]
        A5["其他自研 Agent<br/>(Rust / C# / PHP / ...)"]
    end

    subgraph SDK["IAOEP SDK (可选装饰层)"]
        S1["spring-boot-starter<br/>+ @ObservedAgent / @ObservedLLMCall / @ObservedTool"]
        S2["iaoep-sdk<br/>+ @observed_agent / @observed_llm_call / @observed_tool"]
        S3["@iaoep/sdk<br/>+ observedAgent() / observedLLMCall() / observedTool()"]
        S4["sdk-go<br/>+ iaoep.ObservedAgent(ctx, ...)"]
        S5["无 SDK<br/>直接发 OTLP protobuf"]
    end

    subgraph PROTO["OTLP 协议层 (唯一对外接口)"]
        OTLP["OTLP/HTTP :4318<br/>OTLP/gRPC :4317<br/>对齐 OTel GenAI SemConv 1.0+"]
    end

    subgraph INGEST["Ingest Gateway (Go · 无状态)"]
        I1["OTLP 解析"]
        I2["Span 语义映射<br/>→ IAOEP 标准字段"]
        I3["鉴权 + 限流"]
    end

    subgraph BUS["消息总线"]
        K[("Kafka topic<br/>iaoep.traces")]
    end

    subgraph STORE["存储层"]
        CH[("ClickHouse<br/>traces · metrics · logs<br/>+ 物化视图 traces_hourly")]
        PR[("Prometheus")]
        LK[("Loki")]
    end

    subgraph COMP["计算层"]
        API["api (Java)<br/>REST 查询"]
        EV["evaluator (Java + Python)<br/>Golden Dataset · Rule Scorer<br/>LLM-as-Judge · 回归检测"]
        EVE["evolution (Java)<br/>A/B · 审批流 · 自进化"]
    end

    WEB["Web Console<br/>React 19 + Vite + shadcn/ui"]

    A1 --> S1
    A2 --> S2
    A3 --> S3
    A4 --> S4
    A5 -.直接发.-> S5
    S1 & S2 & S3 & S4 & S5 --> OTLP
    OTLP --> I1 --> I2 --> I3 --> K
    K --> CH & PR & LK
    CH --> API & EV & EVE
    PR & LK --> API
    API & EV & EVE --> WEB
```

**关键边界:**
- **OTLP 线之上** 全部属于接入方 —— 业务服务想怎么写就怎么写
- **OTLP 线之下** 全部属于 IAOEP —— 协议解析、路由、存储、查询、UI
- **Ingest Gateway 没有任何业务状态** —— 想扩几实例就扩几实例,Kafka 当 buffer
- **存储与计算解耦** —— ClickHouse 存明细,物化视图 `traces_hourly` 做小时聚合

### 各组件角色一览

| 组件 | 技术栈 | 状态 | 职责 |
|---|---|---|---|
| Ingest Gateway | Go 1.22 | Phase 1 | OTLP 解析 → Span 标准化 → Kafka 转发 |
| Storage | Java (Spring Boot) | Phase 1 | Kafka Consumer → ClickHouse / Prometheus / Loki 多 sink |
| API | Java (Spring Boot) | Phase 1 | REST 查询入口(PostgreSQL 存元数据) |
| Web Console | React 19 + Vite | Phase 1 | 链路 / 仪表盘 / 评测 / 审批 UI |
| Evaluator | Java + Python | Phase 2 | Golden Dataset / 规则 / LLM-as-Judge / 回归 |
| Evolution | Java | Phase 3 | A/B 测试 / 审批流 / 自进化决策 |
| SDK (4 语言) | Java/Python/TS/Go | Phase 1 | 声明式 OTLP tracing,可选 |

---

## 5 分钟接入(最常见场景)

### 场景 A:你的服务是 Spring Boot (Java 17+)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.iaoep</groupId>
    <artifactId>iaoep-sdk-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
# application.yml
iaoep:
  sdk:
    enabled: true
    otlp-endpoint: http://iaoep-ingest:4318
    service-name: my-agent
    tenant-id: acme-corp
    sampling-ratio: 1.0
```

```java
import io.iaoep.sdk.annotation.*;

@Service
public class MyAgent {

    @ObservedAgent(name = "my-agent", skill = "general")
    public String chat(String query) {
        return callLlm(query);
    }

    @ObservedLLMCall(system = "dashscope", modelParam = "#model",
                      inputTokensParam = "#in", outputTokensParam = "#out")
    public String callLlm(String model, String query, Integer in, Integer out) {
        // 你原来的 LLM 调用
        return "response";
    }

    @ObservedTool(name = "get_time")
    public String now() {
        return java.time.LocalDateTime.now().toString();
    }
}
```

启动后,所有带注解的方法会自动产生 OTLP Span,异步批量发到 Ingest Gateway。

### 场景 B:你的服务是 Python

```bash
pip install iaoep-sdk
# 可选: LangChain 自动 patch
pip install 'iaoep-sdk[langchain]'
```

```bash
export IAOEP_EXPORTER_OTLP_ENDPOINT=http://iaoep-ingest:4318
export IAOEP_SERVICE_NAME=my-agent
export IAOEP_TENANT_ID=acme-corp
```

```python
from iaoep_sdk import observed_agent, observed_llm_call, observed_tool

@observed_agent(name="my-agent", skill="general")
def chat(query: str) -> str:
    return call_llm(query)

@observed_llm_call(
    system="openai",
    model_param="#model",
    input_tokens_param="#result.usage.prompt_tokens",
    output_tokens_param="#result.usage.completion_tokens",
)
def call_llm(model: str, query: str):
    # 你原来的 LLM 调用
    return response

@observed_tool(name="get_time")
def now() -> str:
    from datetime import datetime
    return datetime.now().isoformat()
```

### 场景 C:LangChain (Python 或 JS)

```python
# Python
from iaoep_sdk import patch_langchain
patch_langchain()  # 一行启用,所有 LangChain 调用自动 trace

from langchain_openai import ChatOpenAI
llm = ChatOpenAI(model="qwen3-turbo")
llm.invoke("Hello")  # 自动产生 llm.call Span,含 token 数
```

```ts
// TypeScript
import { configure, patchLangChain } from '@iaoep/sdk';
configure({ serviceName: 'my-agent', endpoint: 'http://iaoep-ingest:4318', tenantId: 'acme-corp' });
patchLangChain();  // 一行启用
```

---

## 端到端联调(从 0 到看见第一条 trace)

按下面顺序执行,大约 10 分钟能跑通整条链路。本节命令假设你在仓库根目录 `iaoep/` 下。

### 0. 前置依赖

| 工具 | 用途 | 验证命令 |
|---|---|---|
| Docker 24+ / Compose v2 | 跑 ClickHouse + Kafka + Prometheus | `docker --version` |
| Go 1.22+ | 跑 Ingest Gateway | `go version` |
| Java 17+ (可选) | 跑 Storage / API 服务 | `java -version` |
| Node 18+ (可选) | 跑 Web Console | `node -v` |
| curl / kafkacat (kcat) | 联调测试 | `curl --version` |

确认以下端口没被占用:`8123` (ClickHouse HTTP) / `9000` (ClickHouse Native) / `9092` `29092` (Kafka) / `4317` `4318` (OTLP) / `9090` (Prometheus) / `3000` (Grafana) / `4317` `4318` (OTLP) / `8080` (api) / `5173` (Web dev)。

### 1. 启动数据层 (ClickHouse + Kafka + Zookeeper + Prometheus + Grafana)

```bash
docker compose up -d clickhouse zookeeper kafka prometheus grafana
```

观察日志,等所有容器 healthy:

```bash
docker compose ps
# 期望:
# iaoep-clickhouse   ... (healthy)
# iaoep-zookeeper    ... (healthy)
# iaoep-kafka        ... (healthy)
# iaoep-prometheus   ... Up
# iaoep-grafana      ... Up
```

**首次启动** ClickHouse 会自动执行 `docker-compose/clickhouse/init.sql`,创建 `iaoep.traces` / `iaoep.metrics` / `iaoep.logs` 三张表 + `iaoep.traces_hourly` 物化视图。

### 2. 验证数据层

```bash
# 2.1 ClickHouse 联通
curl -s "http://iaoep:iaoep_dev_pwd@localhost:8123/?query=SELECT+version()"
# 期望: 24.3.x.x

# 2.2 确认表已建
curl -s "http://iaoep:iaoep_dev_pwd@localhost:8123/?query=SHOW+TABLES+FROM+iaoep"
# 期望: traces  metrics  logs  traces_hourly

# 2.3 Kafka 联通
docker exec -it iaoep-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list
# 期望(空列表也行,auto-create 已开): 能进入交互即正常
```

### 3. 启动 Ingest Gateway

```bash
cd ingest
go mod download
go run ./cmd/ingest
# 期望输出:
#   {"level":"info","msg":"OTLP/HTTP listening","port":4318}
#   {"level":"info","msg":"OTLP/gRPC listening","port":4317}
#   {"level":"info","msg":"Kafka producer ready","topic":"iaoep.traces"}
```

另开一个终端,验证 Ingest 健康:

```bash
curl -s http://localhost:4318/health
# 期望: {"status":"ok","spans_received":0,"spans_dropped":0,...}
```

### 4. 发送第一条测试 trace(直接发 OTLP,不依赖 SDK)

仓库里 `ingest/testdata/` 目录有一份样例 payload,直接 curl:

```bash
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data-binary @ingest/testdata/sample-trace.json
```

**期望返回**: HTTP 200,空 body。Ingest Gateway 会把它转成 Kafka 消息。

### 5. 验证 Ingest 收到了

```bash
# 5.1 看 Ingest 自己的健康统计
curl -s http://localhost:4318/health
# spans_received 应该 = 1

# 5.2 看 Kafka topic 里有消息
docker exec -it iaoep-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic iaoep.traces \
  --from-beginning --max-messages 1 \
  --timeout-ms 5000
# 期望: 看到一段 protobuf JSON 格式的 trace
```

### 6. 用 Python SDK 跑一个真实 Agent(推荐)

```bash
mkdir -p /tmp/my-agent && cd /tmp/my-agent

pip install iaoep-sdk

export IAOEP_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export IAOEP_SERVICE_NAME=my-agent
export IAOEP_TENANT_ID=acme-corp

cat > agent.py <<'PY'
from iaoep_sdk import observed_agent, observed_llm_call, observed_tool

@observed_agent(name="my-agent", skill="general")
def chat(query: str) -> str:
    return call_llm("qwen3-turbo", query)

@observed_llm_call(
    system="dashscope",
    model_param="#model",
    input_tokens_param="#result.usage.prompt_tokens",
    output_tokens_param="#result.usage.completion_tokens",
)
def call_llm(model: str, query: str):
    # 这里假装调用 LLM(没真 key 也行,只要 Span 出去)
    class R: usage = type("U", (), {"prompt_tokens": 12, "completion_tokens": 8})()
    return R()

@observed_tool(name="get_time")
def now() -> str:
    from datetime import datetime
    return datetime.now().isoformat()

if __name__ == "__main__":
    print(chat("hello"))
    print(now())
PY

python agent.py
```

跑完会看到 3 个 Span:`agent.run` → `llm.call` + `tool.execute`(父子结构)。

### 7. 验证 Ingest 收到了 Python Agent 的 trace

```bash
curl -s http://localhost:4318/health
# spans_received 应该 ≥ 3
```

### 8. 在 ClickHouse 里直接查(确认数据落库)

**注意:** storage 服务还没起,所以这一步查不到 —— 数据在 Kafka 里。需要先起 storage,见下一步。如果想看 Kafka 里的中间状态,回到步骤 5.2。

### 9. (可选)启动 Storage 把 Kafka 数据落 ClickHouse

```bash
cd storage
./mvnw spring-boot:run
# 或 mvn spring-boot:run
```

启动后会自动消费 `iaoep.traces` topic 写入 `iaoep.traces` 表。等几秒后:

```bash
# 查最近 10 条 trace
curl -s "http://iaoep:iaoep_dev_pwd@localhost:8123/" \
  --data-urlencode "query=SELECT tenant_id, span_name, agent_name, llm_model, llm_input_tokens, llm_output_tokens, duration_ms, status FROM iaoep.traces ORDER BY start_time DESC LIMIT 10 FORMAT PrettyCompact"
```

**期望看到**:你刚跑的 `my-agent` 的 3 条 Span(agent.run / llm.call / tool.execute),token 数和 duration_ms 都对得上。

### 10. (可选)启动 Web Console 看可视化

```bash
cd web
npm install
npm run dev
# 默认 http://localhost:5173
```

进入后:
- **链路页** (`/traces`) —— 看到刚才 `my-agent` 的 trace 列表
- **详情页** —— 点开看 Span 树、token、成本
- **仪表盘** (`/dashboard`) —— 按 `tenant_id × agent_name` 聚合的指标
- **数据集** (`/datasets`) / **评测** (`/evaluations`) —— Phase 2 起会有内容

### 11. 关闭整个环境

```bash
# 停数据层(保留卷)
docker compose down

# 完全清理(连数据一起删)
docker compose down -v
```

---

### 联调常见问题

<details>
<summary><b>Q1: ClickHouse 启动后 SHOW TABLES 是空的?</b></summary>

`init.sql` 没执行。两种可能:
1. 数据卷已存在但 schema 未建 → `docker compose down -v` 后重新 `up`
2. 镜像版本太老 → 确认用 `clickhouse/clickhouse-server:24.3`
</details>

<details>
<summary><b>Q2: Ingest 报 "kafka: client has run out of available brokers"?</b></summary>

Kafka 还没就绪。`depends_on: condition: service_healthy` 应该挡住了,但如果手动重启顺序乱了,等 30s 重试,或重启 Ingest。
</details>

<details>
<summary><b>Q3: curl 4318 返回 415 Unsupported Media Type?</b></summary>

OTLP/HTTP 要求 `Content-Type: application/json`(protobuf JSON 编码)。如果用了 `application/x-protobuf`,得切到 gRPC 端口 4317。
</details>

<details>
<summary><b>Q4: Python SDK 启动后 Ingest 的 spans_received 没涨?</b></summary>

排查顺序:
1. `echo $IAOEP_EXPORTER_OTLP_ENDPOINT` —— 确认环境变量
2. `curl http://localhost:4318/health` —— 确认 Ingest 活着
3. `export IAOEP_LOG_LEVEL=DEBUG` 再跑,会看到每次 Span 的导出日志
4. 检查代理/防火墙:有些公司内网会拦 4318 端口
</details>

<details>
<summary><b>Q5: ClickHouse 查不到数据但 Kafka 有?</b></summary>

Storage 服务没起 / 起了但 consumer group 没 commit。重启 storage,等 10s,再查。
</details>

<details>
<summary><b>Q6: 端口被占用?</b></summary>

`docker compose down` 关掉旧容器,或修改 `docker-compose.yml` 里 `ports:` 段(改成宿主机空闲端口)。
</details>

---

## 接入路径决策

| 你的环境 | 推荐方案 | 备注 |
|---|---|---|
| **Spring Boot / Java** | `iaoep-sdk-spring-boot-starter` + 注解 | AOP 拦截,零侵入 |
| **Python** | `iaoep-sdk` + 装饰器 / 函数式 API | 跟 LangChain / LlamaIndex 完美兼容 |
| **TypeScript / Node.js** | `@iaoep/sdk` + 高阶函数 | 兼容 LangChain.js / Vercel AI SDK |
| **Go** | `iaoep/sdk-go` + 显式 API | 1.22+,context-first 设计 |
| **LangChain (Py/JS)** | `patch_langchain()` / `patchLangChain()` | 一行启用,所有 chain 自动 trace |
| **AutoGen / CrewAI** | 复用 Python SDK + 装饰器 Agent 入口 | 已规划示例,见 [examples](examples/) |
| **Dify / Coze / FastGPT** | 中间加一层 OTLP adapter | 把它们自带的 OpenAPI 事件转 OTLP |
| **自研 Agent,任意语言** | **直接发 OTLP** | 见下方"方案 5:直接 OTLP" |
| **没有埋点能力** | service-mesh 模式 | 用 Istio / Envoy 抓出口流量,见 [examples/service-mesh](examples/service-mesh) |

---

## 方案详解

### 方案 1: Spring Boot SDK (Java)

详见 [sdk/java/README.md](sdk/java/README.md)。

| 注解 | Span 名 | 关键属性 |
|---|---|---|
| `@ObservedAgent(name, skill="", captureInput=false)` | `agent.run` | `agent.name`, `agent.skill.name` |
| `@ObservedLLMCall(system, modelParam="#x", ...)` | `llm.call` | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.*` |
| `@ObservedTool(name, captureArguments=false)` | `tool.execute` | `tool.name`, `tool.call.id` |

**性能**:`@ObservedAgent` ~50–100μs,`@ObservedLLMCall` ~100–200μs,OTLP 导出完全异步。

### 方案 2: Python SDK

详见 [sdk/python/README.md](sdk/python/README.md)。

```python
# 函数式 API(装饰器不适用时)
from iaoep_sdk import tracer

def my_agent(query):
    with tracer.start_as_current_span("agent.run") as span:
        span.set_attribute("agent.name", "x")
        # 业务逻辑
```

### 方案 3: TypeScript SDK

详见 [sdk/typescript/README.md](sdk/typescript/README.md)。要求 Node.js >= 18。

```ts
import { observedAgent, observedLLMCall, observedTool } from '@iaoep/sdk';

const chat = observedAgent(
  { name: 'my-agent', skill: 'general' },
  async (query: string) => callLLM('qwen3-turbo', query),
);

const callLLM = observedLLMCall(
  { system: 'dashscope', modelPath: 'modelName' },
  async (modelName: string, query: string) => `response for ${query}`,
);

const now = observedTool({ name: 'get_time' }, () => new Date().toISOString());
```

### 方案 4: Go SDK

详见 [sdk/go/README.md](sdk/go/README.md)。要求 Go 1.22+。

```go
import "github.com/iaoep/sdk-go"

func Chat(ctx context.Context, query string) (string, error) {
    return iaoep.ObservedAgent(ctx, "my-agent", "general",
        func(ctx context.Context) (string, error) {
            return callLLM(ctx, query)
        })
}

func callLLM(ctx context.Context, query string) (string, error) {
    return iaoep.ObservedLLMCall(ctx, "openai", "qwen3-turbo", 120, 80,
        func(ctx context.Context) (string, error) {
            return "response", nil
        })
}
```

### 方案 5: 直接发 OTLP(任意语言)

**不依赖任何 IAOEP SDK**,只要能发 HTTP POST 就行。OpenTelemetry 官方 SDK 在 [Rust / C++ / C# / PHP / Ruby / Swift](https://opentelemetry.io/docs/languages/) 都已支持,直接配 OTLP exporter 即可。

最小 HTTP 示例(发到 OTLP/HTTP 端口 4318):

```bash
curl -X POST http://iaoep-ingest:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data-binary @my-trace.json
```

`my-trace.json` 是 OTLP protobuf JSON 格式,结构见 [OpenTelemetry Trace JSON Encoding](https://opentelemetry.io/docs/specs/otlp/#json-protobuf-encoding)。

**如果用官方 OTel SDK,需要按下面的属性约定**给 Span 打 attribute,否则 storage 层无法正确归类。

---

## Span 属性约定(对齐 OTel GenAI SemConv 1.0+)

| 属性 | 适用 Span | 必填 | 说明 |
|---|---|---|---|
| `agent.name` | `agent.run` | ✅ | Agent 标识,用于 trace 聚合 |
| `session.id` / `gen_ai.conversation.id` | `agent.run` | ✅ | 会话 ID |
| `user.id` | `agent.run` | ❌ | 用户 ID(多租户场景区分) |
| `agent.skill.name` | `agent.run` | ❌ | 命中的 Skill 名 |
| `agent.turns.total` | `agent.run` | ❌ | AgentLoop 循环轮数 |
| `gen_ai.system` | `llm.call` | ✅ | `openai` / `dashscope` / `glm` / `deepseek` / `minimax` |
| `gen_ai.request.model` | `llm.call` | ✅ | 模型名 |
| `gen_ai.usage.input_tokens` | `llm.call` | ✅ | 输入 token |
| `gen_ai.usage.output_tokens` | `llm.call` | ✅ | 输出 token |
| `gen_ai.cost.cny` | `llm.call` | ❌ | 成本(CNY) |
| `tool.name` | `tool.execute` | ✅ | 工具名 |
| `tool.call.id` | `tool.execute` | ❌ | 工具调用 ID,用于关联 |
| `tool.error.type` | `tool.execute` | ❌ | 错误类型(`timeout` / `rate_limit` / ...) |

**不推荐** 自创 attribute 名 —— 跟 OTel GenAI SemConv 对齐后,将来可以直接接 Grafana / Datadog / NewRelic 等其他后端。

---

## 配置参考

### 环境变量(所有 SDK 通用)

| 变量 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `IAOEP_EXPORTER_OTLP_ENDPOINT` | ✅ | - | Ingest Gateway 地址,例 `http://iaoep-ingest:4318` |
| `IAOEP_SERVICE_NAME` | ✅ | - | 你的服务名,作为 `service.name` resource attribute |
| `IAOEP_TENANT_ID` | ❌ | `default` | 租户 ID,用于多租户隔离 |
| `IAOEP_SAMPLING_RATIO` | ❌ | `1.0` | 采样率,0–1 |
| `IAOEP_LOG_LEVEL` | ❌ | `INFO` | SDK 调试日志级别 |

### Ingest Gateway 配置

详见 [ingest/README.md](ingest/README.md)。

| 变量 | 默认 | 说明 |
|---|---|---|
| `IAOEP_INGEST_HTTP_PORT` | 4318 | OTLP/HTTP 端口 |
| `IAOEP_INGEST_GRPC_PORT` | 4317 | OTLP/gRPC 端口 |
| `IAOEP_INGEST_KAFKA_BROKERS` | `localhost:29092` | Kafka brokers |
| `IAOEP_INGEST_KAFKA_TOPIC` | `iaoep.traces` | Kafka topic |
| `IAOEP_INGEST_TENANT_ID` | `default` | 默认 tenant id |

---

## 示例

[examples/](examples/) 目录提供了多个真实接入案例:

| 案例 | 框架 | 接入方式 |
|---|---|---|
| [examples/spring-ai-customer-service](examples/spring-ai-customer-service) | Spring AI (Java) | Spring Boot Starter + 注解 |
| [examples/service-mesh](examples/service-mesh) | 任意(无埋点) | Istio/Envoy 出口流量镜像 → Ingest |
| [examples/langchain-research-agent](examples/langchain-research-agent) | LangChain (Python) | Python SDK + `patch_langchain()` |
| [examples/autogen-multi-agent](examples/autogen-multi-agent) | Microsoft AutoGen | Python SDK + 装饰器(规划中) |

---

## 当前状态

**Phase 1 准备中** (Q4 2026)。

- ✅ 设计文档完成
- ✅ 仓库脚手架(本目录)
- ⏳ 数据层骨架 (ClickHouse + Kafka)
- ⏳ Ingest Gateway (Go)
- ⏳ IAOEP SDK (Java / Python / TypeScript / Go)
- ⏳ 评测引擎 (Phase 2)
- ⏳ 自进化引擎 (Phase 3)

完整路线图见 [docs/agentops-platform-design.md](../docs/agentops-platform-design.md)(文档目录待建)。

---

## 文档索引

| 文档 | 说明 |
|---|---|
| [docs/IAOEP-README.md](../docs/IAOEP-README.md) | 项目介绍 / 特性总览 |
| [docs/agentops-platform-design.md](../docs/agentops-platform-design.md) | 完整设计(架构 / 数据模型 / API / 路线图) |
| [docs/sdk-design.md](../docs/sdk-design.md) | SDK 详细 API(Phase 1 写) |
| [ingest/README.md](ingest/README.md) | Ingest Gateway 协议细节 |
| [sdk/java/README.md](sdk/java/README.md) | Java SDK 完整 API |
| [sdk/python/README.md](sdk/python/README.md) | Python SDK 完整 API |
| [sdk/typescript/README.md](sdk/typescript/README.md) | TypeScript SDK 完整 API |
| [sdk/go/README.md](sdk/go/README.md) | Go SDK 完整 API |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献指南 |
| [REPOSITORY_STRUCTURE.md](REPOSITORY_STRUCTURE.md) | 仓库结构 |
| [LICENSE](LICENSE) | MIT License |

---

## License

[MIT](LICENSE)
