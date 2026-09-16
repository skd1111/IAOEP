/**
 * LangChain.js (>= 0.1) 全局 patch — 一行启用 LangChain 全自动 trace.
 *
 * 用法:
 * ```ts
 * import { patchLangChainJS } from '@iaoep/sdk';
 * patchLangChainJS();
 *
 * // 之后所有 ChatOpenAI / Tool 调用都自动 trace
 * const llm = new ChatOpenAI({ model: 'qwen3-turbo' });
 * const result = await llm.invoke('Hello');
 * ```
 */

import { SpanStatusCode } from '@opentelemetry/api';
import { getTracer } from './config.js';

export function patchLangChainJS(): void {
  patchChatModels();
  patchTools();
}

function patchChatModels(): void {
  // 尝试加载 @langchain/core
  let core: typeof import('@langchain/core') | undefined;
  try {
    // dynamic require to make it optional
    // @ts-expect-error - optional dep
    core = require('@langchain/core');
  } catch {
    console.info('[iaoep-sdk] @langchain/core not installed, skipping ChatModel patch');
    return;
  }
  if (!core) return;

  const tracer = getTracer();
  const BaseChatModel = (core as Record<string, unknown>).BaseChatModel as
    | { prototype: Record<string, unknown> }
    | undefined;
  if (!BaseChatModel) return;

  const proto = BaseChatModel.prototype as Record<string, unknown>;
  const origInvoke = proto.invoke as (...a: unknown[]) => Promise<unknown>;
  if (origInvoke) {
    proto.invoke = async function patchedInvoke(this: unknown, ...rest: unknown[]) {
      const modelName = (this as Record<string, unknown>).modelName
        ?? (this as Record<string, unknown>).model
        ?? 'unknown';
      const span = tracer.startSpan('llm.call');
      span.setAttribute('gen_ai.system', 'langchain');
      span.setAttribute('gen_ai.request.model', String(modelName));
      try {
        const result = await origInvoke.apply(this, rest);
        extractTokens(span, result);
        span.setStatus({ code: SpanStatusCode.OK });
        return result;
      } catch (e) {
        span.setStatus({
          code: SpanStatusCode.ERROR,
          message: e instanceof Error ? e.message : String(e),
        });
        throw e;
      } finally {
        span.end();
      }
    };
  }
}

function patchTools(): void {
  let core: typeof import('@langchain/core') | undefined;
  try {
    // @ts-expect-error - optional dep
    core = require('@langchain/core');
  } catch {
    return;
  }
  if (!core) return;

  const tracer = getTracer();
  const StructuredTool = (core as Record<string, unknown>).StructuredTool as
    | { prototype: Record<string, unknown> }
    | undefined;
  if (!StructuredTool) return;

  const proto = StructuredTool.prototype as Record<string, unknown>;
  const origCall = proto._call as (...a: unknown[]) => Promise<unknown>;
  if (origCall) {
    proto._call = async function patchedCall(this: unknown, ...rest: unknown[]) {
      const name = (this as Record<string, unknown>).name ?? 'unknown_tool';
      const span = tracer.startSpan('tool.execute');
      span.setAttribute('tool.name', String(name));
      try {
        const result = await origCall.apply(this, rest);
        span.setStatus({ code: SpanStatusCode.OK });
        return result;
      } catch (e) {
        span.setStatus({
          code: SpanStatusCode.ERROR,
          message: e instanceof Error ? e.message : String(e),
        });
        throw e;
      } finally {
        span.end();
      }
    };
  }
}

function extractTokens(span: { setAttribute: (k: string, v: unknown) => void }, result: unknown): void {
  if (!result || typeof result !== 'object') return;
  const r = result as Record<string, unknown>;

  // LangChain AIMessage: result.usage_metadata.input_tokens / output_tokens
  if (r.usage_metadata && typeof r.usage_metadata === 'object') {
    const usage = r.usage_metadata as Record<string, unknown>;
    if (usage.input_tokens !== undefined) {
      span.setAttribute('gen_ai.usage.input_tokens', Number(usage.input_tokens));
    }
    if (usage.output_tokens !== undefined) {
      span.setAttribute('gen_ai.usage.output_tokens', Number(usage.output_tokens));
    }
  }

  // 备选: result.response_metadata.token_usage
  if (r.response_metadata && typeof r.response_metadata === 'object') {
    const rm = r.response_metadata as Record<string, unknown>;
    const usage = rm.token_usage as Record<string, unknown> | undefined;
    if (usage) {
      if (usage.prompt_tokens !== undefined && r.usage_metadata === undefined) {
        span.setAttribute('gen_ai.usage.input_tokens', Number(usage.prompt_tokens));
      }
      if (usage.completion_tokens !== undefined && r.usage_metadata === undefined) {
        span.setAttribute('gen_ai.usage.output_tokens', Number(usage.completion_tokens));
      }
    }
  }
}
