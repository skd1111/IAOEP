import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, XCircle } from 'lucide-react';
import { Radar, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer } from 'recharts';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi } from '@/lib/api';
import { cn } from '@/lib/utils';

/**
 * 评测详情 — 维度雷达图 + 失败 case 列表 + 跳转 trace
 */
export function EvaluationDetail() {
  const { jobId } = useParams<{ jobId: string }>();

  const { data } = useQuery({
    queryKey: ['evaluation', jobId],
    queryFn: () => mockApi.evaluationDetail(jobId!),
    enabled: !!jobId,
  });

  if (!data) {
    return <div className="p-6 text-muted-foreground">Loading...</div>;
  }

  // 雷达图数据 (维度名 + score)
  const radarData = data.dimensions.map((d) => ({
    dimension: d.name,
    score: d.score * 100,  // 转为百分比
    fullMark: 100,
  }));

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Link to="/evaluations" className="text-muted-foreground hover:text-foreground">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-semibold">评测详情</h1>
          <p className="text-sm text-muted-foreground">
            {data.agentVersion} · {data.id.slice(0, 8)} · overall = {data.overall.toFixed(3)}
          </p>
        </div>
      </div>

      {/* 维度雷达图 */}
      <Card>
        <CardHeader>
          <CardTitle>维度评分</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-80">
            <ResponsiveContainer width="100%" height="100%">
              <RadarChart data={radarData}>
                <PolarGrid />
                <PolarAngleAxis dataKey="dimension" />
                <PolarRadiusAxis angle={90} domain={[0, 100]} />
                <Radar
                  name="Score"
                  dataKey="score"
                  stroke="#3b82f6"
                  fill="#3b82f6"
                  fillOpacity={0.3}
                />
              </RadarChart>
            </ResponsiveContainer>
          </div>
          <div className="grid grid-cols-3 gap-4 mt-4">
            {data.dimensions.map((d) => (
              <div key={d.name} className="p-3 rounded-md bg-muted/50">
                <div className="text-xs text-muted-foreground">{d.name}</div>
                <div className="text-lg font-semibold">{(d.score * 100).toFixed(1)}%</div>
                <div className="text-xs text-muted-foreground mt-1">{d.reason}</div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Case 结果列表 */}
      <Card>
        <CardHeader>
          <CardTitle>Case 结果 ({data.cases.length})</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border">
            {data.cases.map((c, i) => (
              <div
                key={i}
                className={cn(
                  'flex items-start gap-3 p-4',
                  !c.passed && 'bg-red-500/5'
                )}
              >
                {c.passed ? (
                  <CheckCircle2 className="w-4 h-4 text-green-500 mt-1 shrink-0" />
                ) : (
                  <XCircle className="w-4 h-4 text-red-500 mt-1 shrink-0" />
                )}
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-mono truncate">{c.input}</div>
                  <div className="text-xs text-muted-foreground mt-1">
                    期望: <span className="font-mono">{c.expected}</span>
                  </div>
                  {c.reason && (
                    <div className="text-xs text-muted-foreground mt-1">原因: {c.reason}</div>
                  )}
                </div>
                <div className="text-sm font-semibold">
                  {c.score !== undefined ? c.score.toFixed(2) : '-'}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
