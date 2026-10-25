SET LOCAL lock_timeout='3s';
ALTER TABLE app.mr_ocr_batch ADD COLUMN case_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app.mr_ocr_batch ADD COLUMN scan_snapshot_upper INTEGER;
ALTER TABLE app.mr_smart_classification_job
    ADD COLUMN case_code VARCHAR(64),
    ADD COLUMN context_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN context_done BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN context_cursor INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app.mr_smart_classification_job_item
    ADD COLUMN page_number INTEGER,
    ADD COLUMN page_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN related_scan_id INTEGER,
    ADD COLUMN relation_status VARCHAR(16),
    ADD COLUMN relation_reviewed_by BIGINT,
    ADD COLUMN relation_reviewed_at TIMESTAMP,
    ADD CONSTRAINT ck_ocr_relation_status CHECK(relation_status IS NULL OR relation_status IN ('SUGGESTED','CONFIRMED','REJECTED'));
CREATE INDEX idx_ocr_item_page_order ON app.mr_smart_classification_job_item(job_id,page_order,scan_id);
ALTER TABLE app.mr_image_classification_suggestion
    ADD COLUMN page_number INTEGER,
    ADD COLUMN reference_scan_id INTEGER,
    ADD COLUMN context_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT ck_ocr_context_never_auto CHECK(reference_scan_id IS NULL OR NOT auto_eligible);
CREATE INDEX idx_ocr_job_case ON app.mr_smart_classification_job(batch_id,archive_id) WHERE batch_id IS NOT NULL;
CREATE TABLE app.mr_ocr_page_relation_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL REFERENCES app.mr_smart_classification_job(id) ON DELETE CASCADE,
    scan_id INTEGER NOT NULL,
    related_scan_id INTEGER NOT NULL,
    action VARCHAR(16) NOT NULL CHECK(action IN ('CONFIRMED','REJECTED')),
    operator_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
