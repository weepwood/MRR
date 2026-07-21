ALTER TABLE app.mr_scan
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(16) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN IF NOT EXISTS source_node VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_ref TEXT;

COMMENT ON COLUMN app.mr_scan.source_type IS '图片来源类型: AUTO/LOCAL/NAS/HTTP/OSS';
COMMENT ON COLUMN app.mr_scan.source_node IS '受控 NAS/HTTP 节点 ID，不保存任意主机地址';
COMMENT ON COLUMN app.mr_scan.source_ref IS '来源内相对路径或 OSS Object Key，不允许任意绝对路径';

CREATE INDEX IF NOT EXISTS idx_mr_scan_source_type ON app.mr_scan (source_type);
CREATE INDEX IF NOT EXISTS idx_mr_scan_source_node ON app.mr_scan (source_node) WHERE source_node IS NOT NULL;

CREATE TABLE IF NOT EXISTS app.archive_export_job (
    id                 UUID PRIMARY KEY,
    owner_user_id      BIGINT,
    owner_username     VARCHAR(64) NOT NULL,
    format             VARCHAR(8) NOT NULL,
    scope              VARCHAR(32) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    bah                VARCHAR(64),
    sjh                VARCHAR(64),
    scan_ids           TEXT,
    planned_count      INTEGER NOT NULL DEFAULT 0,
    processed_count    INTEGER NOT NULL DEFAULT 0,
    failed_count       INTEGER NOT NULL DEFAULT 0,
    estimated_bytes    BIGINT NOT NULL DEFAULT 0,
    output_bytes       BIGINT NOT NULL DEFAULT 0,
    source_summary     VARCHAR(128),
    file_name          VARCHAR(255),
    file_path          TEXT,
    sha256             VARCHAR(64),
    cancel_requested   BOOLEAN NOT NULL DEFAULT FALSE,
    error_message      TEXT,
    idempotency_key    VARCHAR(128),
    expires_at         TIMESTAMP WITHOUT TIME ZONE,
    created_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at         TIMESTAMP WITHOUT TIME ZONE,
    completed_at       TIMESTAMP WITHOUT TIME ZONE,
    updated_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_export_job_format CHECK (format IN ('ZIP', 'PDF')),
    CONSTRAINT chk_archive_export_job_scope CHECK (scope IN ('WHOLE_ARCHIVE', 'SELECTED_IMAGES')),
    CONSTRAINT chk_archive_export_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_archive_export_job_counts CHECK (
        planned_count >= 0 AND processed_count >= 0 AND failed_count >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_archive_export_job_idempotency
    ON app.archive_export_job (owner_username, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND status <> 'EXPIRED';

CREATE INDEX IF NOT EXISTS idx_archive_export_job_owner_created
    ON app.archive_export_job (owner_username, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_archive_export_job_status_updated
    ON app.archive_export_job (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_archive_export_job_expires
    ON app.archive_export_job (expires_at)
    WHERE expires_at IS NOT NULL;

COMMENT ON TABLE app.archive_export_job IS '病案 ZIP/PDF 后台导出任务，仅保存任务元数据，文件保存在受控临时目录';
