-- ============================================================
-- IAOEP Evaluator API - V5: A/B Test 表 (PR 19)
-- ============================================================

CREATE TABLE IF NOT EXISTS ab_tests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL,
    name                VARCHAR(255),
    suggestion_id       UUID,
    baseline_version    VARCHAR(100) NOT NULL,
    candidate_version   VARCHAR(100) NOT NULL,
    traffic_split       JSONB NOT NULL DEFAULT '{"baseline": 0.5, "candidate": 0.5}'::jsonb,
    status              VARCHAR(50) NOT NULL DEFAULT 'pending',
    baseline_score      FLOAT,
    candidate_score     FLOAT,
    decision            VARCHAR(50),
    decision_reason     TEXT,
    started_at          TIMESTAMP,
    decided_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),

    CONSTRAINT fk_abtest_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES evolution_suggestions(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_abtest_project_status
    ON ab_tests (project_id, status, created_at DESC);
