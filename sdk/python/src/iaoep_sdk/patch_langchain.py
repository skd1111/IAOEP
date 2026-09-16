"""LangChain 全局 patch — 一行启用 LangChain 全自动 trace.

用法:
    from iaoep_sdk import patch_langchain
    patch_langchain()  # 全局启用, 之后所有 LangChain 调用都自动 trace

支持 LangChain >= 0.1 (langchain-core).
"""

from __future__ import annotations

import logging
from typing import Any

logger = logging.getLogger(__name__)


def patch_langchain() -> None:
    """Patch LangChain BaseChatModel.invoke / BaseTool.run 自动产生 Span.

    安全调用: 若 langchain-core 未安装, 则跳过 patch (不抛异常).
    """
    try:
        from langchain_core.language_models.chat_models import BaseChatModel
        from langchain_core.tools import BaseTool
    except ImportError:
        logger.info("langchain-core not installed, skipping patch")
        return

    _patch_chat_model(BaseChatModel)
    _patch_tool(BaseTool)
    logger.info("LangChain patched successfully")


def _patch_chat_model(BaseChatModel: Any) -> None:
    """Patch BaseChatModel.invoke / ainvoke / stream."""
    from iaoep_sdk.config import get_tracer

    tracer = get_tracer()
    original_invoke = BaseChatModel.invoke
    original_ainvoke = BaseChatModel.ainvoke
    original_stream = BaseChatModel.stream

    def patched_invoke(self, *args, **kwargs):
        model = getattr(self, "model_name", None) or getattr(self, "model", "unknown")
        with tracer.start_as_current_span("llm.call") as span:
            span.set_attribute("gen_ai.system", "langchain")
            span.set_attribute("gen_ai.request.model", str(model))
            try:
                result = original_invoke(self, *args, **kwargs)
                _extract_tokens(span, result)
                return result
            except Exception as e:
                from opentelemetry.trace import Status, StatusCode
                span.set_status(Status(StatusCode.ERROR, str(e)))
                raise

    async def patched_ainvoke(self, *args, **kwargs):
        model = getattr(self, "model_name", None) or getattr(self, "model", "unknown")
        with tracer.start_as_current_span("llm.call") as span:
            span.set_attribute("gen_ai.system", "langchain")
            span.set_attribute("gen_ai.request.model", str(model))
            try:
                result = await original_ainvoke(self, *args, **kwargs)
                _extract_tokens(span, result)
                return result
            except Exception as e:
                from opentelemetry.trace import Status, StatusCode
                span.set_status(Status(StatusCode.ERROR, str(e)))
                raise

    def patched_stream(self, *args, **kwargs):
        model = getattr(self, "model_name", None) or getattr(self, "model", "unknown")
        with tracer.start_as_current_span("llm.call") as span:
            span.set_attribute("gen_ai.system", "langchain")
            span.set_attribute("gen_ai.request.model", str(model))
            try:
                yield from original_stream(self, *args, **kwargs)
            except Exception as e:
                from opentelemetry.trace import Status, StatusCode
                span.set_status(Status(StatusCode.ERROR, str(e)))
                raise

    BaseChatModel.invoke = patched_invoke
    BaseChatModel.ainvoke = patched_ainvoke
    BaseChatModel.stream = patched_stream


def _patch_tool(BaseTool: Any) -> None:
    """Patch BaseTool.run / arun."""
    from iaoep_sdk.config import get_tracer

    tracer = get_tracer()
    original_run = BaseTool.run
    original_arun = BaseTool.arun

    def patched_run(self, *args, **kwargs):
        name = getattr(self, "name", "unknown_tool")
        with tracer.start_as_current_span("tool.execute") as span:
            span.set_attribute("tool.name", str(name))
            try:
                return original_run(self, *args, **kwargs)
            except Exception as e:
                from opentelemetry.trace import Status, StatusCode
                span.set_status(Status(StatusCode.ERROR, str(e)))
                raise

    async def patched_arun(self, *args, **kwargs):
        name = getattr(self, "name", "unknown_tool")
        with tracer.start_as_current_span("tool.execute") as span:
            span.set_attribute("tool.name", str(name))
            try:
                return await original_arun(self, *args, **kwargs)
            except Exception as e:
                from opentelemetry.trace import Status, StatusCode
                span.set_status(Status(StatusCode.ERROR, str(e)))
                raise

    BaseTool.run = patched_run
    BaseTool.arun = patched_arun


def _extract_tokens(span: Any, result: Any) -> None:
    """从 LangChain AIMessage 提取 token 数."""
    usage = None
    # AIMessage 多种结构: result.usage_metadata, result.response_metadata, etc.
    if hasattr(result, "usage_metadata") and result.usage_metadata:
        usage = result.usage_metadata
    elif hasattr(result, "response_metadata"):
        rm = result.response_metadata or {}
        if "token_usage" in rm:
            usage = rm["token_usage"]

    if usage:
        in_tok = usage.get("input_tokens") or usage.get("prompt_tokens")
        out_tok = usage.get("output_tokens") or usage.get("completion_tokens")
        if in_tok:
            span.set_attribute("gen_ai.usage.input_tokens", int(in_tok))
        if out_tok:
            span.set_attribute("gen_ai.usage.output_tokens", int(out_tok))
