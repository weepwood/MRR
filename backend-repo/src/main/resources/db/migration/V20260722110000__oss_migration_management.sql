-- OSS 分阶段迁移管理
-- 当前尚未开始正式迁移，因此只补充任务控制与失败状态字段，不回填三千万级 mr_scan 数据。

ALTER TABLE app.mr_scan
    ADD COLUMN IF NOT EXISTS migration_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS migration_error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS migration_next_retry_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS migration_updated_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE app.migration_job
    ALTER COLUMN status TYPE VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mode VARCHAR(16) NOT NULL DEFAULT 'pilot',
    ADD COLUMN IF NOT EXISTS scope_value TEXT,
    ADD COLUMN IF NOT EXISTS requested_count BIGINT,
    ADD COLUMN IF NOT EXISTS max_scan_id INTEGER,
    ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;

-- PostgreSQL 负责兜底，保证应用误触或重复请求时同一时刻仍只有一个活动任务。
CREATE UNIQUE INDEX IF NOT EXISTS uk_migration_job_single_active
    ON app.migration_job ((1))
    WHERE status IN ('pending', 'running', 'cancelling');

CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_retry
    ON app.mr_scan (migration_next_retry_at)
    WHERE migration_status = 'retry_wait';

COMMENT ON COLUMN app.mr_scan.migration_attempts IS 'OSS 迁移尝试次数，成功或人工重置后清零';
COMMENT ON COLUMN app.mr_scan.migration_error_code IS '最近一次迁移错误分类，不保存敏感凭据';
COMMENT ON COLUMN app.mr_scan.migration_next_retry_at IS '临时失败允许再次处理的时间';
COMMENT ON COLUMN app.mr_scan.migration_updated_at IS '迁移状态最后更新时间';
COMMENT ON COLUMN app.migration_job.mode IS '任务模式: pilot/batch/full/retry';
COMMENT ON COLUMN app.migration_job.scope_value IS '可选迁移范围，当前保存 folder';
COMMENT ON COLUMN app.migration_job.requested_count IS '管理员请求处理的最大记录数';
COMMENT ON COLUMN app.migration_job.max_scan_id IS '任务创建时的扫描记录快照上界';
COMMENT ON COLUMN app.migration_job.cancel_requested IS '管理员是否请求安全取消任务';
