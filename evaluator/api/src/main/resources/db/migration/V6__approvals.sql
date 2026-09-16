-- ============================================================
-- IAOEP Evaluator API - V6: Approvals 表 (PR 20)
-- ============================================================

CREATE TABLE IF NOT EXISTS approvals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL,
    suggestion_id   UUID NOT NULL,
    decision        VARCHAR(50) NOT NULL,              -- approved / rejected
    comment         TEXT,
    decided_by      VARCHAR(255) NOT NULL,
    decided_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    is_high_risk    BOOLEAN NOT NULL DEFAULT FALSE,
    signature_index INT,
    metadata        JSONB,

    CONSTRAINT fk_approval_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES evolution_suggestions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_approval_suggestion
    ON approvals (suggestion_id, decided_at);

-- 同一 suggestion 同一用户只能签一次 (防重复)
CREATE UNIQUE INDEX IF NOT EXISTS idx_approval_user_suggestion
    ON approvals (suggestion_id, decided_by);
