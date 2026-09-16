import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, AlertCircle, CheckCircle2, Clock } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { mockApi } from '@/lib/api';
import { buildSpanTree, type SpanNode } from '@/lib/types';
import { formatDuration, formatCost, cn } from '@/lib/utils';

/**
 * Trace 详情页 — 树状展示 span + 每个 span 的属性
 */
export function TraceDetail() {
  const { traceId } = useParams<{ traceId: string }>();

  const { data } = useQuery({
    queryKey: ['trace', traceId],
    queryFn: () => mockApi.traceDetail(traceId!),
    enabled: !!traceId,
  });

  if (!data) {
    return <div className="p-6 text-muted-foreground">Loading...</div>;
  }

  const tree = buildSpanTree(data.spans);
  const totalDuration = Math.max(...data.spans.map((s) => s.duration_ms));
  const totalCost = data.spans.reduce((acc, s) => acc + (s.cost_cny ?? 0), 0);
  const totalTokens = data.spans.reduce(
    (acc, s) => acc + (s.llm_input_tokens ?? 0) + (s.llm_output_tokens ?? 0),
    0
  );

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center gap-3">
        <Link to="/traces" className="text-muted-foreground hover:text-foreground">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div>
          <h1 className="text-xl font-semibold font-mono">{data.trace_id}</h1>
          <p className="text-sm text-muted-foreground">
            {data.spans.length} spans · {formatDuration(totalDuration)} · {totalTokens} tokens · {formatCost(totalCost)}
          </p>
        </div>
      </div>

      {/* Trace 树 */}
      <Card>
        <CardHeader>
          <CardTitle>Span 树</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-1">
            {tree.map((root) => (
              <SpanTreeNode key={root.span_id} node={root} />
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function SpanTreeNode({ node }: { node: SpanNode }) {
  const isError = node.status === 'error';
  const isLLM = node.span_name === 'llm.call';
  const isTool = node.span_name === 'tool.execute';

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2 rounded-md hover:bg-accent',
          isError && 'bg-red-500/10'
        )}
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
      >
        {isError ? (
          <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
        ) : (
          <CheckCircle2 className="w-4 h-4 text-green-500 shrink-0" />
        )}
        <span className="font-mono text-sm font-medium">{node.span_name}</span>
        {node.agent_name && (
          <span className="text-xs text-muted-foreground">[{node.agent_name}]</span>
        )}
        {isLLM && node.llm_model && (
          <span className="text-xs text-muted-foreground">{node.llm_model}</span>
        )}
        {isTool && node.tool_name && (
          <span className="text-xs text-muted-foreground">{node.tool_name}</span>
        )}
        <span className="ml-auto text-xs text-muted-foreground flex items-center gap-1">
          <Clock className="w-3 h-3" />
          {formatDuration(node.duration_ms)}
        </span>
      </div>

      {/* Attributes 详情 (Phase 1 简化展示) */}
      {node.depth === 0 && (
        <div
          className="ml-12 mt-1 mb-2 p-3 rounded-md bg-muted/50 text-xs"
          style={{ marginLeft: `${node.depth * 16 + 36}px` }}
        >
          <div className="grid grid-cols-2 gap-2">
            {Object.entries(node.attributes ?? {}).slice(0, 8).map(([k, v]) => (
              <div key={k}>
                <span className="text-muted-foreground">{k}: </span>
                <span className="font-mono">{v}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {node.children.map((child) => (
        <SpanTreeNode key={child.span_id} node={child} />
      ))}
    </div>
  );
}
