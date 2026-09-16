# Phase 6: Service Mesh A/B Test 集成

> 用 Istio / Envoy 在 Agent 网关层做 A/B 流量分流,**无需 SDK 改代码**。

## 架构

```
                          ┌─────────────────────┐
                          │   Istio Control Plane │
                          │   (istiod)            │
                          └──────────┬──────────┘
                                     │ xDS 配置
                                     ↓
┌──────────┐   请求    ┌─────────┐    拦截   ┌─────────┐    路由    ┌─────────┐
│ Client  │ ─────────→│ Envoy   │ ─────────→│ Envoy   │ ─────────→│ Agent  │
│ (无 SDK) │           │ Sidecar │ 注入 header │ Sidecar │ 50/50    │ v1.0  │
└──────────┘           │ (Client) │             │ (Server) │ split    └─────────┘
                       └─────────┘             └─────────┘  ─────────→ ┌─────────┐
                          │ 路由决策:               │              │ Agent  │
                          │ - 50% baseline          │              │ v1.1  │
                          │ - 50% candidate         │              └─────────┘
                          └────────────────────────┘
```

## 为什么用 Service Mesh?

| 方式 | 优点 | 缺点 |
|---|---|---|
| **SDK @ABTest (Phase 4-5)** | 细粒度, 业务感知 | 需改 Agent 代码 |
| **Service Mesh (Phase 6)** | 零侵入, 集中管理 | 需要 Istio / Linkerd |
| **API 网关 (Kong/APISIX)** | 集中 | 仅入口, 不覆盖内部调用 |

Service Mesh 适合: **多语言 Agent / 老系统改造 / 需要灰度发布**。

## 文件结构

```
service-mesh/
├── README.md                      # 本文件
├── istio/
│   ├── virtual-service.yaml       # 50/50 流量路由
│   ├── destination-rule.yaml      # 两个 subset (baseline / candidate)
│   ├── envoy-filter.yaml          # 注入 X-IAOEP-AB-Test-Group header
│   └── telemetry.yaml             # Istio Telemetry (对接 IAOEP)
├── demo/
│   ├── agent-v1.yaml              # Agent v1.0 (baseline)
│   ├── agent-v1.1.yaml            # Agent v1.1-rc1 (candidate)
│   └── otel-collector.yaml        # OpenTelemetry Collector (转 OTLP)
└── quickstart.md                  # 5 分钟跑通
```

## 关键资源 (Istio)

### VirtualService — 50/50 流量分流

```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: customer-agent
spec:
  hosts:
    - customer-agent
  http:
    - match:
        - headers:
            x-iaoep-ab-test-group:
              exact: baseline
      route:
        - destination:
            host: customer-agent
            subset: v1
    - match:
        - headers:
            x-iaoep-ab-test-group:
              exact: candidate
      route:
        - destination:
            host: customer-agent
            subset: v1.1
    - route:    # 默认 (无 header 时 50/50)
      - destination:
          host: customer-agent
          subset: v1
        weight: 50
      - destination:
          host: customer-agent
          subset: v1.1
        weight: 50
```

### DestinationRule — 两个 subset (baseline / candidate 版本)

```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: customer-agent
spec:
  host: customer-agent
  subsets:
    - name: v1
      labels:
        version: v1.0
    - name: v1.1
      labels:
        version: v1.1-rc1
```

### EnvoyFilter — 注入 header

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: EnvoyFilter
metadata:
  name: iaoep-ab-test-injector
spec:
  configPatches:
    - applyTo: HTTP_FILTER
      match:
        context: SIDECAR_INBOUND
        listener:
          filterChain:
            name: envoy.filters.network.http_connection_manager
      patch:
        operation: INSERT_BEFORE
        value:
          name: envoy.filters.http.lua
          typed_config:
            # Lua 脚本: 根据 Envoy 路由 metadata 注入 X-IAOEP-AB-Test-Group header
            # (Istio VirtualService 已把 group 写到 request header)
            ...
```

(简化: 实际通过 `requestHeadersToAdd` 也行, EnvoyFilter Lua 用于更复杂场景)

### Telemetry — trace 送到 IAOEP

```yaml
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: iaoep-telemetry
spec:
  tracing:
    - providers:
        - name: otel-collector
      randomSamplingPercentage: 100
```

(OTel Collector 配 OTLP exporter → IAOEP Ingest Gateway)

## 部署

```bash
# 1. 安装 Istio (kind / minikube)
istioctl install --set profile=demo -y

# 2. 启用 sidecar 注入
kubectl label namespace default istio-injection=enabled

# 3. 部署 Agent 两个版本
kubectl apply -f demo/agent-v1.yaml
kubectl apply -f demo/agent-v1.1.yaml

# 4. 部署 Istio 路由配置
kubectl apply -f istio/virtual-service.yaml
kubectl apply -f istio/destination-rule.yaml

# 5. 测试
for i in {1..10}; do
  curl http://customer-agent:8080/chat -d '{"query":"你好"}'
done
```

## 验证

```bash
# 1. 在 IAOEP Web Dashboard 按 ab_test_group 分组看 trace
# 2. ClickHouse 查询:
SELECT ab_test_group, count(*), avg(duration_ms), sum(llm_input_tokens+llm_output_tokens)/count() avg_tokens
FROM iaoep.traces
WHERE ab_test_name = 'customer-service-ab'
GROUP BY ab_test_group;
```

## 完整链路

```
[客户端] (无 SDK)
    ↓ HTTP POST /chat
[Envoy Sidecar (Client)] — 注入 X-Forwarded-For 等
    ↓
[Istio Routing] — VirtualService 50/50 split
    ↓ (selected subset: v1.0 或 v1.1)
[Envoy Sidecar (Server)] — 注入 X-IAOEP-AB-Test-Group header
    ↓
[Agent v1.0 / v1.1] — 读 header 决定版本 (可选)
    ↓ (OTel auto-instrumentation)
[OTel Collector]
    ↓ OTLP
[IAOEP Ingest Gateway] → [Kafka] → [Storage Worker] → [ClickHouse]
```

## 适用场景

- ✅ **多语言 Agent 团队**: Python / TypeScript / Java 混合, 不想每个 SDK 都接 A/B
- ✅ **老系统改造**: 已有的 Agent 服务无法改代码加 SDK
- ✅ **灰度发布**: 新版本逐步放量 (5% → 25% → 50% → 100%)
- ✅ **跨服务调用链**: A/B 决策需要跨多个服务传递

## 不适用场景

- ❌ **单语言 / 单服务**: 直接用 SDK (Phase 4) 更简单
- ❌ **细粒度用户级 A/B**: Service Mesh 不擅长按 user_id sticky
- ❌ **没有 K8s / Service Mesh 基础设施**: 用 SDK 更轻

## License

[MIT](../../../LICENSE)
