-- ============================================================
-- IAOEP Evaluator API - V4: Evolution Suggestion 字段补全 (PR 18)
-- Phase 1 V1 已建表占位, 此处补 type 枚举 + 索引
-- ============================================================

-- Evolution Suggestion 字段 (PR 18)
-- V1 表已建, 此处补 diff 字段 (JSONB)
ALTER TABLE evolution_suggestions
    ALTER COLUMN diff TYPE JSONB USING diff::jsonb;

-- A/B Test 字段 (PR 19 占位, V1 已存部分)
ALTER TABLE evolution_suggestions
    ALTER COLUMN ab_test_id TYPE UUID USING ab_test_id::uuid;

-- 索引: 按 risk_level + expected_roi 排序 (审批列表用)
CREATE INDEX IF NOT EXISTS idx_suggestion_risk_roi
    ON evolution_suggestions (project_id, risk_level, expected_roi DESC NULLS LAST);

-- 索引: 按 status 过滤
CREATE INDEX IF NOT EXISTS idx_suggestion_status
    ON evolution_suggestions (project_id, status);
