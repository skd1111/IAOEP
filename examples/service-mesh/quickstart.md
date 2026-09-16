# Phase 6 快速开始 — Service Mesh A/B Test

> 5 分钟在本地 kind/minikube 集群跑通 Istio + Agent v1/v1.1 + IAOEP 完整链路

## 前置条件

- Docker + kind (或 minikube)
- Istio 1.20+ (`istioctl` CLI)
- kubectl
- (可选) OpenTelemetry Collector

## 1. 启动本地 K8s 集群

```bash
# 用 kind 启动
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
- role: worker
- role: worker
EOF

# 安装 Istio
istioctl install --set profile=demo -y
kubectl label namespace default istio-injection=enabled
```

## 2. 部署 OTel Collector

```bash
kubectl apply -f demo/otel-collector.yaml
```

## 3. 部署 Agent 两个版本

```bash
# baseline 版本 (qwen3-turbo)
kubectl apply -f demo/agent-v1.yaml

# candidate 版本 (qwen3-max)
kubectl apply -f demo/agent-v1.1.yaml

# 等 ready
kubectl wait --for=condition=available deployment/customer-agent-v1
kubectl wait --for=condition=available deployment/customer-agent-v1.1
```

## 4. 配置 Istio 路由 (50/50 + 路由透传)

```bash
kubectl apply -f istio/destination-rule.yaml
kubectl apply -f istio/virtual-service.yaml
kubectl apply -f istio/envoy-filter.yaml
kubectl apply -f istio/telemetry.yaml
```

## 5. 暴露 Agent 服务

```bash
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: customer-agent-gateway
spec:
  selector:
    istio: ingressgateway
  servers:
    - port:
        number: 80
        name: http
        protocol: HTTP
      hosts:
        - "*"
---
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: customer-agent-vs-gw
spec:
  hosts:
    - "*"
  gateways:
    - customer-agent-gateway
  http:
    - route:
        - destination:
            host: customer-agent
EOF

# 获取 ingress IP
kubectl get svc istio-ingressgateway -n istio-system
```

## 6. 触发请求

```bash
INGRESS_IP=$(kubectl get svc istio-ingressgateway -n istio-system -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

for i in {1..10}; do
  curl -s "http://$INGRESS_IP/chat" \
    -H "Content-Type: application/json" \
    -d '{"query":"你好","userId":"u-'$i'"}'
  echo ""
done
```

## 7. 验证 A/B 分流

### Istio 端

```bash
# 看两个 subset 的请求数
kubectl logs -l app=customer-agent,version=v1.0 -c istio-proxy | grep -c "POST /chat"
kubectl logs -l app=customer-agent,version=v1.1-rc1 -c istio-proxy | grep -c "POST /chat"

# 应大致 50/50
```

### IAOEP 端

```bash
# ClickHouse 按 ab_test_group 聚合
docker exec iaoep-clickhouse clickhouse-client \
  --user iaoep --password iaoep_dev_pwd \
  --query "SELECT ab_test_group, count(DISTINCT trace_id), count() AS spans, avg(duration_ms) FROM iaoep.traces WHERE ab_test_name='customer-service-ab' GROUP BY ab_test_group FORMAT Vertical"
```

预期:
```
baseline    5    15    185.3
candidate   5    15    612.7
```

### Web Dashboard

`http://localhost:5173/traces?ab_test_group=candidate` → 只看 candidate 的 trace。

## 8. 决策 — 哪个版本胜出

跑评测 (用 Golden Dataset) 对比两个版本:

```bash
# (复用 Phase 2 / 3 的 Evaluation API)
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{id}/evaluations \
  -H "Content-Type: application/json" \
  -d '{"agentVersion": "v1.0", "datasetId": "..."}'

# candidate 评测
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{id}/evaluations \
  -d '{"agentVersion": "v1.1-rc1", "datasetId": "..."}'

# 对比 (回归)
curl -X POST http://localhost:8082/api/v1/iaoep/projects/{id}/regression \
  -d '{"baselineJobId":"...", "candidateJobId":"..."}'
```

如果 candidate 胜出:
- 把 VirtualService 的 weight 从 50/50 调成 0/100 (全量切到 candidate)
- 或用 `kubectl apply -f istio/virtual-service-candidate-100.yaml`

如果 candidate 劣化:
- 回滚 (kubectl rollout undo deployment/customer-agent-v1.1)
- 切回 100% baseline

## 9. 清理

```bash
istioctl uninstall -y --purge
kind delete cluster
```
