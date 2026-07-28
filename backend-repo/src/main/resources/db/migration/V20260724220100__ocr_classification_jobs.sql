-- OCR 智能分类任务、建议与正式类型变更审计。

CREATE TABLE IF NOT EXISTS app.mr_smart_classification_job (
    id                      VARCHAR(36) PRIMARY KEY,
    archive_id              BIGINT NOT NULL,
    scope                   VARCHAR(24) NOT NULL DEFAULT 'UNCLASSIFIED',
    status                  VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    planned_count           INTEGER NOT NULL DEFAULT 0,
    processed_count         INTEGER NOT NULL DEFAULT 0,
    ocr_success_count       INTEGER NOT NULL DEFAULT 0,
    suggested_count         INTEGER NOT NULL DEFAULT 0,
    low_confidence_count    INTEGER NOT NULL DEFAULT 0,
    no_match_count          INTEGER NOT NULL DEFAULT 0,
    failed_count            INTEGER NOT NULL DEFAULT 0,
    rule_set_version        VARCHAR(64) NOT NULL,
    lexicon_version         VARCHAR(64) NOT NULL,
    created_by              VARCHAR(64) NOT NULL,
    started_at              TIMESTAMP WITHOUT TIME ZONE,
    finished_at             TIMESTAMP WITHOUT TIME ZONE,
    cancel_requested        BOOLEAN NOT NULL DEFAULT FALSE,
    error_message           VARCHAR(2000),
    created_at              TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_smart_classification_job_scope CHECK (
        scope IN ('UNCLASSIFIED', 'OCR_FAILED', 'LOW_CONFIDENCE', 'ALL')
    ),
    CONSTRAINT ck_mr_smart_classification_job_status CHECK (
        status IN ('PENDING', 'RUNNING', 'PARTIAL_SUCCESS', 'SUCCESS',
                   'FAILED', 'CANCEL_REQUESTED', 'CANCELLED', 'INTERRUPTED')
    ),
    CONSTRAINT ck_mr_smart_classification_job_counts CHECK (
        planned_count >= 0 AND processed_count >= 0 AND ocr_success_count >= 0
        AND suggested_count >= 0 AND low_confidence_count >= 0
        AND no_match_count >= 0 AND failed_count >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mr_smart_classification_active_archive
    ON app.mr_smart_classification_job (archive_id)
    WHERE status IN ('PENDING', 'RUNNING', 'CANCEL_REQUESTED');
CREATE INDEX IF NOT EXISTS idx_mr_smart_classification_job_status_created
    ON app.mr_smart_classification_job (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mr_smart_classification_job_creator
    ON app.mr_smart_classification_job (created_by, created_at DESC);

CREATE TABLE IF NOT EXISTS app.mr_smart_classification_job_item (
    job_id              VARCHAR(36) NOT NULL REFERENCES app.mr_smart_classification_job (id) ON DELETE CASCADE,
    scan_id             INTEGER NOT NULL REFERENCES app.mr_scan (id) ON DELETE CASCADE,
    original_type       INTEGER NOT NULL DEFAULT 0,
    content_hash        CHAR(64),
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ocr_status          VARCHAR(24),
    suggested_type      INTEGER,
    rule_score          NUMERIC(10,4),
    ocr_confidence      NUMERIC(5,4),
    failure_code        VARCHAR(64),
    failure_message     VARCHAR(2000),
    processed_at        TIMESTAMP WITHOUT TIME ZONE,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (job_id, scan_id),
    CONSTRAINT ck_mr_smart_classification_item_original_type CHECK (original_type >= 0 AND original_type <= 15),
    CONSTRAINT ck_mr_smart_classification_item_suggested_type CHECK (
        suggested_type IS NULL OR (suggested_type >= 1 AND suggested_type <= 15)
    ),
    CONSTRAINT ck_mr_smart_classification_item_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUGGESTED', 'LOW_CONFIDENCE',
                   'NO_MATCH', 'FAILED', 'SKIPPED', 'CANCELLED')
    ),
    CONSTRAINT ck_mr_smart_classification_item_ocr_confidence CHECK (
        ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1)
    )
);

CREATE INDEX IF NOT EXISTS idx_mr_smart_classification_item_job_status
    ON app.mr_smart_classification_job_item (job_id, status, scan_id);

CREATE TABLE IF NOT EXISTS app.mr_image_classification_suggestion (
    id                  BIGSERIAL PRIMARY KEY,
    scan_id             INTEGER NOT NULL REFERENCES app.mr_scan (id) ON DELETE CASCADE,
    job_id              VARCHAR(36) NOT NULL REFERENCES app.mr_smart_classification_job (id) ON DELETE CASCADE,
    original_type       INTEGER NOT NULL,
    suggested_type      INTEGER NOT NULL,
    rule_score          NUMERIC(10,4) NOT NULL,
    ocr_confidence      NUMERIC(5,4),
    matched_rules       JSONB NOT NULL DEFAULT '[]'::JSONB,
    excluded_rules      JSONB NOT NULL DEFAULT '[]'::JSONB,
    review_status       VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    reviewed_by         VARCHAR(64),
    reviewed_at         TIMESTAMP WITHOUT TIME ZONE,
    final_type          INTEGER,
    rule_set_version    VARCHAR(64) NOT NULL,
    lexicon_version     VARCHAR(64) NOT NULL,
    ocr_content_hash    CHAR(64),
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_image_classification_original_type CHECK (original_type >= 0 AND original_type <= 15),
    CONSTRAINT ck_mr_image_classification_suggested_type CHECK (suggested_type >= 1 AND suggested_type <= 15),
    CONSTRAINT ck_mr_image_classification_final_type CHECK (
        final_type IS NULL OR (final_type >= 0 AND final_type <= 15)
    ),
    CONSTRAINT ck_mr_image_classification_ocr_confidence CHECK (
        ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1)
    ),
    CONSTRAINT ck_mr_image_classification_review_status CHECK (
        review_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'OVERRIDDEN', 'SUPERSEDED')
    ),
    CONSTRAINT uk_mr_image_classification_job_scan UNIQUE (job_id, scan_id)
);

CREATE INDEX IF NOT EXISTS idx_mr_image_classification_scan_status
    ON app.mr_image_classification_suggestion (scan_id, review_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mr_image_classification_job_status
    ON app.mr_image_classification_suggestion (job_id, review_status, id);

CREATE TABLE IF NOT EXISTS app.mr_image_type_audit (
    id                  BIGSERIAL PRIMARY KEY,
    scan_id             INTEGER NOT NULL REFERENCES app.mr_scan (id) ON DELETE RESTRICT,
    archive_id          BIGINT,
    old_type            INTEGER NOT NULL,
    suggested_type      INTEGER,
    new_type            INTEGER NOT NULL,
    operation           VARCHAR(32) NOT NULL,
    job_id              VARCHAR(36),
    rule_set_version    VARCHAR(64),
    rule_score          NUMERIC(10,4),
    ocr_confidence      NUMERIC(5,4),
    operator_user_id    INTEGER,
    operator_username   VARCHAR(64) NOT NULL,
    request_id          VARCHAR(64),
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_image_type_audit_old_type CHECK (old_type >= 0 AND old_type <= 15),
    CONSTRAINT ck_mr_image_type_audit_suggested_type CHECK (
        suggested_type IS NULL OR (suggested_type >= 1 AND suggested_type <= 15)
    ),
    CONSTRAINT ck_mr_image_type_audit_new_type CHECK (new_type >= 0 AND new_type <= 15),
    CONSTRAINT ck_mr_image_type_audit_operation CHECK (
        operation IN ('MANUAL_EDIT', 'ACCEPT_SUGGESTION', 'BATCH_ACCEPT', 'OVERRIDE_SUGGESTION')
    ),
    CONSTRAINT ck_mr_image_type_audit_ocr_confidence CHECK (
        ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1)
    )
);

CREATE INDEX IF NOT EXISTS idx_mr_image_type_audit_scan_created
    ON app.mr_image_type_audit (scan_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_mr_image_type_audit_job
    ON app.mr_image_type_audit (job_id) WHERE job_id IS NOT NULL;

COMMENT ON TABLE app.mr_smart_classification_job IS '单病案 OCR 智能分类持久化任务；同病案只允许一个活动任务';
COMMENT ON TABLE app.mr_image_classification_suggestion IS '分类建议与正式 mr_scan.btype 分离保存，必须经审核接口确认';
COMMENT ON TABLE app.mr_image_type_audit IS '图片正式类型变更审计；操作者必须来自后端认证上下文';
