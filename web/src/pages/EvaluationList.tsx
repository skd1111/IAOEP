import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Play, CheckCircle2, XCircle, Clock, AlertCircle } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockEvaluation } from '@/lib/api';
import { formatRelativeTime, formatDuration } from '@/lib/utils';

/**
 * 评测任务列表 — Phase 2 完整版
 *
 * 注: Phase 1 backend API 还没实现, 用 mock 数据. Phase 2 backend 完成后切换.
 */
export function EvaluationList() {
  const { data } = useQuery({
    queryKey: ['evaluations'],
    queryFn: mockApi.evaluations,
    refetchInterval: 5_000, // 5s 刷新 (看进度)
  });

  const evaluations: MockEvaluation[] = data ?? [];
  const completed = evaluations.filter((e) => e.status === 'completed').length;
  const failed = evaluations.filter((e) => e.status === 'failed').length;
  const running = evaluations.filter((e) => e.status === 'running').length;

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">评测任务</h1>
          <p className="text-sm text-muted-foreground mt-1">
            共 {evaluations.length} 个 ·{' '}
            <span className="text-green-500">{completed} 完成</span> ·{' '}
            <span className="text-red-500">{failed} 失败</span> ·{' '}
            <span className="text-blue-500">{running} 运行中</span>
          </p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md">
          <Play className="w-4 h-4" />
          触发新评测
        </button>
      </div>

      {/* 评测任务列表 */}
      <Card>
        <CardHeader>
          <CardTitle>评测列表</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {evaluations.map((e) => (
              <EvaluationRow key={e.id} evaluation={e} />
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function EvaluationRow({ evaluation }: { evaluation: MockEvaluation }) {
  const statusIcon = {
    pending: <Clock className="w-4 h-4 text-muted-foreground" />,
    running: <Play className="w-4 h-4 text-blue-500 animate-pulse" />,
    completed: <CheckCircle2 className="w-4 h-4 text-green-500" />,
    failed: <XCircle className="w-4 h-4 text-red-500" />,
    cancelled: <AlertCircle className="w-4 h-4 text-orange-500" />,
  }[evaluation.status];

  return (
    <Link
      to={`/evaluations/${evaluation.id}`}
      className="flex items-center gap-4 p-4 hover:bg-accent transition-colors"
    >
      <div className="shrink-0">{statusIcon}</div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm">{evaluation.id.slice(0, 8)}</span>
          <span className="text-sm font-medium">{evaluation.agentVersion}</span>
          {evaluation.score !== undefined && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-secondary">
              score: {evaluation.score.toFixed(2)}
            </span>
          )}
        </div>
        <div className="text-xs text-muted-foreground mt-1">
          {evaluation.completedCases}/{evaluation.totalCases} cases ·{' '}
          {formatRelativeTime(evaluation.createdAt)}
        </div>
        {/* 进度条 */}
        {evaluation.status === 'running' && (
          <div className="mt-2 h-1 bg-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-primary transition-all"
              style={{ width: `${evaluation.progress * 100}%` }}
            />
          </div>
        )}
      </div>
      <div className="text-right">
        <div className="text-xs text-muted-foreground">
          {evaluation.duration ? formatDuration(evaluation.duration) : '-'}
        </div>
      </div>
    </Link>
  );
}
