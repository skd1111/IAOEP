"""IAOEP Python SDK — A/B Test 集成 (Phase 5: 细粒度).

Phase 4 + 5: 让 SDK 自动决定 Agent 调用走 baseline / candidate 版本,
按 trace_id 细粒度分配 (同一 trace 所有 span 落到同一 group).

用法:
    from iaoep_sdk import ab_test, ab_test_context

    @ab_test(name="customer-service-ab")
    def chat(query: str) -> str:
        group = ab_test_context.current_group()
        return call_llm(query, model="qwen3-turbo" if group == "baseline" else "qwen3-max")
"""

from __future__ import annotations

import functools
import logging
import os
import threading
import time
import uuid
from contextvars import ContextVar
from typing import Any, Callable, Dict, Optional, Tuple
from uuid import UUID

import httpx

logger = logging.getLogger("iaoep-sdk")

from __future__ import annotations

import functools
import logging
import os
import threading
import time
from contextvars import ContextVar
from typing import Any, Callable, Dict, Optional, Tuple
from uuid import UUID

import httpx

logger = logging.getLogger("iaoep-sdk")


# ============================================================
# ContextVar: 当前线程的 A/B 测试分组
# ============================================================

_current_group: ContextVar[Optional[str]] = ContextVar("iaoep_ab_group", default=None)
_current_name: ContextVar[Optional[str]] = ContextVar("iaoep_ab_name", default=None)


class ABTestContext:
    """当前线程的 A/B 测试分组 (baseline / candidate).

    用 ContextVar 实现, 跨异步 协程安全.
    """

    @staticmethod
    def set(ab_test_name: str, group: str) -> None:
        _current_name.set(ab_test_name)
        _current_group.set(group)

    @staticmethod
    def clear() -> None:
        _current_name.set(None)
        _current_group.set(None)

    @staticmethod
    def current_group() -> Optional[str]:
        return _current_group.get()

    @staticmethod
    def current_ab_test_name() -> Optional[str]:
        return _current_name.get()


# ============================================================
# ABTestClient — HTTP 调用 evaluator /assign 端点
# ============================================================

class ABTestClient:
    """IAOEP A/B Test HTTP Client.

    缓存分配结果 (按 ab_test_name + user_id 维度), 避免每次请求都打 evaluator.
    """

    def __init__(
        self,
        base_url: Optional[str] = None,
        cache_ttl_seconds: int = 30,
        timeout_seconds: int = 5,
    ):
        self.base_url = (
            base_url
            or os.environ.get("IAOEP_EVALUATOR_BASE_URL")
            or "http://localhost:8082"
        )
        self.cache_ttl_seconds = cache_ttl_seconds
        self.timeout_seconds = timeout_seconds
        self._cache: Dict[str, Tuple[str, float]] = {}
        self._lock = threading.Lock()
        self._http = httpx.Client(timeout=timeout_seconds)

    def assign(self, ab_test_name: str, project_id: UUID, user_id: str) -> str:
        """调 evaluator 决定 baseline / candidate, 带本地缓存."""
        cache_key = f"{ab_test_name}:{user_id or 'anonymous'}"
        now = time.time()
        with self._lock:
            cached = self._cache.get(cache_key)
            if cached and cached[1] > now:
                return cached[0]

        try:
            resp = self._http.get(
                f"{self.base_url}/api/v1/iaoep/projects/{project_id}/ab-tests/{ab_test_name}/assign"
            )
            resp.raise_for_status()
            data = resp.json()
            group = data.get("group", "baseline")
        except Exception as e:
            logger.debug("ABTest assign failed, fallback to baseline: %s", e)
            group = "baseline"

        with self._lock:
            self._cache[cache_key] = (group, now + self.cache_ttl_seconds)
        return group

    def close(self):
        self._http.close()


# ============================================================
# @ab_test 装饰器
# ============================================================

_client: Optional[ABTestClient] = None


def _get_client() -> ABTestClient:
    """获取 (懒加载) ABTestClient 全局单例."""
    global _client
    if _client is None:
        _client = ABTestClient()
    return _client


def ab_test(
    name: str,
    groups: Tuple[str, str] = ("baseline", "candidate"),
    project_id: Optional[UUID] = None,
    cache_seconds: int = 30,
) -> Callable:
    """@ab_test 装饰器 — 自动决定 baseline / candidate 版本.

    用法:
        @ab_test(name="customer-service-ab")
        def chat(query: str) -> str:
            # 读 ABTestContext.current_group() 决定版本
            group = ab_test_context.current_group()  # 或: from iaoep_sdk import ab_test_context
            model = "qwen3-turbo" if group == "baseline" else "qwen3-max"
            return call_llm(model=model, query=query)
    """
    def decorator(func: Callable) -> Callable:
        client = _get_client()
        pid = project_id or UUID("00000000-0000-0000-0000-000000000001")

        @functools.wraps(func)
        def sync_wrapper(*args, **kwargs) -> Any:
            trace_id = _extract_trace_id(args, kwargs)
            group = client.assign_by_trace_id(name, pid, trace_id)
            logger.debug("ab_test %s assigned group=%s for trace_id=%s", name, group, trace_id)
            ABTestContext.set(name, group)
            try:
                return func(*args, **kwargs)
            finally:
                ABTestContext.clear()

        @functools.wraps(func)
        async def async_wrapper(*args, **kwargs) -> Any:
            trace_id = _extract_trace_id(args, kwargs)
            group = client.assign_by_trace_id(name, pid, trace_id)
            logger.debug("ab_test %s assigned group=%s for trace_id=%s", name, group, trace_id)
            ABTestContext.set(name, group)
            try:
                return await func(*args, **kwargs)
            finally:
                ABTestContext.clear()

        import inspect
        if inspect.iscoroutinefunction(func):
            return async_wrapper
        return sync_wrapper

    return decorator


def _extract_trace_id(args: tuple, kwargs: dict) -> str:
    """从参数提取 trace_id (Phase 5: 优先 trace_id, 兜底 user_id).

    真实生产应从 OTEL current span 取 trace_id; 这里简化从 kwargs 取.
    """
    if "trace_id" in kwargs:
        return str(kwargs["trace_id"])
    if "traceId" in kwargs:
        return str(kwargs["traceId"])
    # 兜底: 随机生成 UUID (同一函数调用内一致)
    return str(uuid.uuid4())


def _extract_user_id(args: tuple, kwargs: dict) -> str:
    """从参数提取 user_id."""
    if "user_id" in kwargs:
        return str(kwargs["user_id"])
    if "userId" in kwargs:
        return str(kwargs["userId"])
    for a in args:
        if isinstance(a, str) and a:
            return a
    return "anonymous"


# 模块级 alias (方便 import)
ab_test_context = ABTestContext
