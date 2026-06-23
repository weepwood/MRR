-- ============================================================
-- V3: 迁移任务表 — 支持批量 OSS 迁移任务管理
-- ============================================================

CREATE TABLE IF NOT EXISTS app.migration_job (
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    total_count     BIGINT          NOT NULL DEFAULT 0,
    processed_count BIGINT          NOT NULL DEFAULT 0,
    failed_count    BIGINT          NOT NULL DEFAULT 0,
    rate            NUMERIC(5,2)    DEFAULT 0,
    error_message   TEXT,
    created_by      TEXT,
    started_at      TIMESTAMP WITHOUT TIME ZONE,
    completed_at    TIMESTAMP WITHOUT TIME ZONE,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_job_status     ON app.migration_job (status);
CREATE INDEX IF NOT EXISTS idx_migration_job_created_at ON app.migration_job (created_at DESC);

COMMENT ON TABLE  app.migration_job IS 'OSS 迁移任务表 — 追踪批量迁移任务执行状态';
COMMENT ON COLUMN app.migration_job.status          IS '任务状态: pending/running/completed/failed/cancelled';
COMMENT ON COLUMN app.migration_job.total_count     IS '待迁移文件总数';
COMMENT ON COLUMN app.migration_job.processed_count IS '已处理文件数';
COMMENT ON COLUMN app.migration_job.failed_count    IS '失败文件数';
COMMENT ON COLUMN app.migration_job.rate            IS '完成百分比';
COMMENT ON COLUMN app.migration_job.created_by      IS '任务创建者（用户名）';
