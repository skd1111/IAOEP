import { AlertTriangle, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockFederationAlert } from '@/lib/api';
import { cn, formatRelativeTime } from '@/lib/utils';

/**
 * Federation Alerts — Phase 7.10 异常告警页.
 *
 * <p>v1.0 GA: mock 数据. FederationScheduler 检测到 dimension noisy
 * 突然飙升/暴跌时, 通过 NotificationService 推送钉钉/企微告警, 这里展示历史告警.</p>
 */
export function FederationAlerts() {
  // Phase 7.10 简化: 直接 mock 几条历史告警
  const alerts: MockFederationAlert[] = [
    {
      id: 'alert-1',
      dimension: 'model=qwen3-turbo,skill=code-review',
      metric: 'model_accuracy',
      latestValue: 0.78,
      historicalAvg: 0.86,
      deviation: 0.08,
      threshold: 0.15,
      severity: 'warning',
      detectedAt: new Date(Date.now() - 86400_000).toISOString(),
      status: 'acknowledged',
      message: 'code-review 准确率从 0.86 跌至 0.78 (-0.08), 接近告警阈值',
    },
    {
      id: 'alert-2',
      dimension: 'model=qwen3-max,skill=data-analysis',
      metric: 'p99_latency_ms',
      latestValue: 2400,
      historicalAvg: 1800,
      deviation: 600,
      threshold: 0.15,
      severity: 'critical',
      detectedAt: new Date(Date.now() - 3 * 3600_000).toISOString(),
      status: 'open',
      message: 'P99 latency 从 1800ms 飙升至 2400ms (+33%), 已超过告警阈值',
    },
    {
      id: 'alert-3',
      dimension: 'model=qwen3-turbo',
      metric: 'error_rate',
      latestValue: 0.05,
      historicalAvg: 0.03,
      deviation: 0.02,
      threshold: 0.15,
      severity: 'warning',
      detectedAt: new Date(Date.now() - 5 * 86400_000).toISOString(),
      status: 'resolved',
      message: '错误率轻微上升, 已被运维 review',
    },
  ];

  const criticalCount = alerts.filter((a) => a.severity === 'critical' && a.status !== 'resolved').length;
  const openCount = alerts.filter((a) => a.status === 'open').length;

  return (
    <div className="p-6 space-y-4 max-w-4xl">
      <div>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <AlertTriangle className="w-6 h-6" />
          Federation 告警
          {criticalCount > 0 && (
            <span className="ml-2 text-xs px-2 py-0.5 rounded-full bg-red-500/10 text-red-500">
              {criticalCount} critical
            </span>
          )}
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          {openCount} open / {alerts.length} total · 阈值可在 Settings 调整
        </p>
      </div>

      <div className="space-y-3">
        {alerts.map((alert) => (
          <AlertCard key={alert.id} alert={alert} />
        ))}
      </div>

      {alerts.length === 0 && (
        <Card>
          <CardContent className="p-12 text-center text-muted-foreground">
            🎉 当前没有 Federation 告警
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function AlertCard({ alert }: { alert: MockFederationAlert }) {
  const isResolved = alert.status === 'resolved';
  const isCritical = alert.severity === 'critical';
  const deviationPercent = alert.historicalAvg !== 0
    ? (alert.deviation / Math.abs(alert.historicalAvg)) * 100
    : 0;

  return (
    <Card className={cn(isCritical && !isResolved && 'border-red-500/50 bg-red-500/5')}>
      <CardContent className="p-4">
        <div className="flex items-start gap-3">
          {isResolved ? (
            <CheckCircle2 className="w-5 h-5 text-green-500 shrink-0 mt-1" />
          ) : (
            <AlertTriangle
              className={cn(
                'w-5 h-5 shrink-0 mt-1',
                isCritical ? 'text-red-500' : 'text-orange-500'
              )}
            />
          )}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <SeverityBadge severity={alert.severity} />
              <StatusBadge status={alert.status} />
              <span className="text-sm font-mono">{alert.dimension}</span>
            </div>
            <p className="text-sm text-muted-foreground mt-1">{alert.message}</p>
            <div className="grid grid-cols-4 gap-3 mt-3 text-xs">
              <Metric label="Metric" value={alert.metric} />
              <Metric label="当前" value={alert.latestValue.toFixed(2)} />
              <Metric label="历史均值" value={alert.historicalAvg.toFixed(2)} />
              <Metric label="偏离" value={`${deviationPercent.toFixed(1)}%`} />
            </div>
            <div className="text-xs text-muted-foreground mt-2">
              {formatRelativeTime(new Date(alert.detectedAt).getTime())}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function SeverityBadge({ severity }: { severity: 'warning' | 'critical' }) {
  return (
    <span className={cn(
      'text-xs px-2 py-0.5 rounded-full',
      severity === 'critical'
        ? 'bg-red-500/10 text-red-500'
        : 'bg-orange-500/10 text-orange-500'
    )}>
      {severity === 'critical' ? 'Critical' : 'Warning'}
    </span>
  );
}

function StatusBadge({ status }: { status: 'open' | 'acknowledged' | 'resolved' }) {
  const config = {
    open:         { color: 'bg-red-500/10 text-red-500',         label: 'Open' },
    acknowledged: { color: 'bg-orange-500/10 text-orange-500', label: 'Acknowledged' },
    resolved:     { color: 'bg-green-500/10 text-green-500',   label: 'Resolved' },
  }[status];
  return (
    <span className={cn('text-xs px-2 py-0.5 rounded-full', config.color)}>
      {config.label}
    </span>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-muted-foreground">{label}</div>
      <div className="font-mono text-foreground">{value}</div>
    </div>
  );
}
