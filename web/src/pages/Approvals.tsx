import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { CheckCircle2, XCircle, AlertTriangle, ShieldCheck } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockSuggestion } from '@/lib/api';
import { cn, formatRelativeTime } from '@/lib/utils';

/**
 * 审批控制台 — 列出待审批的 EvolutionSuggestion, 一键通过/拒绝.
 */
export function Approvals() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ['approvals'],
    queryFn: mockApi.suggestions,
    refetchInterval: 5_000,
  });

  const suggestions: MockSuggestion[] = (data ?? []).filter((s) => s.status === 'PROPOSED');
  const highRisk = suggestions.filter((s) => s.riskLevel === 'HIGH').length;
  const mediumRisk = suggestions.filter((s) => s.riskLevel === 'MEDIUM').length;

  const approve = useMutation({
    mutationFn: (id: string) => mockApi.approveSuggestion(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['approvals'] }),
  });

  const reject = useMutation({
    mutationFn: (id: string) => mockApi.rejectSuggestion(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['approvals'] }),
  });

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <ShieldCheck className="w-6 h-6" />
          审批控制台
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          共 {suggestions.length} 个待审批 ·{' '}
          <span className="text-red-500">{highRisk} 高风险</span> ·{' '}
          <span className="text-orange-500">{mediumRisk} 中风险</span>
        </p>
      </div>

      {suggestions.length === 0 ? (
        <Card>
          <CardContent className="p-12 text-center text-muted-foreground">
            🎉 当前没有待审批的建议
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>待办列表</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y divide-border">
              {suggestions.map((s) => (
                <div key={s.id} className="p-4 space-y-3">
                  <div className="flex items-start gap-3">
                    <RiskBadge level={s.riskLevel} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium">{s.target}</span>
                        <span className="text-xs px-2 py-0.5 rounded-full bg-secondary">
                          {s.type}
                        </span>
                      </div>
                      <p className="text-sm text-muted-foreground mt-1">{s.description}</p>
                      <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                        <span>类型: <code className="text-foreground">{s.type}</code></span>
                        <span>预期 ROI: <code className="text-foreground">{(s.expectedRoi * 100).toFixed(0)}%</code></span>
                        <span>{formatRelativeTime(s.createdAt)}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 pl-7">
                    <button
                      onClick={() => approve.mutate(s.id)}
                      disabled={approve.isPending}
                      className="flex items-center gap-2 px-3 py-1.5 bg-green-500 text-white rounded-md text-sm hover:bg-green-600 disabled:opacity-50"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      通过
                    </button>
                    <button
                      onClick={() => reject.mutate(s.id)}
                      disabled={reject.isPending}
                      className="flex items-center gap-2 px-3 py-1.5 bg-red-500 text-white rounded-md text-sm hover:bg-red-600 disabled:opacity-50"
                    >
                      <XCircle className="w-4 h-4" />
                      拒绝
                    </button>
                    {s.riskLevel === 'HIGH' && (
                      <span className="text-xs text-red-500 ml-2">
                        ⚠️ 高风险需 2 个 Maintainer 签字
                      </span>
                    )}
                    <Link
                      to={`/suggestions/${s.id}`}
                      className="ml-auto text-xs text-primary hover:underline"
                    >
                      详情 →
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function RiskBadge({ level }: { level: 'LOW' | 'MEDIUM' | 'HIGH' }) {
  const config = {
    LOW:    { color: 'text-green-500 bg-green-500/10',     icon: CheckCircle2, label: '低风险' },
    MEDIUM: { color: 'text-orange-500 bg-orange-500/10',  icon: AlertTriangle, label: '中风险' },
    HIGH:   { color: 'text-red-500 bg-red-500/10',        icon: AlertTriangle, label: '高风险' },
  }[level];
  const Icon = config.icon;
  return (
    <div className={cn('shrink-0 w-8 h-8 rounded-full flex items-center justify-center', config.color)}>
      <Icon className="w-4 h-4" />
    </div>
  );
}
