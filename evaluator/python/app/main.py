"""IAOEP LLM-as-Judge sidecar — FastAPI 服务.

启动:
    uvicorn app.main:app --host 0.0.0.0 --port 9000

提供端点:
    POST /score   — 给定 trace + rubric, 返回 0-1 分 + 反馈文本
    GET  /health  — 健康检查

设计要点:
    - 默认 LLM = 千问 (qwen3-max) via OpenAI 兼容协议
    - 支持多维度评分 (一次调用多个维度, 减少 round-trip)
    - 输出严格 JSON (用 prompt 强约束 + 后处理校验)
"""

from __future__ import annotations

import json
import logging
import os
import re
import time
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
import httpx

logger = logging.getLogger("iaoep-judge")
logging.basicConfig(level=logging.INFO)


# ============================================================
# 数据模型
# ============================================================

class JudgeConfig(BaseModel):
    """LLM Judge 配置 (从 Java 端传入)"""
    llm_model: str = "qwen3-max"
    llm_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    llm_api_key: str = ""
    timeout_seconds: int = 30
    voting_count: int = 1  # 多 LLM 投票, 默认 1 (单次评分)


class ScoreDimension(BaseModel):
    """单个维度的评分"""
    name: str = Field(..., description="维度名, e.g. accuracy / style / safety")
    score: float = Field(..., ge=0.0, le=1.0)
    reason: str = Field(..., description="评分理由")


class ScoreRequest(BaseModel):
    """评分请求"""
    trace: Dict[str, Any] = Field(..., description="一条 IAOEP trace (含 spans)")
    rubric: str = Field(..., description="评分规则 (用户给定)")
    dimensions: List[str] = Field(
        default_factory=lambda: ["accuracy", "style", "safety"],
        description="要评分的维度列表",
    )
    config: JudgeConfig = Field(default_factory=JudgeConfig)


class ScoreResponse(BaseModel):
    """评分响应"""
    overall: float = Field(..., ge=0.0, le=1.0, description="加权总分")
    dimensions: List[ScoreDimension]
    raw_response: str = Field("", description="LLM 原始响应 (调试用)")
    elapsed_ms: int


# ============================================================
# Prompt 模板 (按维度)
# ============================================================

SYSTEM_PROMPT = """你是 IAOEP AI 评测系统的 LLM Judge.
你的任务: 根据给定的 rubric (评分规则), 对 Agent 的输出进行评分.
你必须以严格 JSON 格式返回结果, 不要包含任何额外文字.

JSON 格式:
{
  "dimensions": [
    {"name": "<维度名>", "score": <0.0-1.0 浮点数>, "reason": "<≤100 字理由>"},
    ...
  ]
}

要求:
- score 范围 [0.0, 1.0], 越高越好
- reason 简洁, 指出关键问题
- 仅返回 JSON, 不要 Markdown 代码块
"""

USER_PROMPT_TEMPLATE = """## 评分规则 (rubric)
{rubric}

## Agent 输入
{input}

## Agent 输出
{output}

## 需要评分的维度
{dimensions}

请对每个维度按 rubric 严格评分, 输出 JSON."""


# ============================================================
# LLM 调用 (OpenAI 兼容协议)
# ============================================================

async def call_llm(config: JudgeConfig, messages: List[Dict[str, str]]) -> str:
    """调用 OpenAI 兼容 LLM, 返回 assistant content."""
    if not config.llm_api_key:
        raise HTTPException(500, "IAOEP_JUDGE_LLM_API_KEY not configured")

    url = f"{config.llm_base_url.rstrip('/')}/chat/completions"
    headers = {
        "Authorization": f"Bearer {config.llm_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": config.llm_model,
        "messages": messages,
        "temperature": 0.1,
        "max_tokens": 2048,
        "response_format": {"type": "json_object"},
    }

    async with httpx.AsyncClient(timeout=config.timeout_seconds) as client:
        resp = await client.post(url, json=payload, headers=headers)
        if resp.status_code != 200:
            raise HTTPException(502, f"LLM API failed: {resp.status_code} {resp.text[:500]}")
        data = resp.json()
        return data["choices"][0]["message"]["content"]


# ============================================================
# 评分核心
# ============================================================

def extract_input_output(trace: Dict[str, Any]) -> tuple[str, str]:
    """从 trace 提取 Agent 输入和输出 (取第一个 agent.run span 的 input/output)."""
    input_text = ""
    output_text = ""
    spans = trace.get("spans", [])
    for s in spans:
        if s.get("span_name") == "agent.run":
            input_text = str(s.get("agent_input", "") or "")
            output_text = str(s.get("agent_output", "") or "")
            break
    # 兜底: 取任意 span 的属性
    if not output_text:
        for s in spans:
            if s.get("agent_output"):
                output_text = str(s["agent_output"])
                break
    return input_text, output_text


def parse_llm_response(raw: str) -> List[ScoreDimension]:
    """从 LLM 原始响应解析 ScoreDimension 列表. 严格模式."""
    # 1. 尝试直接 JSON 解析
    text = raw.strip()

    # 2. 兼容 markdown 代码块
    md_match = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", text, re.DOTALL)
    if md_match:
        text = md_match.group(1)

    try:
        obj = json.loads(text)
    except json.JSONDecodeError as e:
        logger.warning("LLM returned invalid JSON: %s | raw=%s", e, raw[:200])
        raise HTTPException(502, f"LLM returned invalid JSON: {e}")

    # 兼容两种格式:
    #   {"dimensions": [...]}
    #   [...]
    if isinstance(obj, dict) and "dimensions" in obj:
        dims_raw = obj["dimensions"]
    elif isinstance(obj, list):
        dims_raw = obj
    else:
        raise HTTPException(502, f"unexpected LLM response shape: {list(obj.keys())}")

    dimensions = []
    for d in dims_raw:
        try:
            dimensions.append(ScoreDimension(
                name=str(d["name"]),
                score=float(d["score"]),
                reason=str(d.get("reason", "")),
            ))
        except (KeyError, ValueError, TypeError) as e:
            logger.warning("invalid dimension in LLM response: %s", e)
            continue

    return dimensions


async def score_once(req: ScoreRequest) -> List[ScoreDimension]:
    """单次 LLM 评分 (不投票)."""
    input_text, output_text = extract_input_output(req.trace)

    user_prompt = USER_PROMPT_TEMPLATE.format(
        rubric=req.rubric,
        input=input_text[:4000],   # 截断防止超 token
        output=output_text[:4000],
        dimensions=", ".join(req.dimensions),
    )

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_prompt},
    ]

    raw = await call_llm(req.config, messages)
    return parse_llm_response(raw)


async def score(req: ScoreRequest) -> ScoreResponse:
    """完整评分: 多 LLM 投票 + 加权聚合."""
    start = time.time()

    # 投票: 调用 N 次, 取每个维度的平均分
    all_runs: List[List[ScoreDimension]] = []
    raw_responses: List[str] = []

    for i in range(max(1, req.config.voting_count)):
        try:
            dims = await score_once(req)
            all_runs.append(dims)
            raw_responses.append(f"vote[{i}]: {len(dims)} dimensions")
        except HTTPException as e:
            if i == 0:
                raise  # 第一次失败直接抛
            logger.warning("vote[%d] failed: %s", i, e.detail)
            raw_responses.append(f"vote[{i}]: FAILED")

    if not all_runs:
        raise HTTPException(500, "all voting attempts failed")

    # 聚合: 按维度名取平均
    by_dim: Dict[str, List[float]] = {}
    reasons: Dict[str, List[str]] = {}
    for run in all_runs:
        for d in run:
            by_dim.setdefault(d.name, []).append(d.score)
            reasons.setdefault(d.name, []).append(d.reason)

    dimensions = []
    weighted_sum = 0.0
    weight_total = 0.0
    for name, scores in by_dim.items():
        avg = sum(scores) / len(scores)
        # 取第一条 reason (避免冗长)
        reason = reasons[name][0] if reasons[name] else ""
        dimensions.append(ScoreDimension(name=name, score=avg, reason=reason))
        # 加权: 简单平均 (未来可配置)
        weighted_sum += avg
        weight_total += 1.0

    overall = weighted_sum / weight_total if weight_total > 0 else 0.0
    elapsed_ms = int((time.time() - start) * 1000)

    return ScoreResponse(
        overall=overall,
        dimensions=dimensions,
        raw_response=" | ".join(raw_responses),
        elapsed_ms=elapsed_ms,
    )


# ============================================================
# FastAPI 应用
# ============================================================

app = FastAPI(
    title="IAOEP LLM-as-Judge",
    description="LLM Judge sidecar — IAOEP Phase 2 (PR 14)",
    version="0.2.0",
)


@app.get("/health")
async def health() -> Dict[str, Any]:
    return {
        "status": "ok",
        "service": "iaoep-judge",
        "version": "0.2.0",
        "default_model": os.environ.get("IAOEP_JUDGE_LLM_MODEL", "qwen3-max"),
    }


@app.post("/score", response_model=ScoreResponse)
async def post_score(req: ScoreRequest) -> ScoreResponse:
    return await score(req)
