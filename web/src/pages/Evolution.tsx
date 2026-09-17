import { useQuery } from '@tanstack/react-query';
import { GitCommit, Undo2, CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockEvolutionLog } from '@/lib/api';
import { formatRelativeTime } from '@/lib/utils';

/**
 * 演进历史 — 时间线展示所有已应用的变更.
 */
export function Evolution() {
  const { data } = useQuery({
    queryKey: ['evolution'],
    queryFn: mockApi.evolutionLogs,
    refetchInterval: 10_000,
  });

  const logs: MockEvolutionLog[] = data ?? [];

  return (
    <div className="p-6 space-y-4">
      <div>
        <h1 className="text-2xl font-semibold flex items-center gap-2">
          <GitCommit className="w-6 h-6" />
          演进历史
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          共 {logs.length} 条变更记录 (合规审计可追溯)
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>变更时间线</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="relative border-l border-border ml-4 space-y-6 py-2">
            {logs.map((log) => (
              <li key={log.id} className="ml-6">
                <span className="absolute -left-2 flex items-center justify-center w-4 h-4 bg-primary text-primary-foreground rounded-full">
                  <GitCommit className="w-2.5 h-2.5" />
                </span>

                <div className="space-y-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-medium">{log.target}</span>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-secondary">
                      {log.type}
                    </span>
                    <OutcomeBadge outcome={log.outcome} />
                  </div>

                  {/* before / after 对比 */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div className="p-2 rounded-md bg-red-500/5 border border-red-500/20">
                      <div className="text-red-500 font-medium mb-1">变更前</div>
                      <pre className="font-mono text-muted-foreground whitespace-pre-wrap">
                        {JSON.stringify(log.beforeConfig, null, 2)}
                      </pre>
                    </div>
                    <div className="p-2 rounded-md bg-green-500/5 border border-green-500/20">
                      <div className="text-green-500 font-medium mb-1">变更后</div>
                      <pre className="font-mono text-muted-foreground whitespace-pre-wrap">
                        {JSON.stringify(log.afterConfig, null, 2)}
                      </pre>
                    </div>
                  </div>

                  {log.rolledBackAt && (
                    <div className="flex items-center gap-2 text-xs text-red-500">
                      <Undo2 className="w-3 h-3" />
                      已回滚 by {log.rolledBackBy} ·{' '}
                      {formatRelativeTime(new Date(log.rolledBackAt).getTime())}
                    </div>
                  )}

                  <div className="text-xs text-muted-foreground">
                    by {log.appliedBy} · {formatRelativeTime(new Date(log.appliedAt).getTime())}
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
    </div>
  );
}

function OutcomeBadge({ outcome }: { outcome: 'SUCCESS' | 'FAILED' | 'REVERTED' }) {
  const config = {
    SUCCESS: { color: 'text-green-500 bg-green-500/10',  label: '生效',      icon: CheckCircle2 },
    FAILED:  { color: 'text-red-500 bg-red-500/10',      label: '失败',      icon: Undo2 },
    REVERTED:{ color: 'text-orange-500 bg-orange-500/10', label: '已回滚', icon: Undo2 },
  }[outcome];
  const Icon = config.icon;
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full flex items-center gap-1 ${config.color}`}>
      <Icon className="w-3 h-3" />
      {config.label}
    </span>
  );
}
