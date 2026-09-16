-- ============================================================
-- IAOEP Evaluator API - V2 索引 (PR 15: 评测任务调度)
-- ============================================================

-- 调度器扫描: 查 pending 任务, 按 created_at 排序
CREATE INDEX IF NOT EXISTS idx_job_pending_created
    ON evaluation_jobs (created_at)
    WHERE status = 'pending';

-- 状态过滤 + 时间排序 (项目内列表)
CREATE INDEX IF NOT EXISTS idx_job_project_status_created
    ON evaluation_jobs (project_id, status, created_at DESC);

-- 数据集级联删除保护 (Phase 2 暂用 SET NULL, 已加外键)
-- 不需要新索引
