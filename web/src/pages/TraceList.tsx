import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Search, ChevronRight } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi } from '@/lib/api';
import { formatRelativeTime, formatDuration, formatNumber, formatCost } from '@/lib/utils';
import type { TraceSummary } from '@/lib/types';

/**
 * Trace 列表页 — 查看所有 trace,支持过滤.
 */
export function TraceList() {
  const [userId, setUserId] = useState('');
  const [agentName, setAgentName] = useState('');

  const { data } = useQuery({
    queryKey: ['traces', userId, agentName],
    queryFn: () => mockApi.traces(),
    refetchInterval: 10_000,
  });

  const traces = data?.traces ?? [];

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-semibold">链路</h1>
        <p className="text-sm text-muted-foreground mt-1">
          共 {data?.total ?? 0} 条 trace
        </p>
      </div>

      {/* 过滤栏 */}
      <Card>
        <CardContent className="p-4 flex items-center gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              placeholder="用户 ID"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full h-9 pl-9 pr-3 rounded-md border border-input bg-transparent text-sm shadow-sm"
            />
          </div>
          <input
            placeholder="Agent 名"
            value={agentName}
            onChange={(e) => setAgentName(e.target.value)}
            className="h-9 max-w-xs px-3 rounded-md border border-input bg-transparent text-sm shadow-sm"
          />
        </CardContent>
      </Card>

      {/* 列表 */}
      <Card>
        <CardHeader>
          <CardTitle>Trace 列表</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {traces.map((t) => (
              <TraceRow key={t.trace_id} trace={t} />
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function TraceRow({ trace }: { trace: TraceSummary }) {
  const isError = trace.status === 'error';
  return (
    <Link
      to={`/traces/${trace.trace_id}`}
      className="flex items-center gap-4 p-4 hover:bg-accent transition-colors"
    >
      <div className={`w-2 h-2 rounded-full ${isError ? 'bg-red-500' : 'bg-green-500'}`} />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="font-mono text-sm truncate">{trace.trace_id}</span>
          <span className="text-xs text-muted-foreground">{trace.agent_name}</span>
          {trace.skill_name && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground">
              {trace.skill_name}
            </span>
          )}
        </div>
        <div className="text-xs text-muted-foreground mt-1">
          {trace.user_id && <>user: {trace.user_id} · </>}
          {trace.span_count} spans · {formatNumber(trace.total_tokens)} tokens · {formatCost(trace.total_cost_cny)}
        </div>
      </div>
      <div className="text-right">
        <div className="text-sm font-medium">{formatDuration(trace.duration_ms)}</div>
        <div className="text-xs text-muted-foreground">{formatRelativeTime(trace.start_time)}</div>
      </div>
      <ChevronRight className="w-4 h-4 text-muted-foreground" />
    </Link>
  );
}

// 占位 Input 已删除, 改用原生 <input> 或从 '@/components/ui/input' 导入 (Phase 8)
// 当前用原生 <input>:
//   <input className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm" ... />
