SET LOCAL lock_timeout = '3s';

ALTER TABLE app.mr_scan_ocr
    ADD COLUMN result_version VARCHAR(36),
    ADD COLUMN model_checksum CHAR(64),
    ADD COLUMN preprocessing_version VARCHAR(32),
    ADD COLUMN lines_json JSONB NOT NULL DEFAULT '[]'::JSONB,
    ADD COLUMN image_width INTEGER,
    ADD COLUMN image_height INTEGER;

CREATE TABLE app.mr_scan_ocr_history (
    result_version VARCHAR(36) PRIMARY KEY,
    scan_id INTEGER NOT NULL,
    archive_id BIGINT,
    content_hash CHAR(64),
    engine_version VARCHAR(128),
    model_checksum CHAR(64),
    preprocessing_version VARCHAR(32),
    ocr_text TEXT,
    lines_json JSONB NOT NULL,
    confidence NUMERIC(5,4),
    image_width INTEGER,
    image_height INTEGER,
    processed_at TIMESTAMP,
    archived_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ocr_history_scan ON app.mr_scan_ocr_history(scan_id, archived_at DESC);

ALTER TABLE app.mr_smart_classification_job
    ADD COLUMN owner_user_id BIGINT,
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD COLUMN selected_scan_id INTEGER,
    ADD COLUMN config_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
    ADD COLUMN lease_token VARCHAR(36),
    ADD COLUMN lease_until TIMESTAMP,
    ADD COLUMN cursor_id INTEGER NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_ocr_job_idempotency
    ON app.mr_smart_classification_job(owner_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
ALTER TABLE app.mr_smart_classification_job_item
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP,
    ADD COLUMN ocr_result_version VARCHAR(36);

-- ADMIN permissions are resolved dynamically from Permissions.ALL_PERMISSIONS as well.
UPDATE app.mr_auth_role SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'ocr:text-read')
WHERE code = 'ADMIN' AND POSITION('ocr:text-read' IN COALESCE(permissions,'')) = 0;
