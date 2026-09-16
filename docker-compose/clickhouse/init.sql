-- ============================================================
-- IAOEP ClickHouse Schema (Phase 5: A/B Test Group 字段)
-- ============================================================

CREATE DATABASE IF NOT EXISTS iaoep;

-- ============================================================
-- Traces 主表 (所有 Span 数据) — Phase 5 加 ab_test_group
-- ============================================================
CREATE TABLE IF NOT EXISTS iaoep.traces (
    tenant_id          LowCardinality(String),
    trace_id           String,
    span_id            String,
    parent_span_id     String DEFAULT '',
    span_name          LowCardinality(String),        -- agent.run / llm.call / tool.execute / agent.turn
    span_kind          LowCardinality(String),        -- INTERNAL / CLIENT / SERVER

    -- 时间
    start_time         DateTime64(9),
    end_time           DateTime64(9),
    duration_ms        UInt32,

    -- Agent 字段
    agent_name         LowCardinality(String) DEFAULT '',
    session_id         String DEFAULT '',
    user_id            String DEFAULT '',
    skill_name         LowCardinality(String) DEFAULT '',

    -- LLM 字段
    llm_system         LowCardinality(String) DEFAULT '',  -- openai/dashscope/glm/deepseek/anthropic
    llm_model          LowCardinality(String) DEFAULT '',
    llm_input_tokens   UInt32 DEFAULT 0,
    llm_output_tokens  UInt32 DEFAULT 0,

    -- Tool 字段
    tool_name          LowCardinality(String) DEFAULT '',
    tool_call_id       String DEFAULT '',
    tool_error_type    LowCardinality(String) DEFAULT '',

    -- 成本
    cost_cny           Float64 DEFAULT 0,

    -- 状态
    status             LowCardinality(String),        -- success / error / timeout
    error_message      String DEFAULT '',

    -- Phase 5: A/B Test 字段
    ab_test_name       LowCardinality(String) DEFAULT '',
    ab_test_group      LowCardinality(String) DEFAULT '',  -- baseline / candidate

    -- 灵活扩展字段
    tags               Map(String, String) DEFAULT map(),
    attributes         Map(String, String) DEFAULT map(),

    -- Span events (LLM 输入输出 / 工具结果)
    events             Array(Tuple(
        timestamp   DateTime64(9),
        name        String,
        attributes  Map(String, String)
    )) DEFAULT []
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(start_time)
ORDER BY (tenant_id, start_time, trace_id)
TTL start_time + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- 索引: 按 trace_id 快速查询
ALTER TABLE iaoep.traces ADD INDEX IF NOT EXISTS idx_trace_id trace_id TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE iaoep.traces ADD INDEX IF NOT EXISTS idx_user_id user_id TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE iaoep.traces ADD INDEX IF NOT EXISTS idx_session_id session_id TYPE bloom_filter(0.01) GRANULARITY 4;

-- Phase 5: A/B Test 索引
ALTER TABLE iaoep.traces ADD INDEX IF NOT EXISTS idx_ab_test
    (ab_test_name, ab_test_group) TYPE set(100) GRANULARITY 4;

-- ============================================================
-- Metrics (OTLP Metrics 直存)
-- ============================================================
CREATE TABLE IF NOT EXISTS iaoep.metrics (
    tenant_id          LowCardinality(String),
    metric_name        LowCardinality(String),
    timestamp          DateTime64(9),
    value              Float64,
    labels             Map(String, String) DEFAULT map()
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, metric_name, timestamp)
TTL timestamp + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- ============================================================
-- Logs (OTLP Logs 直存, Phase 2 加入)
-- ============================================================
CREATE TABLE IF NOT EXISTS iaoep.logs (
    tenant_id          LowCardinality(String),
    trace_id           String DEFAULT '',
    span_id            String DEFAULT '',
    severity           LowCardinality(String),        -- INFO / WARN / ERROR
    timestamp          DateTime64(9),
    body               String,
    attributes         Map(String, String) DEFAULT map()
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(timestamp)
ORDER BY (tenant_id, timestamp)
TTL timestamp + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- ============================================================
-- 物化视图: 每小时每 (tenant, agent, skill, ab_group) 聚合
-- ============================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS iaoep.traces_hourly
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(hour)
ORDER BY (tenant_id, hour, agent_name, span_name, ab_test_group, status)
AS
SELECT
    tenant_id,
    toStartOfHour(start_time) AS hour,
    agent_name,
    span_name,
    ab_test_group,
    status,
    count() AS span_count,
    sum(duration_ms) AS total_duration_ms,
    sum(llm_input_tokens + llm_output_tokens) AS total_tokens,
    sum(cost_cny) AS total_cost_cny
FROM iaoep.traces
GROUP BY tenant_id, hour, agent_name, span_name, ab_test_group, status;
