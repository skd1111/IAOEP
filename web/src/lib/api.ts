import type { DashboardMetrics, EvolutionInsight, TraceListResponse, TraceDetailResponse } from './types';

const API_BASE = '/api/v1';

/**
 * IAOEP Web API client.
 *
 * 通过 Vite proxy 代理到 backend (默认 http://localhost:8081).
 */

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    throw new Error(`API ${path} failed: ${res.status} ${res.statusText}`);
  }
  return res.json();
}

export const api = {
  /** Dashboard 顶部 metrics */
  getDashboardMetrics: () =>
    fetchJson<DashboardMetrics>('/dashboard/metrics'),

  /** trace 列表 (分页 + 过滤) */
  listTraces: (params: {
    user_id?: string;
    agent_name?: string;
    status?: string;
    from?: number;
    to?: number;
    limit?: number;
    cursor?: string;
  }) => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') search.set(k, String(v));
    });
    return fetchJson<TraceListResponse>(`/traces?${search}`);
  },

  /** trace 详情 (含所有 spans) */
  getTraceDetail: (traceId: string) =>
    fetchJson<TraceDetailResponse>(`/traces/${traceId}`),

  /** Dashboard 自进化洞察 */
  getEvolutionInsight: () =>
    fetchJson<EvolutionInsight>('/dashboard/evolution-insight'),

  /** 健康检查 */
  health: () => fetchJson<{ status: string }>('/health'),

  // ============================================================
  // Phase 3 mock (PR 21 补全)
  // ============================================================

  approveSuggestion: (_id: string): Promise<void> =>
    new Promise((resolve) => setTimeout(resolve, 300)),

  rejectSuggestion: (_id: string): Promise<void> =>
    new Promise((resolve) => setTimeout(resolve, 300)),

  evolutionLogs: (): MockEvolutionLog[] => [
    {
      id: 'elog-1',
      type: 'prompt_change',
      target: 'CustomerServiceAgent.chat()',
      beforeConfig: { systemPrompt: '你是客服助手' },
      afterConfig: { systemPrompt: '你是客服助手. 当前用户: ... 简洁回答' },
      appliedBy: 'analyst-agent',
      appliedAt: new Date(Date.now() - 86400_000).toISOString(),
      outcome: 'SUCCESS',
    },
    {
      id: 'elog-2',
      type: 'route_change',
      target: 'LLM.router',
      beforeConfig: { primary: 'qwen3-max', fallback: 'qwen3-turbo' },
      afterConfig: { primary: 'qwen3-max', fallback: 'qwen3-turbo', simple_query_route: 'qwen3-turbo' },
      appliedBy: 'admin',
      appliedAt: new Date(Date.now() - 3 * 86400_000).toISOString(),
      outcome: 'SUCCESS',
    },
    {
      id: 'elog-3',
      type: 'model_swap',
      target: 'LLM.model',
      beforeConfig: { model: 'qwen3-turbo' },
      afterConfig: { model: 'qwen3-max' },
      appliedBy: 'admin',
      appliedAt: new Date(Date.now() - 7 * 86400_000).toISOString(),
      outcome: 'REVERTED',
      rolledBackAt: new Date(Date.now() - 5 * 86400_000).toISOString(),
      rolledBackBy: 'admin',
      note: '切换后 P99 延迟从 800ms 增至 2.1s, 已回滚',
    },
  ],

  // ============================================================
  // Phase 7: Federation mock
  // ============================================================

  federation: (): MockFederationAggregate[] => [
    {
      id: 'fed-1',
      name: 'model_accuracy',
      dimension: 'model=qwen3-turbo',
      noisyValue: 0.873,
      trueValue: 0.893,
      sampleSize: 3,
      sensitivity: 0.333,
      epsilon: 1.0,
      createdAt: new Date(Date.now() - 86400_000).toISOString(),
      tenantHashes: ['1a2b3c4d', '5e6f7g8h', '9i0j1k2l'],
    },
    {
      id: 'fed-2',
      name: 'model_accuracy',
      dimension: 'model=qwen3-max',
      noisyValue: 0.917,
      trueValue: 0.928,
      sampleSize: 5,
      sensitivity: 0.2,
      epsilon: 1.0,
      createdAt: new Date(Date.now() - 2 * 86400_000).toISOString(),
      tenantHashes: ['1a2b3c4d', '5e6f7g8h', '9i0j1k2l', 'mnopqrst', 'uvwxyzab'],
    },
    {
      id: 'fed-3',
      name: 'p99_latency_ms',
      dimension: 'model=qwen3-max',
      noisyValue: 1247.8,
      trueValue: 1180.2,
      sampleSize: 8,
      sensitivity: 0.125,
      epsilon: 1.0,
      createdAt: new Date(Date.now() - 3600_000).toISOString(),
      tenantHashes: ['1a2b3c4d', '5e6f7g8h', '9i0j1k2l', 'mnopqrst', 'uvwxyzab', 'cdefghij', 'klmnopqr', 'stuvwxyz'],
    },
  ],

  triggerFederation: (_req: {
    name: string;
    dimension: string;
    tenantValues: Record<string, number>;
    epsilon: number;
  }): Promise<void> =>
    new Promise((resolve) => setTimeout(resolve, 500)),

  federationTimeline: (): MockFederationTimelinePoint[] => {
    const dims = [
      'model=qwen3-turbo,skill=code-review',
      'model=qwen3-turbo,skill=data-analysis',
      'model=qwen3-max,skill=code-review',
      'model=qwen3-max,skill=data-analysis',
    ];
    const out: MockFederationTimelinePoint[] = [];
    const baseTs = Date.now() - 14 * 86400_000;
    for (let day = 0; day < 14; day++) {
      dims.forEach((dim, i) => {
        const base = dim.includes('qwen3-max') ? 0.92 : 0.85;
        const noise = (Math.random() - 0.5) * 0.05;
        out.push({
          timestamp: new Date(baseTs + day * 86400_000).toISOString(),
          dimension: dim,
          noisyValue: parseFloat((base + noise + i * 0.005).toFixed(4)),
          sampleSize: 5 + Math.floor(Math.random() * 5),
        });
      });
    }
    return out;
  },
};

// ============================================================
// Phase 2 mock 数据类型 (backend API 接通后替换)
// ============================================================

export interface MockEvaluation {
  id: string;
  agentVersion: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
  progress: number;
  totalCases: number;
  completedCases: number;
  score?: number;
  duration?: number;
  createdAt: number;
}

export interface MockEvaluationDetail extends MockEvaluation {
  overall: number;
  dimensions: Array<{ name: string; score: number; reason: string }>;
  cases: Array<{
    input: string;
    expected: string;
    passed: boolean;
    score?: number;
    reason?: string;
  }>;
}

export interface MockRegression {
  id: string;
  baselineVersion: string;
  candidateVersion: string;
  passed: boolean;
  delta: number;
  threshold: number;
  createdAt: number;
}

export interface MockDataset {
  id: string;
  name: string;
  version: string;
  isActive: boolean;
  caseCount: number;
  createdAt: number;
  updatedAt: number;
}

export interface MockSuggestion {
  id: string;
  type: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  target: string;
  description: string;
  expectedRoi: number;
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'DEPLOYED' | 'REVERTED';
  createdAt: number;
}

export interface MockEvolutionLog {
  id: string;
  type: string;
  target: string;
  beforeConfig: Record<string, unknown>;
  afterConfig: Record<string, unknown>;
  appliedBy: string;
  appliedAt: string;
  rolledBackAt?: string;
  rolledBackBy?: string;
  outcome: 'SUCCESS' | 'FAILED' | 'REVERTED';
  note?: string;
}

export interface MockFederationAggregate {
  id: string;
  name: string;                    // e.g. "model_accuracy"
  dimension: string;               // e.g. "model=qwen3-turbo"
  noisyValue: number;              // DP-noised (对外)
  trueValue?: number;              // 真实值 (仅 Owner 可见)
  sampleSize: number;              // 参与的租户数
  sensitivity: number;
  epsilon: number;                 // 隐私预算
  createdAt: string;               // ISO datetime
  tenantHashes: string[];          // 租户 ID hash 列表
}

export interface MockFederationTimelinePoint {
  timestamp: string;               // ISO datetime
  dimension: string;               // e.g. "model=qwen3-turbo,skill=code-review"
  noisyValue: number;
  sampleSize: number;
}

export interface MockFederationAlert {
  id: string;
  dimension: string;
  metric: string;
  latestValue: number;
  historicalAvg: number;
  deviation: number;
  threshold: number;
  severity: 'warning' | 'critical';
  detectedAt: string;
  status: 'open' | 'acknowledged' | 'resolved';
  message: string;
}

// Phase 1 mock: backend 还没实现,先用假数据
export const mockApi = {
  metrics: (): DashboardMetrics => ({
    total_traces_24h: 1247,
    total_spans_24h: 3892,
    total_tokens_24h: 1_256_400,
    total_cost_24h_cny: 18.42,
    p50_latency_ms: 245,
    p95_latency_ms: 1280,
    p99_latency_ms: 3400,
    error_rate: 0.023,
    active_agents: 12,
  }),

  evolutionInsight: (): EvolutionInsight => ({
    autoDiscovered: 7,
    autoRollbacks: 2,
    pendingApprovals: 3,
    successRate: 0.86,
    costSaved: 42.5,
    latencyImproved: 18.3,
  }),

  traces: (): TraceListResponse => ({
    total: 1247,
    traces: Array.from({ length: 20 }).map((_, i) => ({
      trace_id: `trace-${(Date.now() - i * 60000).toString(16)}-${i}`,
      agent_name: i % 3 === 0 ? 'customer-service' : i % 3 === 1 ? 'code-review' : 'data-analysis',
      user_id: `user-${i % 5}`,
      session_id: i < 3 ? 'sess-abc123' : `sess-${i % 10}`,
      skill_name: i % 2 === 0 ? 'general' : 'expert',
      start_time: Date.now() - i * 60000,
      duration_ms: 200 + Math.floor(Math.random() * 2000),
      span_count: 3 + Math.floor(Math.random() * 8),
      total_tokens: 500 + Math.floor(Math.random() * 3000),
      total_cost_cny: Math.random() * 0.5,
      status: Math.random() < 0.05 ? 'error' : 'success',
    })),
  }),

  traceDetail: (traceId: string): TraceDetailResponse => ({
    trace_id: traceId,
    spans: [
      {
        tenant_id: 'demo-tenant',
        trace_id: traceId,
        span_id: 'span-1',
        span_name: 'agent.run',
        span_kind: 'INTERNAL',
        start_time_unix_nano: Date.now() * 1_000_000,
        end_time_unix_nano: (Date.now() + 2500) * 1_000_000,
        duration_ms: 2500,
        agent_name: 'customer-service',
        skill_name: 'general',
        user_id: 'alice',
        session_id: 'sess-abc123',
        llm_input_tokens: 120,
        llm_output_tokens: 80,
        cost_cny: 0.005,
        status: 'success',
        attributes: {
          'user.locale': 'zh-CN',
          'agent.version': 'v1.2.0',
          'deployment.region': 'cn-beijing',
          'deployment.env': 'production',
        },
      },
      {
        tenant_id: 'demo-tenant',
        trace_id: traceId,
        span_id: 'span-2',
        parent_span_id: 'span-1',
        span_name: 'llm.call',
        span_kind: 'CLIENT',
        start_time_unix_nano: (Date.now() + 100) * 1_000_000,
        end_time_unix_nano: (Date.now() + 700) * 1_000_000,
        duration_ms: 600,
        llm_system: 'openai',
        llm_model: 'qwen3-turbo',
        llm_input_tokens: 120,
        llm_output_tokens: 80,
        cost_cny: 0.002,
        status: 'success',
        llm_input: 'System: 你是客服助手。请简洁、友好地回答用户问题。\nUser: 你们的退货政策是什么？',
        llm_output: '我们的退货政策如下：\n1. 购买后 30 天内可申请退货\n2. 商品需保持原包装和未使用状态\n3. 退款将在 5-7 个工作日内原路返回\n\n如需退货，请提供订单号，我来帮您处理。',
        attributes: {
          'llm.temperature': '0.7',
          'llm.max_tokens': '2048',
        },
      },
      {
        tenant_id: 'demo-tenant',
        trace_id: traceId,
        span_id: 'span-3',
        parent_span_id: 'span-1',
        span_name: 'tool.execute',
        span_kind: 'INTERNAL',
        start_time_unix_nano: (Date.now() + 750) * 1_000_000,
        end_time_unix_nano: (Date.now() + 820) * 1_000_000,
        duration_ms: 70,
        tool_name: 'get_current_time',
        status: 'success',
        attributes: {
          'tool.provider': 'builtin',
          'tool.cache_hit': 'true',
        },
      },
      {
        tenant_id: 'demo-tenant',
        trace_id: traceId,
        span_id: 'span-4',
        parent_span_id: 'span-1',
        span_name: 'llm.call',
        span_kind: 'CLIENT',
        start_time_unix_nano: (Date.now() + 850) * 1_000_000,
        end_time_unix_nano: (Date.now() + 2350) * 1_000_000,
        duration_ms: 1500,
        llm_system: 'openai',
        llm_model: 'qwen3-max',
        llm_input_tokens: 350,
        llm_output_tokens: 200,
        cost_cny: 0.003,
        status: 'timeout',
        error_message: 'LLM 响应超时 (1500ms > 1000ms 阈值)',
        llm_input: 'System: 你是客服助手。当前用户: alice (VIP)\nUser: 我的订单 #20260917-001 什么时候发货？我已经等了 3 天了。\nContext: 用户最近一次交互中查询了退货政策。',
        llm_output: '[超时 - 无响应]',
        attributes: {
          'llm.timeout_ms': '1000',
          'llm.retry_count': '2',
        },
      },
      {
        tenant_id: 'demo-tenant',
        trace_id: traceId,
        span_id: 'span-5',
        parent_span_id: 'span-4',
        span_name: 'tool.execute',
        span_kind: 'INTERNAL',
        start_time_unix_nano: (Date.now() + 2400) * 1_000_000,
        end_time_unix_nano: (Date.now() + 2480) * 1_000_000,
        duration_ms: 80,
        tool_name: 'search_knowledge_base',
        status: 'error',
        error_message: '知识库连接失败: ECONNREFUSED 10.0.1.5:6379',
        attributes: {
          'tool.endpoint': 'http://10.0.1.5:6379',
          'tool.retry': '3',
          'error.code': 'ECONNREFUSED',
        },
      },
    ],
  }),

  // ============================================================
  // Phase 2: 评测 / 回归 / Dataset
  // ============================================================

  evaluations: (): MockEvaluation[] =>
    Array.from({ length: 12 }).map((_, i) => {
      const statuses: MockEvaluation['status'][] = ['completed', 'running', 'failed', 'completed', 'completed'];
      const status = i < 5 ? statuses[i] : statuses[Math.floor(Math.random() * statuses.length)];
      return {
        id: `eval-${i}-${Date.now().toString(16)}`,
        agentVersion: i % 2 === 0 ? 'v1.0' : 'v1.1-rc1',
        status,
        progress: status === 'completed' ? 1.0 : Math.random(),
        totalCases: 50,
        completedCases: status === 'completed' ? 50 : Math.floor(Math.random() * 50),
        score: status === 'completed' ? 0.7 + Math.random() * 0.2 : undefined,
        duration: status === 'completed' ? 30000 + Math.random() * 60000 : undefined,
        createdAt: Date.now() - i * 3600_000,
      };
    }),

  evaluationDetail: (jobId: string): MockEvaluationDetail => ({
    id: jobId,
    agentVersion: 'v1.0',
    status: 'completed',
    progress: 1.0,
    totalCases: 5,
    completedCases: 5,
    score: 0.85,
    duration: 45000,
    createdAt: Date.now(),
    overall: 0.85,
    dimensions: [
      { name: 'accuracy', score: 0.92, reason: '答案准确率 92%, 4/5 完全匹配 expected_output' },
      { name: 'style', score: 0.80, reason: '风格统一, 但个别答案略口语化' },
      { name: 'safety', score: 1.00, reason: '所有输出无敏感内容' },
      { name: 'cost', score: 0.75, reason: '平均成本 ¥0.03, 部分 case 超阈值' },
    ],
    cases: [
      { input: '什么是 IAOEP?', expected: '开源的 Agent 可观测平台', passed: true, score: 0.95 },
      { input: '列出 IAOEP 的核心特性', expected: 'OTLP接入、AI评测、自进化', passed: true, score: 0.90 },
      { input: '如何接入千问?', expected: 'OpenAI 兼容端点', passed: true, score: 0.85 },
      { input: 'IAOEP 的 license?', expected: 'MIT', passed: true, score: 1.00 },
      { input: 'IAOEP 支持哪些 Agent 框架?', expected: '任意', passed: false, score: 0.45, reason: '答案说"任意"但实际需要具体框架名' },
    ],
  }),

  regressions: (): MockRegression[] => [
    { id: 'reg-1', baselineVersion: 'v1.0', candidateVersion: 'v1.1-rc1', passed: true,  delta:  0.03, threshold: 0.05, createdAt: Date.now() - 1 * 3600_000 },
    { id: 'reg-2', baselineVersion: 'v1.0', candidateVersion: 'v1.1-rc2', passed: false, delta: -0.08, threshold: 0.05, createdAt: Date.now() - 4 * 3600_000 },
    { id: 'reg-3', baselineVersion: 'v0.9', candidateVersion: 'v1.0',  passed: true,  delta:  0.12, threshold: 0.05, createdAt: Date.now() - 24 * 3600_000 },
  ],

  datasets: (): MockDataset[] => [
    { id: 'ds-1', name: 'code-review',  version: 'v1', isActive: true,  caseCount: 50, createdAt: Date.now() - 7 * 86400_000,  updatedAt: Date.now() - 86400_000 },
    { id: 'ds-2', name: 'data-analysis', version: 'v1', isActive: true,  caseCount: 30, createdAt: Date.now() - 14 * 86400_000, updatedAt: Date.now() - 2 * 86400_000 },
    { id: 'ds-3', name: 'general',       version: 'v1', isActive: true,  caseCount: 100, createdAt: Date.now() - 30 * 86400_000, updatedAt: Date.now() - 7 * 86400_000 },
  ],

  // ============================================================
  // Phase 3 mock
  // ============================================================

  suggestions: (): MockSuggestion[] => [
    {
      id: 'sug-1',
      type: 'prompt_change',
      riskLevel: 'MEDIUM',
      target: 'CustomerServiceAgent.chat()',
      description: '检测到 5 次格式错误 (LLM 输出不符合 schema), 建议 prompt 增加格式示例',
      expectedRoi: 0.25,
      status: 'PROPOSED',
      createdAt: Date.now() - 3 * 3600_000,
    },
    {
      id: 'sug-2',
      type: 'model_swap',
      riskLevel: 'HIGH',
      target: 'LLM.model',
      description: '检测到 12 次准确率低, 建议切换到 qwen3-max',
      expectedRoi: 0.30,
      status: 'PROPOSED',
      createdAt: Date.now() - 5 * 3600_000,
    },
    {
      id: 'sug-3',
      type: 'param_tune',
      riskLevel: 'LOW',
      target: 'AgentLoop.maxTurns',
      description: '检测到 3 次超时, 建议降低 maxTurns 从 10 到 6',
      expectedRoi: 0.15,
      status: 'PROPOSED',
      createdAt: Date.now() - 1 * 3600_000,
    },
    {
      id: 'sug-4',
      type: 'route_change',
      riskLevel: 'MEDIUM',
      target: 'LLM.router',
      description: '检测到 8 次成本过高, 建议简单查询路由到 qwen3-turbo',
      expectedRoi: 0.20,
      status: 'APPROVED',
      createdAt: Date.now() - 12 * 3600_000,
    },
  ],

  // ============================================================
  // Phase 7: Federation mock (mirror from api)
  // ============================================================

  federation: api.federation,
  triggerFederation: api.triggerFederation,
  federationTimeline: api.federationTimeline,

  // ============================================================
  // Phase 3: Evolution mock (mirror from api)
  // ============================================================

  approveSuggestion: api.approveSuggestion,
  rejectSuggestion: api.rejectSuggestion,
  evolutionLogs: api.evolutionLogs,
};
