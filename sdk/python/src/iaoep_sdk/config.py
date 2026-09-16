"""IAOEP SDK 配置 — OpenTelemetry Tracer 全局单例.

提供 configure() 显式初始化, 或 lazy 自动初始化 (从环境变量读取).
"""

from __future__ import annotations

import os
import threading
from typing import Optional

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace.sampling import (
    ALWAYS_ON,
    ALWAYS_OFF,
    TraceIdRatioBased,
)


_lock = threading.Lock()
_initialized = False


def configure(
    endpoint: Optional[str] = None,
    service_name: Optional[str] = None,
    tenant_id: Optional[str] = None,
    sampling_ratio: Optional[float] = None,
    api_key: Optional[str] = None,
) -> trace.Tracer:
    """显式初始化 Tracer (默认从环境变量读取).

    Returns:
        OpenTelemetry Tracer 实例.
    """
    global _initialized

    endpoint = endpoint or os.environ.get(
        "IAOEP_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"
    )
    service_name = service_name or os.environ.get(
        "IAOEP_SERVICE_NAME", "iaoep-python-agent"
    )
    tenant_id = tenant_id or os.environ.get("IAOEP_TENANT_ID", "default")
    ratio = sampling_ratio if sampling_ratio is not None else float(
        os.environ.get("IAOEP_SAMPLING_RATIO", "1.0")
    )

    with _lock:
        if not _initialized:
            resource = Resource.create({
                "service.name": service_name,
                "iaoep.tenant_id": tenant_id,
            })

            sampler = (
                ALWAYS_ON if ratio >= 1.0
                else ALWAYS_OFF if ratio <= 0.0
                else TraceIdRatioBased(ratio)
            )

            provider = TracerProvider(resource=resource, sampler=sampler)
            exporter = OTLPSpanExporter(endpoint=f"{endpoint}/v1/traces")
            provider.add_span_processor(BatchSpanProcessor(exporter))
            trace.set_tracer_provider(provider)

            _initialized = True

    return trace.get_tracer("io.iaoep.sdk", "0.1.0")


def get_tracer() -> trace.Tracer:
    """获取 Tracer (若未初始化则 lazy 调用 configure)."""
    if not _initialized:
        configure()
    return trace.get_tracer("io.iaoep.sdk", "0.1.0")
