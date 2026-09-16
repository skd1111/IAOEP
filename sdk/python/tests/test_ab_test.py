"""A/B Test 集成单元测试."""

import pytest
from unittest.mock import patch, AsyncMock

from iaoep_sdk.ab_test import ab_test, ABTestContext, ABTestClient


def test_context_set_clear():
    ABTestContext.set("test-ab", "baseline")
    assert ABTestContext.current_group() == "baseline"
    assert ABTestContext.current_ab_test_name() == "test-ab"
    ABTestContext.clear()
    assert ABTestContext.current_group() is None


def test_extract_user_id_from_kwarg():
    from iaoep_sdk.ab_test import _extract_user_id
    assert _extract_user_id(("a", "b"), {"user_id": "alice"}) == "alice"
    assert _extract_user_id(("a", "b"), {}) == "a"  # 第一个 string 参数
    assert _extract_user_id((), {}) == "anonymous"


@pytest.mark.asyncio
async def test_ab_test_decorator_assigns_group():
    """@ab_test 装饰器自动决定 group 并设置 ContextVar."""

    call_log = []

    @ab_test(name="test-ab")
    def my_agent(query: str) -> str:
        group = ABTestContext.current_group()
        call_log.append(("sync", query, group))
        return f"response for {query} ({group})"

    with patch.object(ABTestClient, "assign", return_value="candidate"):
        result = my_agent("hello")

    assert result == "response for hello (candidate)"
    assert call_log == [("sync", "hello", "candidate")]


def test_ab_test_async():
    """@ab_test 也支持 async 函数."""

    @ab_test(name="test-ab-async")
    async def my_async_agent(query: str) -> str:
        return f"async response ({ABTestContext.current_group()})"

    with patch.object(ABTestClient, "assign", return_value="baseline"):
        import asyncio
        result = asyncio.run(my_async_agent("test"))

    assert result == "async response (baseline)"


def test_ab_test_client_fallback():
    """evaluator 调用失败时兜底到 baseline."""
    client = ABTestClient(base_url="http://localhost:99999", cache_ttl_seconds=30)

    with patch("httpx.Client.get", side_effect=Exception("connection refused")):
        group = client.assign("test-ab", project_id=None, user_id="alice")

    assert group == "baseline"
    client.close()
