import { useMemo, useState, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, AlertCircle, CheckCircle2, Clock, Timer, Wrench, Coins,
  Search, Copy, Check, ChevronDown, ChevronUp, Download, ExternalLink,
  List, BarChart3,
} from 'lucide-react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { api, mockApi } from '@/lib/api';
import { buildSpanTree, type SpanNode, type StandardSpan } from '@/lib/types';
import { formatDuration, formatCost, formatNumber, cn } from '@/lib/utils';

/* ─── helpers ─── */

function flattenSpans(roots: SpanNode[]): StandardSpan[] {
  const out: StandardSpan[] = [];
  const walk = (n: SpanNode) => { out.push(n); n.children.forEach(walk); };
  roots.forEach(walk);
  return out;
}

function collectMatchingIds(roots: SpanNode[], q: string): Set<string> {
  const matched = new Set<string>();
  const ql = q.toLowerCase();
  const walk = (n: SpanNode) => {
    const hay = [n.span_name, n.agent_name, n.tool_name, n.llm_model, n.span_kind]
      .filter(Boolean).join(' ').toLowerCase();
    if (hay.includes(ql)) matched.add(n.span_id);
    n.children.forEach(walk);
  };
  roots.forEach(walk);
  // 同时加入匹配节点的祖先 ID，保证树结构可见
  const withAncestors = new Set<string>(matched);
  const addAncestors = (nodes: SpanNode[], parentIds: Set<string>) => {
    nodes.forEach((n) => {
      if (matched.has(n.span_id)) parentIds.forEach((id) => withAncestors.add(id));
      addAncestors(n.children, new Set([...parentIds, n.span_id]));
    });
  };
  addAncestors(roots, new Set());
  return withAncestors;
}

function highlight(text: string, q: string) {
  if (!q) return text;
  const i = text.toLowerCase().indexOf(q.toLowerCase());
  if (i < 0) return text;
  return (
    <>
      {text.slice(0, i)}
      <mark className="bg-yellow-500/30 rounded px-0.5">{text.slice(i, i + q.length)}</mark>
      {text.slice(i + q.length)}
    </>
  );
}

/* ─── main component ─── */

export function TraceDetail() {
  const { traceId } = useParams<{ traceId: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['trace', traceId],
    queryFn: () => api.getTraceDetail(traceId!),
    enabled: !!traceId,
    retry: 1,
  });

  const resolved = data ?? (isError ? mockApi.traceDetail(traceId!) : undefined);

  // ─ state ──
  const [search, setSearch] = useState('');
  const [view, setView] = useState<'tree' | 'waterfall'>('tree');
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [copied, setCopied] = useState(false);
  const [llmOpenId, setLlmOpenId] = useState<string | null>(null);

  // ── memoized ──
  const { tree, flatSpans, totalDuration, totalCost, totalTokens, rootSpan, minTs, maxTs } = useMemo(() => {
    if (!resolved) return { tree: [], flatSpans: [], totalDuration: 0, totalCost: 0, totalTokens: 0, rootSpan: null, minTs: 0, maxTs: 0 };
    const t = buildSpanTree(resolved.spans);
    const flat = flattenSpans(t);
    const dur = resolved.spans.reduce((m, s) => Math.max(m, s.duration_ms), 0);
    const cost = resolved.spans.reduce((a, s) => a + (s.cost_cny ?? 0), 0);
    const tokens = resolved.spans.reduce((a, s) => a + (s.llm_input_tokens ?? 0) + (s.llm_output_tokens ?? 0), 0);
    const root = flat.find((s) => !s.parent_span_id) ?? flat[0] ?? null;
    const starts = resolved.spans.map((s) => s.start_time_unix_nano);
    const ends = resolved.spans.map((s) => s.end_time_unix_nano);
    return {
      tree: t, flatSpans: flat, totalDuration: dur, totalCost: cost, totalTokens: tokens,
      rootSpan: root, minTs: Math.min(...starts), maxTs: Math.max(...ends),
    };
  }, [resolved]);

  const matchIds = useMemo(() => (search.trim() ? collectMatchingIds(tree, search.trim()) : null), [tree, search]);

  // ── actions ──
  const toggleId = useCallback((id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }, []);

  const expandAll = useCallback(() => {
    if (!resolved) return;
    setExpandedIds(new Set(resolved.spans.map((s) => s.span_id)));
  }, [resolved]);

  const collapseAll = useCallback(() => setExpandedIds(new Set()), []);

  const copyId = useCallback((text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }, []);

  const exportJson = useCallback(() => {
    if (!resolved) return;
    const blob = new Blob([JSON.stringify(resolved, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `trace-${resolved.trace_id}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
  }, [resolved]);

  // ── related traces (same session) ─
  const relatedTraces = useMemo(() => {
    if (!rootSpan?.session_id) return [];
    const all = mockApi.traces().traces;
    return all.filter((t) => t.session_id === rootSpan.session_id && t.trace_id !== traceId).slice(0, 5);
  }, [rootSpan, traceId]);

  // ── loading ──
  if (isLoading && !resolved) {
    return (
      <div className="p-6 space-y-4">
        <div className="flex items-center gap-3">
          <div className="h-5 w-5 rounded bg-muted animate-pulse" />
          <div className="space-y-2">
            <div className="h-5 w-48 rounded bg-muted animate-pulse" />
            <div className="h-4 w-72 rounded bg-muted animate-pulse" />
          </div>
        </div>
        <Card>
          <CardHeader><div className="h-5 w-20 rounded bg-muted animate-pulse" /></CardHeader>
          <CardContent>
            <div className="space-y-2">
              {Array.from({ length: 5 }).map((_, i) => (
                <div key={i} className="h-8 rounded bg-muted animate-pulse" style={{ marginLeft: `${i * 16}px` }} />
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  // ── error ──
  if (!resolved) {
    return (
      <div className="p-6">
        <Card>
          <CardContent className="p-8 text-center">
            <AlertCircle className="w-10 h-10 text-destructive mx-auto mb-3" />
            <p className="text-lg font-medium mb-1">无法加载 Trace</p>
            <p className="text-sm text-muted-foreground mb-4">
              Trace <code className="font-mono">{traceId}</code> 不存在或加载失败
            </p>
            <Link to="/traces" className="text-sm text-primary hover:underline">&larr; 返回链路列表</Link>
          </CardContent>
        </Card>
      </div>
    );
  }

  const timeRange = maxTs - minTs || 1;

  return (
    <div className="p-6 space-y-4">
      {/* ─ header ── */}
      <div className="flex items-center gap-3">
        <Link to="/traces" className="text-muted-foreground hover:text-foreground">
          <ArrowLeft className="w-5 h-5" />
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold font-mono truncate">{resolved.trace_id}</h1>
            <button
              type="button"
              onClick={() => copyId(resolved.trace_id)}
              className="text-muted-foreground hover:text-foreground transition-colors"
              title="复制 Trace ID"
            >
              {copied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
            </button>
          </div>
          <p className="text-sm text-muted-foreground">
            {resolved.spans.length} spans · {formatDuration(totalDuration)} · {formatNumber(totalTokens)} tokens · {formatCost(totalCost)}
          </p>
        </div>
        <button
          type="button"
          onClick={exportJson}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-md border border-border hover:bg-accent transition-colors"
          title="导出 JSON"
        >
          <Download className="w-3.5 h-3.5" /> 导出
        </button>
      </div>

      {/* ── metadata panel ─ */}
      <Card>
        <CardContent className="p-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-2 text-sm">
            {rootSpan?.agent_name && (
              <div><span className="text-muted-foreground">Agent</span><p className="font-medium">{rootSpan.agent_name}</p></div>
            )}
            {rootSpan?.user_id && (
              <div><span className="text-muted-foreground">User</span><p className="font-medium font-mono">{rootSpan.user_id}</p></div>
            )}
            {rootSpan?.session_id && (
              <div>
                <span className="text-muted-foreground">Session</span>
                <p className="font-medium font-mono">{rootSpan.session_id}</p>
              </div>
            )}
            {rootSpan?.skill_name && (
              <div><span className="text-muted-foreground">Skill</span><p className="font-medium">{rootSpan.skill_name}</p></div>
            )}
            {rootSpan?.attributes?.['deployment.region'] && (
              <div><span className="text-muted-foreground">Region</span><p className="font-medium">{rootSpan.attributes['deployment.region']}</p></div>
            )}
            {rootSpan?.attributes?.['deployment.env'] && (
              <div><span className="text-muted-foreground">Env</span><p className="font-medium">{rootSpan.attributes['deployment.env']}</p></div>
            )}
            {rootSpan?.attributes?.['agent.version'] && (
              <div><span className="text-muted-foreground">Version</span><p className="font-medium">{rootSpan.attributes['agent.version']}</p></div>
            )}
            <div>
              <span className="text-muted-foreground">Status</span>
              <p className={cn(
                'font-medium',
                rootSpan?.status === 'error' && 'text-red-500',
                rootSpan?.status === 'timeout' && 'text-amber-500',
                rootSpan?.status === 'success' && 'text-green-500',
              )}>{rootSpan?.status ?? 'unknown'}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* ─ toolbar ── */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px] max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            placeholder="搜索 span 名称、工具、模型…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full h-9 pl-9 pr-3 rounded-md border border-border bg-transparent text-sm"
          />
        </div>
        <div className="flex rounded-md border border-border overflow-hidden">
          <button
            type="button"
            onClick={() => setView('tree')}
            className={cn('flex items-center gap-1.5 px-3 py-1.5 text-xs transition-colors', view === 'tree' ? 'bg-accent font-medium' : 'hover:bg-accent/50')}
          >
            <List className="w-3.5 h-3.5" /> 树状
          </button>
          <button
            type="button"
            onClick={() => setView('waterfall')}
            className={cn('flex items-center gap-1.5 px-3 py-1.5 text-xs transition-colors border-l border-border', view === 'waterfall' ? 'bg-accent font-medium' : 'hover:bg-accent/50')}
          >
            <BarChart3 className="w-3.5 h-3.5" /> 时间轴
          </button>
        </div>
        <div className="flex gap-1">
          <button type="button" onClick={expandAll} className="flex items-center gap-1 px-2.5 py-1.5 text-xs rounded-md border border-border hover:bg-accent transition-colors">
            <ChevronDown className="w-3.5 h-3.5" /> 全部展开
          </button>
          <button type="button" onClick={collapseAll} className="flex items-center gap-1 px-2.5 py-1.5 text-xs rounded-md border border-border hover:bg-accent transition-colors">
            <ChevronUp className="w-3.5 h-3.5" /> 全部收起
          </button>
        </div>
      </div>

      {/* ── content ── */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle>{view === 'tree' ? 'Span 树' : '时间轴'}</CardTitle>
        </CardHeader>
        <CardContent>
          {view === 'tree' ? (
            <div className="space-y-1">
              {tree.map((root) => (
                <SpanTreeNode
                  key={root.span_id}
                  node={root}
                  search={search}
                  matchIds={matchIds}
                  expandedIds={expandedIds}
                  onToggle={toggleId}
                  llmOpenId={llmOpenId}
                  onLlmToggle={setLlmOpenId}
                  onCopy={copyId}
                />
              ))}
            </div>
          ) : (
            <WaterfallView spans={flatSpans} minTs={minTs} timeRange={timeRange} totalDuration={totalDuration} />
          )}
        </CardContent>
      </Card>

      {/* ── related traces ── */}
      {relatedTraces.length > 0 && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">同 Session 其他 Trace</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y divide-border">
              {relatedTraces.map((t) => (
                <Link key={t.trace_id} to={`/traces/${t.trace_id}`} className="flex items-center gap-3 p-3 hover:bg-accent transition-colors">
                  <div className={`w-2 h-2 rounded-full ${t.status === 'error' ? 'bg-red-500' : 'bg-green-500'}`} />
                  <span className="font-mono text-xs flex-1 truncate">{t.trace_id}</span>
                  <span className="text-xs text-muted-foreground">{t.agent_name}</span>
                  <span className="text-xs text-muted-foreground">{formatDuration(t.duration_ms)}</span>
                  <ExternalLink className="w-3.5 h-3.5 text-muted-foreground" />
                </Link>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/* ─── SpanTreeNode ─── */

interface TreeNodeProps {
  node: SpanNode;
  search: string;
  matchIds: Set<string> | null;
  expandedIds: Set<string>;
  onToggle: (id: string) => void;
  llmOpenId: string | null;
  onLlmToggle: (id: string | null) => void;
  onCopy: (text: string) => void;
}

function SpanTreeNode({ node, search, matchIds, expandedIds, onToggle, llmOpenId, onLlmToggle, onCopy }: TreeNodeProps) {
  const isError = node.status === 'error';
  const isTimeout = node.status === 'timeout';
  const isLLM = node.span_name === 'llm.call';
  const isTool = node.span_name === 'tool.execute';
  const hasAttributes = node.attributes && Object.keys(node.attributes).length > 0;
  const isExpanded = expandedIds.has(node.span_id);
  const isMatch = matchIds?.has(node.span_id);
  const isHidden = matchIds !== null && !matchIds.has(node.span_id);
  const showLlm = llmOpenId === node.span_id;

  const StatusIcon = isError ? AlertCircle : isTimeout ? Timer : CheckCircle2;
  const statusIconClass = cn('w-4 h-4 shrink-0', isError && 'text-red-500', isTimeout && 'text-amber-500', !isError && !isTimeout && 'text-green-500');

  if (isHidden) return null;

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-2 px-3 py-2 rounded-md hover:bg-accent transition-colors',
          isError && 'bg-red-500/10',
          isTimeout && 'bg-amber-500/10',
          isMatch && search && 'ring-1 ring-yellow-500/50',
        )}
        style={{ paddingLeft: `${node.depth * 16 + 12}px` }}
      >
        <StatusIcon className={statusIconClass} />
        <span className="font-mono text-sm font-medium">{highlight(node.span_name, search)}</span>
        {node.agent_name && <span className="text-xs text-muted-foreground">[{highlight(node.agent_name, search)}]</span>}
        {isLLM && node.llm_model && <span className="text-xs text-muted-foreground">{highlight(node.llm_model, search)}</span>}
        {isTool && node.tool_name && (
          <span className="text-xs inline-flex items-center gap-1 text-muted-foreground">
            <Wrench className="w-3 h-3" />{highlight(node.tool_name, search)}
          </span>
        )}
        {isLLM && (node.llm_input_tokens != null || node.llm_output_tokens != null) && (
          <span className="text-xs inline-flex items-center gap-1 text-muted-foreground">
            <Coins className="w-3 h-3" />
            {formatNumber((node.llm_input_tokens ?? 0) + (node.llm_output_tokens ?? 0))} tokens
            {node.cost_cny != null && <span>· {formatCost(node.cost_cny)}</span>}
          </span>
        )}
        {/* copy span id */}
        <button type="button" onClick={() => onCopy(node.span_id)} className="text-muted-foreground hover:text-foreground opacity-0 group-hover:opacity-100 transition-opacity" title="复制 Span ID">
          <Copy className="w-3 h-3" />
        </button>
        <span className="ml-auto text-xs text-muted-foreground flex items-center gap-1">
          <Clock className="w-3 h-3" />{formatDuration(node.duration_ms)}
        </span>
      </div>

      {/* error message */}
      {(isError || isTimeout) && node.error_message && (
        <div className="px-3 py-1.5 text-xs text-red-600 dark:text-red-400 bg-red-500/5 border-l-2 border-red-500/30" style={{ marginLeft: `${node.depth * 16 + 36}px` }}>
          {node.error_message}
        </div>
      )}

      {/* LLM input/output */}
      {isLLM && (node.llm_input || node.llm_output) && (
        <button
          type="button"
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
          style={{ marginLeft: `${node.depth * 16 + 36}px` }}
          onClick={() => onLlmToggle(showLlm ? null : node.span_id)}
        >
          <span className="text-[10px]">{showLlm ? '▼' : '▶'}</span>
          <span>LLM 内容</span>
        </button>
      )}
      {isLLM && showLlm && (
        <div className="mt-1 mb-2 space-y-2" style={{ marginLeft: `${node.depth * 16 + 36}px` }}>
          {node.llm_input && (
            <div className="rounded-md bg-blue-500/5 border border-blue-500/20 p-3 text-xs">
              <div className="text-blue-600 dark:text-blue-400 font-medium mb-1">Input</div>
              <pre className="whitespace-pre-wrap font-mono text-muted-foreground leading-relaxed">{node.llm_input}</pre>
            </div>
          )}
          {node.llm_output && (
            <div className="rounded-md bg-green-500/5 border border-green-500/20 p-3 text-xs">
              <div className="text-green-600 dark:text-green-400 font-medium mb-1">Output</div>
              <pre className="whitespace-pre-wrap font-mono text-muted-foreground leading-relaxed">{node.llm_output}</pre>
            </div>
          )}
        </div>
      )}

      {/* attributes */}
      {hasAttributes && (
        <button
          type="button"
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
          style={{ marginLeft: `${node.depth * 16 + 36}px` }}
          onClick={() => onToggle(node.span_id)}
        >
          <span className="text-[10px]">{isExpanded ? '▼' : '▶'}</span>
          <span>属性 ({Object.keys(node.attributes!).length})</span>
        </button>
      )}
      {hasAttributes && isExpanded && (
        <div className="ml-12 mt-1 mb-2 p-3 rounded-md bg-muted/50 text-xs" style={{ marginLeft: `${node.depth * 16 + 36}px` }}>
          <div className="grid grid-cols-2 gap-2">
            {Object.entries(node.attributes!).map(([k, v]) => (
              <div key={k}><span className="text-muted-foreground">{k}: </span><span className="font-mono">{v}</span></div>
            ))}
          </div>
        </div>
      )}

      {node.children.map((child) => (
        <SpanTreeNode key={child.span_id} node={child} search={search} matchIds={matchIds} expandedIds={expandedIds} onToggle={onToggle} llmOpenId={llmOpenId} onLlmToggle={onLlmToggle} onCopy={onCopy} />
      ))}
    </div>
  );
}

/* ─── WaterfallView ─── */

function WaterfallView({ spans, minTs, timeRange, totalDuration }: { spans: StandardSpan[]; minTs: number; timeRange: number; totalDuration: number }) {
  const sorted = useMemo(() => [...spans].sort((a, b) => a.start_time_unix_nano - b.start_time_unix_nano), [spans]);
  const ticks = 5;

  return (
    <div className="space-y-0.5">
      {/* header row */}
      <div className="flex items-center text-xs text-muted-foreground border-b border-border pb-1 mb-1">
        <div className="w-[200px] shrink-0 px-2 font-medium">Span</div>
        <div className="flex-1 relative h-5">
          {Array.from({ length: ticks + 1 }).map((_, i) => {
            const pct = (i / ticks) * 100;
            const ms = Math.round((i / ticks) * totalDuration);
            return (
              <div key={i} className="absolute top-0 text-[10px]" style={{ left: `${pct}%` }}>
                <div className="border-l border-border h-3" />
                <span className="text-muted-foreground">{formatDuration(ms)}</span>
              </div>
            );
          })}
        </div>
      </div>
      {/* span rows */}
      {sorted.map((s) => {
        const leftPct = ((s.start_time_unix_nano - minTs) / timeRange) * 100;
        const widthPct = Math.max((s.duration_ms / totalDuration) * 100, 0.5);
        const isErr = s.status === 'error';
        const isTmo = s.status === 'timeout';
        const barColor = isErr ? 'bg-red-500/70' : isTmo ? 'bg-amber-500/70' : 'bg-blue-500/60';
        return (
          <div key={s.span_id} className="flex items-center group">
            <div className="w-[200px] shrink-0 px-2 py-1.5 truncate text-xs font-mono" title={`${s.span_name}${s.tool_name ? ` · ${s.tool_name}` : ''}${s.llm_model ? ` · ${s.llm_model}` : ''}`}>
              {s.span_name}
              {s.tool_name && <span className="text-muted-foreground ml-1">· {s.tool_name}</span>}
              {s.llm_model && <span className="text-muted-foreground ml-1">· {s.llm_model}</span>}
            </div>
            <div className="flex-1 relative h-6">
              <div
                className={cn('absolute top-1 h-4 rounded-sm transition-opacity group-hover:opacity-100 opacity-80', barColor)}
                style={{ left: `${leftPct}%`, width: `${widthPct}%` }}
                title={`${s.span_name} — ${formatDuration(s.duration_ms)}`}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
