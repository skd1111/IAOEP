import { useQuery } from '@tanstack/react-query';
import { Activity, AlertCircle, Clock, DollarSign, Hash, Users, Zap } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi } from '@/lib/api';
import { formatNumber, formatCost, formatDuration } from '@/lib/utils';

/**
 * Dashboard 主页 — 总览 metrics + 关键指标卡片
 *
 * Phase 1: 用 mock 数据. Phase 2: 接入真实 API.
 */
export function Dashboard() {
  // Phase 1: mock. Phase 2: api.getDashboardMetrics()
  const { data: metrics } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: mockApi.metrics,
    refetchInterval: 30_000, // 30s 刷新
  });

  if (!metrics) {
    return <div className="p-6 text-muted-foreground">Loading...</div>;
  }

  const cards = [
    { label: 'Traces (24h)', value: formatNumber(metrics.total_traces_24h), icon: Activity, accent: 'text-blue-500' },
    { label: 'Spans (24h)', value: formatNumber(metrics.total_spans_24h), icon: Hash, accent: 'text-cyan-500' },
    { label: 'Tokens (24h)', value: formatNumber(metrics.total_tokens_24h), icon: Zap, accent: 'text-purple-500' },
    { label: 'Cost (24h)', value: formatCost(metrics.total_cost_24h_cny), icon: DollarSign, accent: 'text-green-500' },
    { label: 'P50 Latency', value: formatDuration(metrics.p50_latency_ms), icon: Clock, accent: 'text-orange-500' },
    { label: 'P95 Latency', value: formatDuration(metrics.p95_latency_ms), icon: Clock, accent: 'text-orange-600' },
    { label: 'P99 Latency', value: formatDuration(metrics.p99_latency_ms), icon: Clock, accent: 'text-red-500' },
    { label: 'Error Rate', value: `${(metrics.error_rate * 100).toFixed(1)}%`, icon: AlertCircle, accent: metrics.error_rate > 0.05 ? 'text-red-500' : 'text-green-500' },
    { label: 'Active Agents', value: metrics.active_agents, icon: Users, accent: 'text-indigo-500' },
  ];

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">仪表盘</h1>
        <p className="text-sm text-muted-foreground mt-1">
          最近 24 小时 Agent 可观测性总览
        </p>
      </div>

      {/* Metrics 卡片网格 */}
      <div className="grid grid-cols-3 gap-4">
        {cards.map((c) => (
          <Card key={c.label}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                {c.label}
              </CardTitle>
              <c.icon className={`w-4 h-4 ${c.accent}`} />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{c.value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 占位: 实时 trace 流 */}
      <Card>
        <CardHeader>
          <CardTitle>实时 trace (Phase 1 占位)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground">
            实时 trace 流将在 Phase 1 PR 8 完整版本中提供 (SSE 或 WebSocket).
            <br />
            当前页面使用 mock 数据演示 UI,backend API 将在后续 PR 提供.
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
