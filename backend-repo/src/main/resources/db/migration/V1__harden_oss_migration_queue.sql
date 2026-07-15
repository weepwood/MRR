-- Harden the OSS migration queue for tens of millions of files.
-- The queue uses PostgreSQL row leases and SKIP LOCKED so multiple worker
-- threads cannot process the same scan record concurrently.

ALTER TABLE app.mr_scan
    ADD COLUMN IF NOT EXISTS migration_job_id BIGINT,
    ADD COLUMN IF NOT EXISTS migration_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS migration_next_retry TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS migration_lease_owner VARCHAR(128),
    ADD COLUMN IF NOT EXISTS migration_lease_until TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS migration_last_error TEXT;

ALTER TABLE app.migration_job
    ADD COLUMN IF NOT EXISTS max_scan_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_mr_scan_migration_job'
          AND conrelid = 'app.mr_scan'::regclass
    ) THEN
        ALTER TABLE app.mr_scan
            ADD CONSTRAINT fk_mr_scan_migration_job
            FOREIGN KEY (migration_job_id)
            REFERENCES app.migration_job (id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- At most one full migration job may be pending or running. This protects
-- against double-clicks and races between API requests.
CREATE UNIQUE INDEX IF NOT EXISTS ux_migration_job_single_active
    ON app.migration_job ((1))
    WHERE status IN ('pending', 'running');

CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_claim
    ON app.mr_scan (migration_status, migration_next_retry, id)
    WHERE uploadflag != 0
      AND (oss_url IS NULL OR oss_url = '')
      AND migration_status IS DISTINCT FROM 'failed_permanent';

CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_lease
    ON app.mr_scan (migration_lease_until, id)
    WHERE migration_status IN ('claimed', 'uploading');

CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_job
    ON app.mr_scan (migration_job_id, id)
    WHERE migration_job_id IS NOT NULL;

COMMENT ON COLUMN app.mr_scan.migration_job_id IS '当前领取该记录的迁移任务 ID';
COMMENT ON COLUMN app.mr_scan.migration_attempts IS 'OSS 迁移累计尝试次数';
COMMENT ON COLUMN app.mr_scan.migration_next_retry IS '下一次允许重试时间';
COMMENT ON COLUMN app.mr_scan.migration_lease_owner IS '当前迁移 Worker 标识';
COMMENT ON COLUMN app.mr_scan.migration_lease_until IS '领取租约到期时间，超时后允许重新领取';
COMMENT ON COLUMN app.mr_scan.migration_last_error IS '最近一次迁移失败原因';
COMMENT ON COLUMN app.migration_job.max_scan_id IS '任务创建时待迁移记录最大 ID，用于固定任务快照';
