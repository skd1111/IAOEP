-- ============================================================
-- IAOEP Evaluator API - V8: Federation Aggregates 表 (Phase 7)
-- 跨租户聚合统计 + 差分隐私
-- ============================================================

CREATE TABLE IF NOT EXISTS federation_aggregates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,            -- e.g. "model_accuracy"
    dimension       VARCHAR(200) NOT NULL,            -- e.g. "model=qwen3-max"
    true_value      DOUBLE PRECISION NOT NULL,       -- 真实值 (仅 Owner)
    noisy_value     DOUBLE PRECISION NOT NULL,       -- DP 噪声值 (对外)
    sample_size     INT NOT NULL,                     -- 参与的租户数
    tenant_hashes   JSONB NOT NULL,                   -- 租户 ID hash
    epsilon         DOUBLE PRECISION NOT NULL,        -- 隐私预算
    sensitivity     DOUBLE PRECISION NOT NULL,        -- 敏感度
    window_start    TIMESTAMP NOT NULL,
    window_end      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 索引: 按 name + 时间查询
CREATE INDEX IF NOT EXISTS idx_federation_name_time
    ON federation_aggregates (name, window_start DESC);

-- 索引: 按 dimension 查询
CREATE INDEX IF NOT EXISTS idx_federation_dimension
    ON federation_aggregates (dimension, window_start DESC);
