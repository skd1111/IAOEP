/**
 * IAOEP TypeScript SDK — A/B Test 集成 (Phase 4)
 *
 * 自动决定 Agent 调用走 baseline / candidate 版本.
 *
 * 用法:
 * ```ts
 * import { abTest, abTestContext } from '@iaoep/sdk';
 *
 * const chat = abTest(
 *   { name: 'customer-service-ab' },
 *   async (query: string) => {
 *     const group = abTestContext.currentGroup();
 *     const model = group === 'baseline' ? 'qwen3-turbo' : 'qwen3-max';
 *     return callLLM(model, query);
 *   }
 * );
 * ```
 */

import { AsyncLocalStorage } from 'node:async_hooks';

// ============================================================
// AsyncLocalStorage: 当前异步上下文的 A/B 测试分组
// ============================================================

interface ABState {
  abTestName: string | null;
  group: string | null;
}

const abStorage = new AsyncLocalStorage<ABState>();

export const abTestContext = {
  /** 设置当前上下文的 A/B 测试名 + 分组 */
  set(abTestName: string, group: string): void {
    abStorage.enterWith({ abTestName, group });
  },

  /** 清除 (自动通过 withABTest 调用) */
  clear(): void {
    // AsyncLocalStorage 自动随作用域结束, 这里仅显式退出
    abStorage.enterWith({ abTestName: null, group: null });
  },

  /** 读当前 group (baseline / candidate / null) */
  currentGroup(): string | null {
    const state = abStorage.getStore();
    return state?.group ?? null;
  },

  /** 读当前 A/B 测试名 */
  currentABTestName(): string | null {
    const state = abStorage.getStore();
    return state?.abTestName ?? null;
  },
};

// ============================================================
// ABTestClient — HTTP 调用 evaluator /assign 端点
// ============================================================

export interface ABTestClientOptions {
  baseUrl?: string;
  cacheTtlSeconds?: number;
  timeoutSeconds?: number;
}

interface CacheEntry {
  group: string;
  expireAt: number;
}

export class ABTestClient {
  private baseUrl: string;
  private cacheTtlMs: number;
  private timeoutMs: number;
  private cache = new Map<string, CacheEntry>();

  constructor(options: ABTestClientOptions = {}) {
    this.baseUrl =
      options.baseUrl
      ?? (typeof process !== 'undefined' ? process.env.IAOEP_EVALUATOR_BASE_URL : undefined)
      ?? 'http://localhost:8082';
    this.cacheTtlMs = (options.cacheTtlSeconds ?? 30) * 1000;
    this.timeoutMs = (options.timeoutSeconds ?? 5) * 1000;
  }

  /**
   * Phase 5: 按 trace_id 细粒度分配 (同一 trace 所有 span 一致).
   */
  async assignByTraceId(abTestName: string, projectId: string, traceId: string): Promise<string> {
    return this.doAssign(abTestName, projectId, `trace:${traceId}`);
  }

  /**
   * Phase 4 兼容 API: 按 userId 分配.
   */
  async assign(abTestName: string, projectId: string, userId: string): Promise<string> {
    return this.doAssign(abTestName, projectId, `user:${userId || 'anonymous'}`);
  }

  private async doAssign(abTestName: string, projectId: string, stickyKey: string): Promise<string> {
    const cacheKey = `${abTestName}:${stickyKey}`;
    const now = Date.now();
    const cached = this.cache.get(cacheKey);
    if (cached && cached.expireAt > now) return cached.group;

    let group = 'baseline';
    try {
      const resp = await fetch(
        `${this.baseUrl}/api/v1/iaoep/projects/${projectId}/ab-tests/${abTestName}/assign`,
        { signal: AbortSignal.timeout(this.timeoutMs) }
      );
      if (resp.ok) {
        const data = await resp.json() as { group?: string };
        group = data.group ?? 'baseline';
      }
    } catch (e) {
      console.debug('[iaoep-sdk] ABTest assign failed, fallback to baseline:', e);
    }

    this.cache.set(cacheKey, { group, expireAt: now + this.cacheTtlMs });
    return group;
  }
}

// ============================================================
// abTest() 高阶函数
// ============================================================

let _client: ABTestClient | null = null;

function getClient(): ABTestClient {
  if (!_client) _client = new ABTestClient();
  return _client;
}

export interface ABTestOptions {
  name: string;
  groups?: [string, string];        // 默认 ['baseline', 'candidate']
  projectId?: string;
  cacheSeconds?: number;
}

/**
 * abTest() — 高阶函数包装, 自动决定 baseline / candidate 版本.
 *
 * 装饰 async 函数, 调用前调 evaluator /assign 决定 group, 写入 AsyncLocalStorage.
 * 被装饰函数内部可通过 `abTestContext.currentGroup()` 读到 group.
 *
 * Phase 5: 按 trace_id 细粒度分配 (同一 trace 所有 span 一致).
 */
export function abTest<TArgs extends unknown[], TReturn>(
  options: ABTestOptions,
  fn: (...args: TArgs) => Promise<TReturn>,
): (...args: TArgs) => Promise<TReturn> {
  const client = getClient();
  const projectId = options.projectId ?? '00000000-0000-0000-0000-000000000001';

  return async (...args: TArgs): Promise<TReturn> => {
    const traceId = extractTraceId(args);
    const group = await client.assignByTraceId(options.name, projectId, traceId);

    console.debug(`[iaoep-sdk] ab_test ${options.name} assigned group=${group} for trace_id=${traceId}`);

    // 进入新的 AsyncLocalStorage 上下文, 让 fn 内部能读 group
    return await abStorage.run({ abTestName: options.name, group }, async () => {
      return fn(...args);
    });
  };
}

function extractTraceId(args: unknown[]): string {
  for (const a of args) {
    if (typeof a === 'string' && a) return a;
    if (a && typeof a === 'object') {
      const obj = a as Record<string, unknown>;
      if (typeof obj.traceId === 'string') return obj.traceId;
      if (typeof obj.trace_id === 'string') return obj.trace_id;
      if (typeof obj.userId === 'string') return obj.userId;
      if (typeof obj.user_id === 'string') return obj.user_id;
    }
  }
  // 兜底: 随机生成 (Phase 5 应从 OTEL current span 取)
  return crypto.randomUUID();
}
