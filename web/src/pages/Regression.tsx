import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockRegression } from '@/lib/api';
import { formatRelativeTime } from '@/lib/utils';

/**
 * 回归检测页 — 版本对比
 */
export function Regression() {
  const [baseline, setBaseline] = useState('');
  const [candidate, setCandidate] = useState('');

  const { data } = useQuery({
    queryKey: ['regressions'],
    queryFn: mockApi.regressions,
    refetchInterval: 5_000,
  });

  const reports: MockRegression[] = data ?? [];

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">回归检测</h1>
        <p className="text-sm text-muted-foreground mt-1">
          对比两个 Agent 版本的评测结果, 维度下降超阈值则阻断.
        </p>
      </div>

      {/* 新建对比 (Phase 2 简化 UI) */}
      <Card>
        <CardHeader>
          <CardTitle>新建对比</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-3">
            <input
              placeholder="基线任务 ID"
              value={baseline}
              onChange={(e) => setBaseline(e.target.value)}
              className="flex h-9 rounded-md border border-input bg-transparent px-3 text-sm flex-1"
            />
            <ArrowRight className="w-4 h-4 text-muted-foreground" />
            <input
              placeholder="候选任务 ID"
              value={candidate}
              onChange={(e) => setCandidate(e.target.value)}
              className="flex h-9 rounded-md border border-input bg-transparent px-3 text-sm flex-1"
            />
            <button className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
              对比
            </button>
          </div>
        </CardContent>
      </Card>

      {/* 历史报告 */}
      <Card>
        <CardHeader>
          <CardTitle>回归报告</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {reports.map((r) => (
              <div key={r.id} className="flex items-center gap-4 p-4">
                {r.passed ? (
                  <CheckCircle2 className="w-5 h-5 text-green-500 shrink-0" />
                ) : (
                  <AlertTriangle className="w-5 h-5 text-red-500 shrink-0" />
                )}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-mono">{r.baselineVersion}</span>
                    <ArrowRight className="w-3 h-3" />
                    <span className="font-mono">{r.candidateVersion}</span>
                    <span
                      className={
                        r.passed
                          ? 'text-xs px-2 py-0.5 rounded-full bg-green-500/10 text-green-500'
                          : 'text-xs px-2 py-0.5 rounded-full bg-red-500/10 text-red-500'
                      }
                    >
                      {r.passed ? 'PASSED' : 'FAILED'}
                    </span>
                  </div>
                  <div className="text-xs text-muted-foreground mt-1">
                    overall delta: {r.delta >= 0 ? '+' : ''}
                    {r.delta.toFixed(3)} (threshold: {r.threshold.toFixed(3)})
                  </div>
                </div>
                <div className="text-xs text-muted-foreground">
                  {formatRelativeTime(r.createdAt)}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
