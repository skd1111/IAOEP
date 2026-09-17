/**
 * IAOEP 前后端共享类型定义
 *
 * 对应 ClickHouse iaoep.traces 表 + Kafka StandardSpan JSON 格式.
 */

export interface StandardSpan {
  tenant_id: string;
  trace_id: string;
  span_id: string;
  parent_span_id?: string;
  span_name: string;
  span_kind: string;
  start_time_unix_nano: number;
  end_time_unix_nano: number;
  duration_ms: number;

  // Agent
  agent_name?: string;
  session_id?: string;
  user_id?: string;
  skill_name?: string;

  // LLM
  llm_system?: string;
  llm_model?: string;
  llm_input_tokens?: number;
  llm_output_tokens?: number;
  llm_input?: string;   // prompt / messages 原文
  llm_output?: string;  // 模型返回原文

  // Tool
  tool_name?: string;
  tool_call_id?: string;
  tool_error_type?: string;

  // 成本
  cost_cny?: number;

  // 状态
  status: 'success' | 'error' | 'timeout' | 'unset';
  error_message?: string;

  // 灵活扩展
  tags?: Record<string, string>;
  attributes?: Record<string, string>;
}

export interface TraceSummary {
  trace_id: string;
  agent_name: string;
  user_id?: string;
  session_id?: string;
  skill_name?: string;
  start_time: number;
  end_time?: number;
  duration_ms: number;
  span_count: number;
  total_tokens: number;
  total_cost_cny: number;
  status: 'success' | 'error' | 'timeout';
}

export interface DashboardMetrics {
  total_traces_24h: number;
  total_spans_24h: number;
  total_tokens_24h: number;
  total_cost_24h_cny: number;
  p50_latency_ms: number;
  p95_latency_ms: number;
  p99_latency_ms: number;
  error_rate: number;
  active_agents: number;
}

/** Dashboard 自进化洞察卡片数据 */
export interface EvolutionInsight {
  autoDiscovered: number;       // 本周自动发现的问题数
  autoRollbacks: number;        // 自动回滚次数
  pendingApprovals: number;     // 待审批建议
  successRate: number;          // 自进化成功率 (0-1)
  costSaved: number;            // 节省成本 (CNY)
  latencyImproved: number;      // 延迟改善百分比
}

export interface TraceListResponse {
  total: number;
  traces: TraceSummary[];
}

export interface TraceDetailResponse {
  trace_id: string;
  spans: StandardSpan[];
}

export type SpanNode = StandardSpan & {
  children: SpanNode[];
  depth: number;
};

export function buildSpanTree(spans: StandardSpan[]): SpanNode[] {
  const byId = new Map<string, SpanNode>();
  spans.forEach((s) => byId.set(s.span_id, { ...s, children: [], depth: 0 }));

  const roots: SpanNode[] = [];
  byId.forEach((node) => {
    if (node.parent_span_id && byId.has(node.parent_span_id)) {
      const parent = byId.get(node.parent_span_id)!;
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });

  // 计算 depth
  const setDepth = (node: SpanNode, depth: number) => {
    node.depth = depth;
    node.children.forEach((c) => setDepth(c, depth + 1));
  };
  roots.forEach((r) => setDepth(r, 0));

  return roots;
}
