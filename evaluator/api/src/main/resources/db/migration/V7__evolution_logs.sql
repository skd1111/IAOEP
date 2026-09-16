-- ============================================================
-- IAOEP Evaluator API - V7: Evolution Logs 表 (PR 21)
-- ============================================================

CREATE TABLE IF NOT EXISTS evolution_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL,
    suggestion_id   UUID,
    type            VARCHAR(50) NOT NULL,
    target          VARCHAR(255) NOT NULL,
    before_config   JSONB,
    after_config    JSONB,
    applied_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    applied_by      VARCHAR(255) NOT NULL,
    rolled_back_at  TIMESTAMP,
    rolled_back_by  VARCHAR(255),
    outcome         VARCHAR(50) NOT NULL DEFAULT 'success',
    note            TEXT,

    CONSTRAINT fk_evo_log_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES evolution_suggestions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_evo_log_project
    ON evolution_logs (project_id, applied_at DESC);
