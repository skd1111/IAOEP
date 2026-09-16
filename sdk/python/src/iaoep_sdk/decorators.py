"""IAOEP 装饰器 — 一行注解接入 OTLP tracing.

设计参考 IAOEP SDK 设计文档 (§4 Java SDK), Python 版用装饰器实现.
"""

from __future__ import annotations

import functools
import inspect
import json
import uuid
from typing import Any, Callable, Optional

from opentelemetry.trace import StatusCode, Status

from iaoep_sdk.config import get_tracer


def _truncate(s: str, max_len: int) -> str:
    """截断 payload 防止巨型内容撑爆 trace."""
    if not isinstance(s, str):
        s = str(s)
    return s if len(s) <= max_len else s[:max_len] + "..."


def _safe_json(obj: Any) -> str:
    """安全 JSON 序列化."""
    try:
        return json.dumps(obj, default=str, ensure_ascii=False)
    except Exception:
        return repr(obj)


def _resolve_param(signature, bound_args, param_path: str) -> Any:
    """从方法参数中按 SpEL-lite 路径取值 (支持 '.' 分隔嵌套).

    e.g. "#result.input_tokens" → bound_args['result'].input_tokens
    """
    if not param_path.startswith("#"):
        return None
    path = param_path[1:]  # remove leading '#'

    parts = path.split(".")
    if not parts:
        return None

    # 第一段必须是参数名
    first = parts[0]
    if first not in bound_args:
        return None
    value = bound_args[first]

    # 后续段是属性访问
    for attr in parts[1:]:
        try:
            value = getattr(value, attr)
        except AttributeError:
            return None
        if callable(value):
            try:
                value = value()
            except Exception:
                return None
    return value


# ============================================================
# @observed_agent
# ============================================================

def observed_agent(
    name: str,
    skill: str = "",
    capture_input: bool = False,
    capture_output: bool = False,
    max_payload_length: int = 4096,
) -> Callable:
    """标注 Agent 方法, 产生 agent.run Span.

    Usage:
        @observed_agent(name="customer-service", skill="general")
        def chat(query: str) -> str:
            return call_llm(query)
    """
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span("agent.run") as span:
                span.set_attribute("agent.name", name)
                if skill:
                    span.set_attribute("agent.skill.name", skill)
                if capture_input:
                    span.set_attribute("agent.input",
                                       _truncate(_safe_json({"args": args, "kwargs": kwargs}),
                                                 max_payload_length))
                try:
                    result = func(*args, **kwargs)
                    if capture_output:
                        span.set_attribute("agent.output",
                                           _truncate(_safe_json(result), max_payload_length))
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span("agent.run") as span:
                span.set_attribute("agent.name", name)
                if skill:
                    span.set_attribute("agent.skill.name", skill)
                if capture_input:
                    span.set_attribute("agent.input",
                                       _truncate(_safe_json({"args": args, "kwargs": kwargs}),
                                                 max_payload_length))
                try:
                    result = await func(*args, **kwargs)
                    if capture_output:
                        span.set_attribute("agent.output",
                                           _truncate(_safe_json(result), max_payload_length))
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        if inspect.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper
    return decorator


# ============================================================
# @observed_llm_call
# ============================================================

def observed_llm_call(
    system: str,
    model_param: str = "",
    input_tokens_param: str = "",
    output_tokens_param: str = "",
) -> Callable:
    """标注 LLM 调用方法, 产生 llm.call Span (含 token 数).

    Usage:
        @observed_llm_call(system="openai",
                           model_param="#model",
                           input_tokens_param="#result.usage.prompt_tokens",
                           output_tokens_param="#result.usage.completion_tokens")
        def call_llm(model: str, messages):
            response = openai_client.chat(model=model, messages=messages)
            return response
    """
    def decorator(func: Callable) -> Callable:
        sig = inspect.signature(func)

        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs):
            bound = sig.bind(*args, **kwargs)
            bound.apply_defaults()

            tracer = get_tracer()
            with tracer.start_as_current_span("llm.call") as span:
                span.set_attribute("gen_ai.system", system)

                model = _resolve_param(sig, bound.arguments, model_param) if model_param else None
                if model:
                    span.set_attribute("gen_ai.request.model", str(model))

                try:
                    result = func(*args, **kwargs)
                    # 提取 token 数
                    if input_tokens_param:
                        in_tok = _resolve_param(sig, bound.arguments | {"result": result},
                                                input_tokens_param)
                        if in_tok is not None:
                            span.set_attribute("gen_ai.usage.input_tokens", int(in_tok))
                    if output_tokens_param:
                        out_tok = _resolve_param(sig, bound.arguments | {"result": result},
                                                 output_tokens_param)
                        if out_tok is not None:
                            span.set_attribute("gen_ai.usage.output_tokens", int(out_tok))
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs):
            bound = sig.bind(*args, **kwargs)
            bound.apply_defaults()

            tracer = get_tracer()
            with tracer.start_as_current_span("llm.call") as span:
                span.set_attribute("gen_ai.system", system)

                model = _resolve_param(sig, bound.arguments, model_param) if model_param else None
                if model:
                    span.set_attribute("gen_ai.request.model", str(model))

                try:
                    result = await func(*args, **kwargs)
                    if input_tokens_param:
                        in_tok = _resolve_param(sig, bound.arguments | {"result": result},
                                                input_tokens_param)
                        if in_tok is not None:
                            span.set_attribute("gen_ai.usage.input_tokens", int(in_tok))
                    if output_tokens_param:
                        out_tok = _resolve_param(sig, bound.arguments | {"result": result},
                                                 output_tokens_param)
                        if out_tok is not None:
                            span.set_attribute("gen_ai.usage.output_tokens", int(out_tok))
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        if inspect.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper
    return decorator


# ============================================================
# @observed_tool
# ============================================================

def observed_tool(
    name: str,
    capture_arguments: bool = False,
    max_payload_length: int = 2048,
) -> Callable:
    """标注工具方法, 产生 tool.execute Span.

    Usage:
        @observed_tool(name="get_current_time")
        def get_time() -> str:
            return datetime.now().isoformat()
    """
    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span("tool.execute") as span:
                span.set_attribute("tool.name", name)
                span.set_attribute("tool.call.id", str(uuid.uuid4()))
                if capture_arguments:
                    span.set_attribute("tool.arguments",
                                       _truncate(_safe_json({"args": args, "kwargs": kwargs}),
                                                 max_payload_length))
                try:
                    result = func(*args, **kwargs)
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs):
            tracer = get_tracer()
            with tracer.start_as_current_span("tool.execute") as span:
                span.set_attribute("tool.name", name)
                span.set_attribute("tool.call.id", str(uuid.uuid4()))
                if capture_arguments:
                    span.set_attribute("tool.arguments",
                                       _truncate(_safe_json({"args": args, "kwargs": kwargs}),
                                                 max_payload_length))
                try:
                    result = await func(*args, **kwargs)
                    span.set_status(Status(StatusCode.OK))
                    return result
                except Exception as e:
                    span.set_status(Status(StatusCode.ERROR, str(e)))
                    span.record_exception(e)
                    raise

        if inspect.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper
    return decorator
