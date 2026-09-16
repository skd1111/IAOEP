"""LLM Judge sidecar 单元测试 (mock LLM)."""

import json
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    return TestClient(app)


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "ok"
    assert data["service"] == "iaoep-judge"


def test_parse_valid_response():
    """parse_llm_response 正确解析合法 JSON."""
    from app.main import parse_llm_response, ScoreDimension

    raw = json.dumps({
        "dimensions": [
            {"name": "accuracy", "score": 0.9, "reason": "good"},
            {"name": "style",    "score": 0.7, "reason": "ok"},
        ]
    })
    dims = parse_llm_response(raw)
    assert len(dims) == 2
    assert dims[0].name == "accuracy"
    assert dims[0].score == 0.9


def test_parse_markdown_wrapped():
    """parse_llm_response 处理 markdown 代码块."""
    from app.main import parse_llm_response

    raw = "```json\n" + json.dumps({"dimensions": [{"name": "x", "score": 0.5, "reason": "r"}]}) + "\n```"
    dims = parse_llm_response(raw)
    assert len(dims) == 1
    assert dims[0].name == "x"


def test_parse_invalid_json():
    """parse_llm_response 失败抛 HTTPException."""
    from app.main import parse_llm_response
    from fastapi import HTTPException

    with pytest.raises(HTTPException) as exc:
        parse_llm_response("not json")
    assert exc.value.status_code == 502


def test_extract_input_output():
    """extract_input_output 提取第一个 agent.run span 的 input/output."""
    from app.main import extract_input_output

    trace = {
        "spans": [
            {"span_name": "llm.call", "duration_ms": 100},
            {"span_name": "agent.run", "agent_input": "Q?", "agent_output": "A!"},
            {"span_name": "tool.execute", "duration_ms": 50},
        ]
    }
    inp, out = extract_input_output(trace)
    assert inp == "Q?"
    assert out == "A!"


def test_score_endpoint_success(client):
    """POST /score 调用 LLM mock 返回, 解析成功."""
    mock_response = json.dumps({
        "dimensions": [
            {"name": "accuracy", "score": 0.9, "reason": "good"},
            {"name": "style",    "score": 0.8, "reason": "ok"},
        ]
    })

    with patch("app.main.call_llm", new=AsyncMock(return_value=mock_response)):
        resp = client.post("/score", json={
            "trace": {
                "spans": [
                    {"span_name": "agent.run", "agent_input": "q", "agent_output": "a"}
                ]
            },
            "rubric": "test rubric",
            "dimensions": ["accuracy", "style"],
            "config": {"llm_model": "test", "llm_api_key": "test-key"}
        })

    assert resp.status_code == 200
    data = resp.json()
    assert data["overall"] == 0.85  # (0.9 + 0.8) / 2
    assert len(data["dimensions"]) == 2


def test_score_voting(client):
    """voting_count=3 时调用 3 次 LLM, 评分取平均."""
    mock_response_1 = json.dumps({"dimensions": [{"name": "x", "score": 0.6, "reason": "1"}]})
    mock_response_2 = json.dumps({"dimensions": [{"name": "x", "score": 0.8, "reason": "2"}]})
    mock_response_3 = json.dumps({"dimensions": [{"name": "x", "score": 1.0, "reason": "3"}]})

    with patch("app.main.call_llm", new=AsyncMock(side_effect=[mock_response_1, mock_response_2, mock_response_3])):
        resp = client.post("/score", json={
            "trace": {"spans": [{"span_name": "agent.run", "agent_output": "a"}]},
            "rubric": "r",
            "dimensions": ["x"],
            "config": {"voting_count": 3, "llm_api_key": "k"}
        })

    data = resp.json()
    assert data["overall"] == pytest.approx(0.8, 0.01)  # (0.6+0.8+1.0)/3
