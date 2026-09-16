# IAOEP Helm Chart

K8s 一键部署 IAOEP (Ingest + Storage + Web + ClickHouse + Kafka)。

## 前置条件

- K8s 1.24+
- Helm 3.8+
- 已安装 Bitnami Helm 仓库 (默认依赖)

## 快速安装

```bash
# 添加 Bitnami 仓库 (如果还没)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# 创建 namespace
kubectl create namespace monitoring

# 安装 (使用默认配置)
helm install iaoep ./helm --namespace monitoring

# 查看资源
kubectl get all -n monitoring -l app.kubernetes.io/part-of=iaoep
```

## 访问服务

| 服务 | 端口 (ClusterIP) | 端口 (NodePort/LoadBalancer) | 说明 |
|---|---|---|---|
| Ingest OTLP/HTTP | 4318 | 同左 | Agent 上报 OTLP trace |
| Ingest OTLP/gRPC | 4317 | 同左 | 高性能 binary 上报 |
| Storage | 8081 | 同左 | 健康检查 + Prometheus |
| Web Dashboard | 80 | 同左 (默认 ClusterIP) | UI |

## 启用 Web Ingress

```yaml
# values.yaml
web:
  ingress:
    enabled: true
    className: nginx
    hosts:
      - host: iaoep.example.com
        paths:
          - path: /
            pathType: Prefix
```

```bash
helm upgrade iaoep ./helm --namespace monitoring -f my-values.yaml
```

## 多租户 + 限流配置

```yaml
# values.yaml
ingest:
  # 多个 tenant 用逗号分隔
  apiKeys: "tenant-acme:secret-key-acme-123,tenant-foo:secret-key-foo-456"
  rateLimit: 500      # 每 tenant 500 spans/s
  rateBurst: 5000     # 突发可至 5000 spans
```

Agent 上报时:
```bash
curl -X POST http://iaoep-ingest:4318/v1/traces \
  -H "Content-Type: application/json" \
  -H "X-IAOEP-API-Key: secret-key-acme-123" \
  -d @trace.json
```

错误码:
- `401 Unauthorized` — API Key 错误
- `429 Too Many Requests` — 超出限流
- `400 Bad Request` — OTLP payload 格式错误

## 自定义存储

```yaml
clickhouse:
  persistence:
    size: 100Gi
    storageClass: ssd
  auth:
    existingSecret: my-clickhouse-secret
    username: iaoep
kafka:
  replicaCount: 3
  persistence:
    size: 50Gi
```

## 升级

```bash
helm upgrade iaoep ./helm --namespace monitoring -f my-values.yaml
```

## 卸载

```bash
helm uninstall iaoep -n monitoring
# 数据默认保留 (PVC), 需手动清理:
kubectl delete pvc -n monitoring -l app.kubernetes.io/part-of=iaoep
```

## 数据迁移 / 备份

ClickHouse 备份:
```bash
kubectl exec -n monitoring deploy/iaoep-clickhouse -- \
  clickhouse-client --query "BACKUP DATABASE iaoep TO S3('s3://bucket/backup')"
```

Kafka 暂未配置备份,生产建议开启 MirrorMaker 2 跨集群同步。

## 文件结构

```
helm/
├── Chart.yaml          # Chart 元数据
├── values.yaml         # 默认配置 (覆盖这里)
├── templates/
│   ├── _helpers.tpl    # 模板辅助函数
│   ├── ingest/         # Ingest Gateway
│   ├── storage/        # Storage Worker
│   └── web/            # Web Dashboard
└── README.md           # 本文件
```

## License

[MIT](../../LICENSE)
