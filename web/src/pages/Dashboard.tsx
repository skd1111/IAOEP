import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  Activity, AlertCircle, Clock, DollarSign, Hash, Users, Zap,
  GitCommit, Undo2, ShieldCheck, TrendingUp, ArrowRight, Sparkles,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { api, mockApi } from '@/lib/api';
import { formatNumber, formatCost, formatDuration } from '@/lib/utils';
import type { DashboardMetrics, EvolutionInsight } from '@/lib/types';

/**
 * Dashboard 主页 — 总览 metrics + 自进化洞察 + 闭环流程图
 *
 * 优先用真实 API, 失败时 fallback 到 mock 数据.
 */
export function Dashboard() {
  const { data: metrics, isError } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: api.getDashboardMetrics,
    refetchInterval: 30_000,
    retry: 1,
  });

  const { data: insightData, isError: insightError } = useQuery({
    queryKey: ['evolution-insight'],
    queryFn: api.getEvolutionInsight,
    refetchInterval: 30_000,
    retry: 1,
  });

  const resolved: DashboardMetrics = (metrics ?? (isError ? mockApi.metrics() : undefined))!;
  const insight: EvolutionInsight = (insightData ?? (insightError ? mockApi.evolutionInsight() : undefined))!;

  if (!resolved) {
    return <div className="p-6 text-muted-foreground">Loading...</div>;
  }

  const cards = [
    { label: 'Traces (24h)', value: formatNumber(resolved.total_traces_24h), icon: Activity, accent: 'text-blue-500' },
    { label: 'Spans (24h)', value: formatNumber(resolved.total_spans_24h), icon: Hash, accent: 'text-cyan-500' },
    { label: 'Tokens (24h)', value: formatNumber(resolved.total_tokens_24h), icon: Zap, accent: 'text-purple-500' },
    { label: 'Cost (24h)', value: formatCost(resolved.total_cost_24h_cny), icon: DollarSign, accent: 'text-green-500' },
    { label: 'P50 Latency', value: formatDuration(resolved.p50_latency_ms), icon: Clock, accent: 'text-orange-500' },
    { label: 'P95 Latency', value: formatDuration(resolved.p95_latency_ms), icon: Clock, accent: 'text-orange-600' },
    { label: 'P99 Latency', value: formatDuration(resolved.p99_latency_ms), icon: Clock, accent: 'text-red-500' },
    { label: 'Error Rate', value: `${(resolved.error_rate * 100).toFixed(1)}%`, icon: AlertCircle, accent: resolved.error_rate > 0.05 ? 'text-red-500' : 'text-green-500' },
    { label: 'Active Agents', value: resolved.active_agents, icon: Users, accent: 'text-indigo-500' },
  ];

  const evolutionCards = insight ? [
    {
      label: '自动发现问题',
      value: insight.autoDiscovered,
      sub: '本周',
      icon: Sparkles,
      accent: 'text-purple-500',
      bg: 'bg-purple-500/10',
      link: '/evolution',
    },
    {
      label: '自动回滚',
      value: insight.autoRollbacks,
      sub: '已执行',
      icon: Undo2,
      accent: 'text-red-500',
      bg: 'bg-red-500/10',
      link: '/evolution',
    },
    {
      label: '待审批建议',
      value: insight.pendingApprovals,
      sub: '需要处理',
      icon: ShieldCheck,
      accent: 'text-orange-500',
      bg: 'bg-orange-500/10',
      link: '/approvals',
    },
    {
      label: '自进化成功率',
      value: `${(insight.successRate * 100).toFixed(0)}%`,
      sub: '近 30 天',
      icon: TrendingUp,
      accent: 'text-green-500',
      bg: 'bg-green-500/10',
      link: '/evolution',
    },
    {
      label: '节省成本',
      value: formatCost(insight.costSaved),
      sub: '本月累计',
      icon: DollarSign,
      accent: 'text-emerald-500',
      bg: 'bg-emerald-500/10',
      link: '/dashboard',
    },
    {
      label: '延迟改善',
      value: `-${insight.latencyImproved}%`,
      sub: 'P95 优化',
      icon: TrendingUp,
      accent: 'text-cyan-500',
      bg: 'bg-cyan-500/10',
      link: '/dashboard',
    },
  ] : [];

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

      {/* ─── 自进化洞察 ─── */}
      {insight && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <GitCommit className="w-5 h-5 text-purple-500" />
              <h2 className="text-lg font-semibold">自进化洞察</h2>
              <span className="text-xs px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-500 font-medium">
                IAOEP 独有
              </span>
            </div>
            <Link to="/evolution" className="text-sm text-primary hover:underline flex items-center gap-1">
              查看详情 <ArrowRight className="w-3 h-3" />
            </Link>
          </div>

          <div className="grid grid-cols-3 gap-4">
            {evolutionCards.map((c) => (
              <Link key={c.label} to={c.link}>
                <Card className="hover:border-purple-500/30 transition-colors cursor-pointer">
                  <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                    <CardTitle className="text-sm font-medium text-muted-foreground">
                      {c.label}
                    </CardTitle>
                    <div className={`p-1.5 rounded-md ${c.bg}`}>
                      <c.icon className={`w-4 h-4 ${c.accent}`} />
                    </div>
                  </CardHeader>
                  <CardContent>
                    <div className={`text-2xl font-bold ${c.accent}`}>{c.value}</div>
                    <div className="text-xs text-muted-foreground mt-1">{c.sub}</div>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* ─── 闭环流程图 ─── */}
      <Card className="border-purple-500/20 bg-gradient-to-r from-purple-500/5 via-transparent to-cyan-500/5">
        <CardHeader>
          <div className="flex items-center gap-2">
            <CardTitle className="text-base">观测 → 评测 → 自进化 闭环</CardTitle>
            <span className="text-xs px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-500">
              IAOEP 独有
            </span>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between gap-2 py-4">
            <FlowStep
              icon={Activity}
              label="观测"
              desc="OTLP Trace 采集"
              color="text-blue-500"
              bg="bg-blue-500/10"
            />
            <FlowArrow />
            <FlowStep
              icon={TrendingUp}
              label="评测"
              desc="Golden Dataset + LLM-as-Judge"
              color="text-orange-500"
              bg="bg-orange-500/10"
            />
            <FlowArrow />
            <FlowStep
              icon={Sparkles}
              label="分析"
              desc="Analyst Agent 自动发现"
              color="text-purple-500"
              bg="bg-purple-500/10"
            />
            <FlowArrow />
            <FlowStep
              icon={ShieldCheck}
              label="审批"
              desc="三级风险审批流"
              color="text-orange-600"
              bg="bg-orange-600/10"
            />
            <FlowArrow />
            <FlowStep
              icon={GitCommit}
              label="进化"
              desc="自动应用 + 回滚"
              color="text-green-500"
              bg="bg-green-500/10"
            />
          </div>
          <p className="text-xs text-muted-foreground text-center mt-2">
            唯一实现完整自进化闭环的开源平台
          </p>
        </CardContent>
      </Card>

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

function FlowStep({ icon: Icon, label, desc, color, bg }: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  desc: string;
  color: string;
  bg: string;
}) {
  return (
    <div className="flex flex-col items-center gap-2 flex-1">
      <div className={`p-3 rounded-xl ${bg}`}>
        <Icon className={`w-6 h-6 ${color}`} />
      </div>
      <div className="text-sm font-medium">{label}</div>
      <div className="text-xs text-muted-foreground text-center">{desc}</div>
    </div>
  );
}

function FlowArrow() {
  return <ArrowRight className="w-5 h-5 text-muted-foreground/50 shrink-0" />;
}
