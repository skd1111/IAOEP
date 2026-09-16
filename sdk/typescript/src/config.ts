/**
 * IAOEP SDK 配置 — OpenTelemetry Node TracerProvider 全局单例.
 */

import { trace, type Tracer } from '@opentelemetry/api';
import { NodeTracerProvider } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { resourceFromAttributes } from '@opentelemetry/resources';
import {
  ATTR_SERVICE_NAME,
} from '@opentelemetry/semantic-conventions';

export interface IAOEPConfig {
  /** OTLP/HTTP endpoint (default: http://localhost:4318) */
  endpoint?: string;
  /** Service name (required, 对应 service.name resource attribute) */
  serviceName: string;
  /** Tenant ID (default: 'default') */
  tenantId?: string;
  /** Sampling ratio 0.0-1.0 (default: 1.0) */
  samplingRatio?: number;
  /** API key (optional, 预留, 当前 ingest gateway 不强制) */
  apiKey?: string;
}

let configured = false;

export function configure(config: IAOEPConfig): Tracer {
  const endpoint = config.endpoint
    ?? process.env.IAOEP_EXPORTER_OTLP_ENDPOINT
    ?? 'http://localhost:4318';
  const tenantId = config.tenantId
    ?? process.env.IAOEP_TENANT_ID
    ?? 'default';
  const ratio = config.samplingRatio
    ?? parseFloat(process.env.IAOEP_SAMPLING_RATIO ?? '1.0');

  if (!configured) {
    const resource = resourceFromAttributes({
      [ATTR_SERVICE_NAME]: config.serviceName,
      'iaoep.tenant_id': tenantId,
    });

    const provider = new NodeTracerProvider({
      resource,
      sampler: ratio >= 1 ? undefined : undefined,  // 简化: 默认全采样
    });

    const exporter = new OTLPTraceExporter({
      url: `${endpoint}/v1/traces`,
    });
    provider.addSpanProcessor(new BatchSpanProcessor(exporter));

    provider.register();
    configured = true;
  }

  return trace.getTracer('io.iaoep.sdk', '0.1.0');
}

export function getTracer(): Tracer {
  if (!configured) {
    // Lazy init from env
    return configure({
      serviceName: process.env.IAOEP_SERVICE_NAME ?? 'iaoep-typescript-agent',
      endpoint: process.env.IAOEP_EXPORTER_OTLP_ENDPOINT,
      tenantId: process.env.IAOEP_TENANT_ID,
      samplingRatio: process.env.IAOEP_SAMPLING_RATIO
        ? parseFloat(process.env.IAOEP_SAMPLING_RATIO)
        : undefined,
    });
  }
  return trace.getTracer('io.iaoep.sdk', '0.1.0');
}
