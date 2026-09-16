/**
 * IAOEP TypeScript SDK — declarative OTLP tracing for AI agents.
 *
 * Quick start:
 * ```ts
 * import { observedAgent, observedLLMCall, observedTool, configure } from '@iaoep/sdk';
 *
 * configure({
 *   endpoint: 'http://localhost:4318',
 *   serviceName: 'customer-service-agent',
 * });
 *
 * const chat = observedAgent(
 *   { name: 'customer-service', skill: 'general' },
 *   async (query: string) => callLLM(query)
 * );
 * ```
 */

export { configure, getTracer } from './config.js';
export { observedAgent, observedLLMCall, observedTool } from './decorators.js';
export { patchLangChainJS } from './patch-langchain.js';
export { abTest, abTestContext, ABTestClient } from './ab-test.js';
export type { ABTestOptions, ABTestClientOptions } from './ab-test.js';
export type { IAOEPConfig } from './config.js';
export type { ObservedAgentOptions, ObservedLLMOptions, ObservedToolOptions } from './decorators.js';
