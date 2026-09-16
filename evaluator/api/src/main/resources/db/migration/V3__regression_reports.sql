-- ============================================================
-- IAOEP Evaluator API - V3: Regression Report 表 (PR 16)
-- ============================================================

CREATE TABLE IF NOT EXISTS regression_reports (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL,
    baseline_job_id     UUID NOT NULL,
    candidate_job_id    UUID NOT NULL,
    baseline_version    VARCHAR(100),
    candidate_version   VARCHAR(100),
    status              VARCHAR(50) NOT NULL,
    overall_delta       FLOAT,
    threshold           FLOAT NOT NULL DEFAULT 0.05,
    result              JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),

    CONSTRAINT fk_regression_baseline FOREIGN KEY (baseline_job_id)
        REFERENCES evaluation_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_regression_candidate FOREIGN KEY (candidate_job_id)
        REFERENCES evaluation_jobs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_regression_project
    ON regression_reports (project_id, created_at DESC);
