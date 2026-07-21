DROP INDEX IF EXISTS app.uk_archive_export_job_idempotency;

CREATE UNIQUE INDEX IF NOT EXISTS uk_archive_export_job_user_idempotency
    ON app.archive_export_job (owner_user_id, idempotency_key)
    WHERE owner_user_id IS NOT NULL
      AND idempotency_key IS NOT NULL
      AND status <> 'EXPIRED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_archive_export_job_legacy_owner_idempotency
    ON app.archive_export_job (owner_username, idempotency_key)
    WHERE owner_user_id IS NULL
      AND idempotency_key IS NOT NULL
      AND status <> 'EXPIRED';

CREATE INDEX IF NOT EXISTS idx_archive_export_job_owner_user_created
    ON app.archive_export_job (owner_user_id, created_at DESC)
    WHERE owner_user_id IS NOT NULL;

COMMENT ON COLUMN app.archive_export_job.owner_user_id IS
    '不可变任务所有者用户ID；新任务鉴权和幂等均以此字段为准';
