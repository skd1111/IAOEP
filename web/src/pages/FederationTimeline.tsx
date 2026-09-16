import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { TrendingUp, BarChart3 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi, type MockFederationTimelinePoint } from '@/lib/api';
import { cn } from '@/lib/utils';

/**
 * Federation Timeline — 跨租户聚合时间线图.
 *
 * Phase 7.6: 按 (dimension) 分线, 展示 noisy_value 随时间变化.
 * 多维聚合 (model × skill) 在同一图叠加显示.
 */
export function FederationTimeline() {
  const { data } = useQuery({
    queryKey: ['federation-timeline'],
    queryFn: mockApi.federationTimeline,
    refetchInterval: 30_000,
  });

  const points: MockFederationTimelinePoint[] = data ?? [];

  // Pivot: 按 timestamp 分组, 每行 = 一个时间点, 每列 = 一个 dimension
  const pivoted = useMemo(() => {
    const byTime = new Map<number, Record<string, number>>();
    points.forEach((p) => {
      const ts = new Date(p.timestamp).getTime();
      const row = byTime.get(ts) ?? {};
      row[p.dimension] = p.noisyValue;
      byTime.set(ts, row);
    });
    return Array.from(byTime.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([ts, dims]) => ({
        timestamp: new Date(ts).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }),
        ...dims,
      }));
  }, [points]);

  // 所有 dimension
  const dimensions = Array.from(new Set(points.map((p) => p.dimension)));
  const palette = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

  const [view, setView] = useState<'line' | 'bar'>('line');

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold flex items-center gap-2">
            <TrendingUp className="w-6 h-6" />
            Federation Timeline
          </h1>
          <p className="text-sm text-muted-foreground mt-1">
            多维聚合时间线 · noisy value 趋势 · ε-DP 保护
          </p>
        </div>
        <div className="flex items-center gap-1 rounded-md border border-border p-1">
          <button
            onClick={() => setView('line')}
            className={cn(
              'px-3 py-1 rounded text-sm',
              view === 'line' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'
            )}
          >
            Line
          </button>
          <button
            onClick={() => setView('bar')}
            className={cn(
              'px-3 py-1 rounded text-sm',
              view === 'bar' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'
            )}
          >
            <BarChart3 className="w-4 h-4 inline-block mr-1" />
            Bar
          </button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>
            多维聚合: {dimensions.length} dimensions × {pivoted.length} 时间点
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-96">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={pivoted}>
                <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
                <XAxis dataKey="timestamp" />
                <YAxis domain={['auto', 'auto']} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: 8,
                  }}
                  formatter={(value: number) => value.toFixed(3)}
                />
                <Legend />
                {dimensions.map((dim, i) => (
                  <Line
                    key={dim}
                    type="monotone"
                    dataKey={dim}
                    stroke={palette[i % palette.length]}
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    activeDot={{ r: 5 }}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>

      {/* 多维聚合矩阵 (Phase 7.6 新增) */}
      <MultiDimMatrix points={points} />
    </div>
  );
}

/**
 * 多维聚合矩阵 — Phase 7.6 新增
 * 按 (model × skill) 二维分组, 表格展示 noisy_value.
 */
function MultiDimMatrix({ points }: { points: MockFederationTimelinePoint[] }) {
  // Pivot: 提取所有 model × skill 组合
  const models = new Set<string>();
  const skills = new Set<string>();
  const matrix = new Map<string, Map<string, number[]>>();   // model → skill → values

  points.forEach((p) => {
    // dimension 格式: "model=qwen3-turbo,skill=code-review" (简化, Phase 7.6 只支持 2 维)
    const parts = p.dimension.split(',').reduce((acc, kv) => {
      const [k, v] = kv.split('=').map((s) => s.trim());
      if (k && v) acc[k] = v;
      return acc;
    }, {} as Record<string, string>);

    const model = parts.model || 'unknown';
    const skill = parts.skill || 'unknown';

    models.add(model);
    skills.add(skill);

    if (!matrix.has(model)) matrix.set(model, new Map());
    const skillMap = matrix.get(model)!;
    if (!skillMap.has(skill)) skillMap.set(skill, []);
    skillMap.get(skill)!.push(p.noisyValue);
  });

  const modelList = Array.from(models);
  const skillList = Array.from(skills);

  return (
    <Card>
      <CardHeader>
        <CardTitle>多维聚合矩阵 (model × skill)</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <thead>
              <tr>
                <th className="text-left p-2 border-b border-border text-muted-foreground font-medium">
                  Model \ Skill
                </th>
                {skillList.map((s) => (
                  <th key={s} className="p-2 border-b border-border text-muted-foreground font-medium">
                    {s}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {modelList.map((m) => (
                <tr key={m}>
                  <td className="p-2 border-b border-border font-medium">{m}</td>
                  {skillList.map((s) => {
                    const values = matrix.get(m)?.get(s) ?? [];
                    const avg = values.length > 0
                      ? values.reduce((a, b) => a + b, 0) / values.length
                      : null;
                    return (
                      <td key={s} className="p-2 border-b border-border text-center font-mono">
                        {avg !== null ? (
                          <span className="text-foreground">{avg.toFixed(3)}</span>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
