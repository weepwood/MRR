SET LOCAL lock_timeout='3s';
CREATE TABLE app.mr_ocr_batch (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RUNNING' CHECK(status IN ('RUNNING','PAUSED','CANCEL_REQUESTED','CANCELLED','SUCCESS','PARTIAL_SUCCESS','FAILED')),
    after_id INTEGER NOT NULL CHECK(after_id>=0),
    upper_id INTEGER NOT NULL CHECK(upper_id>=after_id),
    cursor_id INTEGER NOT NULL,
    max_pages INTEGER NOT NULL DEFAULT 10000 CHECK(max_pages BETWEEN 1 AND 10000),
    dispatched_count INTEGER NOT NULL DEFAULT 0 CHECK(dispatched_count>=0),
    processed_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    config_snapshot JSONB NOT NULL,
    rule_set_version VARCHAR(64) NOT NULL,
    lexicon_version VARCHAR(64) NOT NULL,
    auto_apply BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(64),
    discovery_done BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(owner_user_id,idempotency_key)
);
CREATE INDEX idx_ocr_batch_active ON app.mr_ocr_batch(status,created_at);
ALTER TABLE app.mr_smart_classification_job ADD COLUMN batch_id VARCHAR(36) REFERENCES app.mr_ocr_batch(id);
CREATE INDEX idx_ocr_job_batch ON app.mr_smart_classification_job(batch_id,status) WHERE batch_id IS NOT NULL;
UPDATE app.mr_auth_role SET permissions=CONCAT_WS(',',NULLIF(permissions,''),'ocr:batch-run')
WHERE code='ADMIN' AND POSITION('ocr:batch-run' IN COALESCE(permissions,''))=0;
