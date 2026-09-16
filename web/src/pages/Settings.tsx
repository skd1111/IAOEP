import { useState } from 'react';
import { Save, Key, Webhook, Bell, Database, Cpu } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { cn } from '@/lib/utils';

/**
 * Settings — IAOEP 配置管理页 (Phase 7.10+).
 *
 * <p>v1.0 GA: 默认 mock, 配置改动只更新本地 state (不持久化).
 * 真实部署中, 这页对应后端 PUT /api/v1/iaoep/settings 接口 (Phase 8 接).</p>
 */
export function Settings() {
  const [tenantId, setTenantId] = useState('demo-tenant');
  const [serviceName, setServiceName] = useState('customer-service-demo');
  const [apiKey, setApiKey] = useState('demo-key-xxxxxxxxxxxxxxxx');
  const [endpoint, setEndpoint] = useState('http://localhost:4318');
  const [dingtalkWebhook, setDingtalkWebhook] = useState('https://oapi.dingtalk.com/robot/send?access_token=xxx');
  const [wechatWebhook, setWechatWebhook] = useState('');
  const [feishuWebhook, setFeishuWebhook] = useState('');
  const [epsilon, setEpsilon] = useState('1.0');
  const [anomalyThreshold, setAnomalyThreshold] = useState('0.15');

  const [savedAt, setSavedAt] = useState<string | null>(null);

  const save = () => {
    setSavedAt(new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
  };

  return (
    <div className="p-6 space-y-4 max-w-4xl">
      <div>
        <h1 className="text-2xl font-semibold">设置</h1>
        <p className="text-sm text-muted-foreground mt-1">
          IAOEP 客户端配置 · 默认 mock, 改动只更新本地预览 (Phase 8 接持久化 API)
        </p>
      </div>

      {/* 1. Agent 接入 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Cpu className="w-4 h-4" />
            Agent 接入
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Field label="Tenant ID" hint="多租户隔离标识">
            <input
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
            />
          </Field>
          <Field label="Service Name" hint="对应 OTLP resource attribute service.name">
            <input
              value={serviceName}
              onChange={(e) => setServiceName(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
            />
          </Field>
          <Field label="IAOEP Endpoint" hint="OTLP/HTTP 地址, 默认 Ingest Gateway 端口">
            <input
              value={endpoint}
              onChange={(e) => setEndpoint(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm font-mono"
            />
          </Field>
          <Field label="API Key" hint="Ingest Gateway 鉴权 (Phase 9 多租户启用)">
            <div className="flex items-center gap-2">
              <Key className="w-4 h-4 text-muted-foreground" />
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                className="flex-1 h-9 rounded-md border border-input bg-transparent px-3 text-sm font-mono"
              />
            </div>
          </Field>
        </CardContent>
      </Card>

      {/* 2. 通知 Webhook */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Webhook className="w-4 h-4" />
            通知 Webhook
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Field label="钉钉 (dingtalk)" hint="https://oapi.dingtalk.com/robot/send?access_token=xxx">
            <input
              value={dingtalkWebhook}
              onChange={(e) => setDingtalkWebhook(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm font-mono"
              placeholder="留空 = 不通知"
            />
          </Field>
          <Field label="企业微信 (wechat_work)">
            <input
              value={wechatWebhook}
              onChange={(e) => setWechatWebhook(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm font-mono"
              placeholder="留空 = 不通知"
            />
          </Field>
          <Field label="飞书 (feishu)">
            <input
              value={feishuWebhook}
              onChange={(e) => setFeishuWebhook(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm font-mono"
              placeholder="留空 = 不通知"
            />
          </Field>
        </CardContent>
      </Card>

      {/* 3. 联邦 + 告警 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Bell className="w-4 h-4" />
            联邦学习 + 异常告警
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Field label="ε (隐私预算, 默认 1.0)" hint="越小噪声越大, 越隐私">
            <input
              type="number"
              step="0.1"
              value={epsilon}
              onChange={(e) => setEpsilon(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
            />
          </Field>
          <Field label="异常告警阈值 (默认 0.15)" hint="某 dimension noisy 偏离历史 > 此值触发告警">
            <input
              type="number"
              step="0.05"
              value={anomalyThreshold}
              onChange={(e) => setAnomalyThreshold(e.target.value)}
              className="w-full h-9 rounded-md border border-input bg-transparent px-3 text-sm"
            />
          </Field>
        </CardContent>
      </Card>

      {/* 4. 数据源 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Database className="w-4 h-4" />
            数据源
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground space-y-2">
            <p>
              v1.0 GA: <span className="text-foreground font-medium">默认全部 mock 数据</span>,
              不需要 ClickHouse / PostgreSQL 即可跑完整 demo.
            </p>
            <p>
              生产部署建议接 ClickHouse:
              <code className="text-foreground font-mono bg-muted px-1 rounded ml-1">
                iaoep.evaluator.federation.datasource=clickhouse
              </code>
            </p>
          </div>
        </CardContent>
      </Card>

      {/* 保存按钮 */}
      <div className="flex items-center gap-3 sticky bottom-4 bg-background/80 backdrop-blur p-3 rounded-md border border-border">
        <button
          onClick={save}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm"
        >
          <Save className="w-4 h-4" />
          保存 (本地预览)
        </button>
        {savedAt && (
          <span className="text-xs text-muted-foreground">
            ✓ 已保存于 {savedAt}
          </span>
        )}
      </div>
    </div>
  );
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="text-xs font-medium text-foreground">{label}</label>
      {hint && <p className="text-xs text-muted-foreground mb-1">{hint}</p>}
      {children}
    </div>
  );
}
