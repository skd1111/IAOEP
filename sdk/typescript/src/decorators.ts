/**
 * IAOEP 高阶函数 — 装饰 Agent/LLM/Tool 调用产生 OTLP Span.
 */

import type { Tracer, Span } from '@opentelemetry/api';
import { SpanStatusCode } from '@opentelemetry/api';
import { getTracer } from './config.js';
import { randomUUID } from 'node:crypto';

// ============================================================
// 类型定义
// ============================================================

export interface ObservedAgentOptions {
  /** Agent 名称 (必填) */
  name: string;
  /** Skill 名 (可选) */
  skill?: string;
  /** 是否捕获输入参数 (PII 风险) */
  captureInput?: boolean;
  /** 是否捕获返回值 (PII 风险) */
  captureOutput?: boolean;
  /** payload 最大长度 (字符) */
  maxPayloadLength?: number;
}

export interface ObservedLLMOptions {
  /** LLM 供应商 (openai / dashscope / anthropic) */
  system: string;
  /** 从参数取模型名的路径 (e.g. "modelName" 或 "options.model") */
  modelPath?: string;
  /** 从参数取 input tokens 的路径 */
  inputTokensPath?: string;
  /** 从参数取 output tokens 的路径 */
  outputTokensPath?: string;
  /** 从返回值取 input tokens 的路径 (e.g. "usage.prompt_tokens") */
  inputTokensResultPath?: string;
  /** 从返回值取 output tokens 的路径 (e.g. "usage.completion_tokens") */
  outputTokensResultPath?: string;
}

export interface ObservedToolOptions {
  /** 工具名 (必填) */
  name: string;
  /** 是否捕获参数 */
  captureArguments?: boolean;
}

// ============================================================
// 工具
// ============================================================

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.substring(0, max) + '...';
}

function safeJson(obj: unknown): string {
  try {
    return JSON.stringify(obj, (_k, v) => v ?? null);
  } catch {
    return String(obj);
  }
}

function resolvePath(obj: unknown, path: string): unknown {
  if (!obj || !path) return undefined;
  const parts = path.split('.');
  let cur: unknown = obj;
  for (const part of parts) {
    if (cur === null || cur === undefined) return undefined;
    cur = (cur as Record<string, unknown>)[part];
  }
  return cur;
}

async function runWithSpan<F extends (...args: never[]) => unknown>(
  tracer: Tracer,
  spanName: string,
  setAttributes: (span: Span) => void,
  func: F,
  args: Parameters<F>,
): Promise<ReturnType<F>> {
  const span = tracer.startSpan(spanName);
  setAttributes(span);
  try {
    const result = await func(...args);
    span.setStatus({ code: SpanStatusCode.OK });
    return result as ReturnType<F>;
  } catch (e) {
    span.setStatus({
      code: SpanStatusCode.ERROR,
      message: e instanceof Error ? e.message : String(e),
    });
    span.recordException(e as Error);
    throw e;
  } finally {
    span.end();
  }
}

// ============================================================
// observedAgent
// ============================================================

export function observedAgent<TArgs extends unknown[], TReturn>(
  options: ObservedAgentOptions,
  func: (...args: TArgs) => TReturn | Promise<TReturn>,
): (...args: TArgs) => Promise<TReturn> {
  const tracer = getTracer();
  return async (...args: TArgs): Promise<TReturn> => {
    return runWithSpan(
      tracer,
      'agent.run',
      (span) => {
        span.setAttribute('agent.name', options.name);
        if (options.skill) {
          span.setAttribute('agent.skill.name', options.skill);
        }
        if (options.captureInput) {
          span.setAttribute(
            'agent.input',
            truncate(safeJson(args), options.maxPayloadLength ?? 4096),
          );
        }
      },
      func,
      args,
    );
  };
}

// ============================================================
// observedLLMCall
// ============================================================

export function observedLLMCall<TArgs extends unknown[], TReturn>(
  options: ObservedLLMOptions,
  func: (...args: TArgs) => TReturn | Promise<TReturn>,
): (...args: TArgs) => Promise<TReturn> {
  const tracer = getTracer();
  return async (...args: TArgs): Promise<TReturn> => {
    return runWithSpan(
      tracer,
      'llm.call',
      (span) => {
        span.setAttribute('gen_ai.system', options.system);

        // 模型名: 从参数取
        if (options.modelPath) {
          const modelVal = resolvePath(args[0], options.modelPath);
          if (modelVal !== undefined) {
            span.setAttribute('gen_ai.request.model', String(modelVal));
          }
        }

        // Token 数: 从参数取
        if (options.inputTokensPath) {
          const val = resolvePath(args[0], options.inputTokensPath);
          if (val !== undefined) {
            span.setAttribute('gen_ai.usage.input_tokens', Number(val));
          }
        }
        if (options.outputTokensPath) {
          const val = resolvePath(args[0], options.outputTokensPath);
          if (val !== undefined) {
            span.setAttribute('gen_ai.usage.output_tokens', Number(val));
          }
        }
      },
      func,
      args,
    );
  };
}

// ============================================================
// observedTool
// ============================================================

export function observedTool<TArgs extends unknown[], TReturn>(
  options: ObservedToolOptions,
  func: (...args: TArgs) => TReturn | Promise<TReturn>,
): (...args: TArgs) => Promise<TReturn> {
  const tracer = getTracer();
  return async (...args: TArgs): Promise<TReturn> => {
    return runWithSpan(
      tracer,
      'tool.execute',
      (span) => {
        span.setAttribute('tool.name', options.name);
        span.setAttribute('tool.call.id', randomUUID());
        if (options.captureArguments) {
          span.setAttribute('tool.arguments', safeJson(args));
        }
      },
      func,
      args,
    );
  };
}
