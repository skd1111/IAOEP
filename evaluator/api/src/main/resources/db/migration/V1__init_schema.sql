-- ============================================================
-- IAOEP Evaluator API - V1 初始化 Schema
-- Phase 2 (PR 12): Golden Dataset 表
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- 用于 gen_random_uuid()

-- ============================================================
-- Golden Datasets (PR 12)
-- ============================================================
CREATE TABLE IF NOT EXISTS golden_datasets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL,
    name        VARCHAR(255) NOT NULL,
    version     VARCHAR(50)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    cases       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    metadata    JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),

    -- 同一项目同名同版本只能存在一个
    CONSTRAINT uq_dataset_project_name_version UNIQUE (project_id, name, version)
);

CREATE INDEX IF NOT EXISTS idx_dataset_project
    ON golden_datasets (project_id);

CREATE INDEX IF NOT EXISTS idx_dataset_active
    ON golden_datasets (project_id, name)
    WHERE is_active = TRUE;

-- ============================================================
-- Evaluation Jobs (PR 15 占位)
-- ============================================================
CREATE TABLE IF NOT EXISTS evaluation_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL,
    dataset_id      UUID,
    agent_version   VARCHAR(100),
    judge_config    JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    progress        FLOAT NOT NULL DEFAULT 0.0,
    total_cases     INT NOT NULL DEFAULT 0,
    completed_cases INT NOT NULL DEFAULT 0,
    result          JSONB,
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_by      VARCHAR(255),

    CONSTRAINT fk_job_dataset FOREIGN KEY (dataset_id)
        REFERENCES golden_datasets(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_job_project_status
    ON evaluation_jobs (project_id, status);

-- ============================================================
-- Evolution Suggestions (PR 16 / Phase 3 占位)
-- ============================================================
CREATE TABLE IF NOT EXISTS evolution_suggestions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID NOT NULL,
    job_id        UUID,
    type          VARCHAR(50) NOT NULL,
    target        VARCHAR(255),
    description   TEXT,
    expected_roi  FLOAT,
    risk_level    VARCHAR(20) NOT NULL DEFAULT 'medium',
    status        VARCHAR(50) NOT NULL DEFAULT 'proposed',
    ab_test_id    UUID,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_suggestion_project_status
    ON evolution_suggestions (project_id, status);

-- ============================================================
-- Notification Channels (PR 17 占位)
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_channels (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50) NOT NULL,            -- wechat_work / dingtalk / feishu
    webhook_url TEXT NOT NULL,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    events      VARCHAR(255)[] NOT NULL DEFAULT '{}',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_channel_project_name UNIQUE (project_id, name)
);
