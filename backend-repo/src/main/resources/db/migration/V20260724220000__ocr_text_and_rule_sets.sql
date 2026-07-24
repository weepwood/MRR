-- OCR 文本事实表与可版本化规则字典。
-- 本迁移不会启动历史全量 OCR，也不会修改 mr_scan.btype。

CREATE TABLE IF NOT EXISTS app.mr_scan_ocr (
    scan_id          INTEGER PRIMARY KEY REFERENCES app.mr_scan (id) ON DELETE CASCADE,
    archive_id       BIGINT,
    status           VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    engine           VARCHAR(64),
    engine_version   VARCHAR(128),
    ocr_text         TEXT,
    confidence       NUMERIC(5,4),
    text_length      INTEGER NOT NULL DEFAULT 0,
    content_hash     CHAR(64),
    error_code       VARCHAR(64),
    error_message    VARCHAR(2000),
    processed_at     TIMESTAMP WITHOUT TIME ZONE,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_scan_ocr_status CHECK (status IN (
        'PENDING', 'PROCESSING', 'SUCCEEDED', 'LOW_CONFIDENCE',
        'FAILED', 'SKIPPED', 'STALE', 'INTERRUPTED'
    )),
    CONSTRAINT ck_mr_scan_ocr_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    CONSTRAINT ck_mr_scan_ocr_text_length CHECK (text_length >= 0)
);

CREATE INDEX IF NOT EXISTS idx_mr_scan_ocr_archive_status
    ON app.mr_scan_ocr (archive_id, status, scan_id);
CREATE INDEX IF NOT EXISTS idx_mr_scan_ocr_content_hash
    ON app.mr_scan_ocr (content_hash) WHERE content_hash IS NOT NULL;

COMMENT ON TABLE app.mr_scan_ocr IS '单张影像当前 OCR 事实结果；分类和病案内搜索统一复用';
COMMENT ON COLUMN app.mr_scan_ocr.archive_id IS '病案主档 ID；在 archive_id 质量基线完成后补充外键校验';
COMMENT ON COLUMN app.mr_scan_ocr.content_hash IS '源文件 SHA-256；变化后将结果标记为 STALE';

CREATE TABLE IF NOT EXISTS app.mr_ocr_lexicon_set (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    version         VARCHAR(64) NOT NULL UNIQUE,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    checksum        CHAR(64),
    created_by      VARCHAR(64) NOT NULL,
    published_by    VARCHAR(64),
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_ocr_lexicon_set_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mr_ocr_lexicon_single_published
    ON app.mr_ocr_lexicon_set ((status)) WHERE status = 'PUBLISHED';

CREATE TABLE IF NOT EXISTS app.mr_ocr_lexicon_entry (
    id                  BIGSERIAL PRIMARY KEY,
    lexicon_set_id      BIGINT NOT NULL REFERENCES app.mr_ocr_lexicon_set (id) ON DELETE CASCADE,
    term                VARCHAR(256) NOT NULL,
    aliases             JSONB NOT NULL DEFAULT '[]'::JSONB,
    common_ocr_errors   JSONB NOT NULL DEFAULT '[]'::JSONB,
    target_type         INTEGER,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    remark              VARCHAR(1000),
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_ocr_lexicon_target_type CHECK (
        target_type IS NULL OR (target_type >= 1 AND target_type <= 15)
    ),
    CONSTRAINT uk_mr_ocr_lexicon_entry UNIQUE (lexicon_set_id, term)
);

CREATE INDEX IF NOT EXISTS idx_mr_ocr_lexicon_entry_set_enabled
    ON app.mr_ocr_lexicon_entry (lexicon_set_id, enabled, target_type);

CREATE TABLE IF NOT EXISTS app.mr_classification_rule_set (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    version         VARCHAR(64) NOT NULL UNIQUE,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    checksum        CHAR(64),
    created_by      VARCHAR(64) NOT NULL,
    published_by    VARCHAR(64),
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITHOUT TIME ZONE,
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_classification_rule_set_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mr_classification_rule_single_published
    ON app.mr_classification_rule_set ((status)) WHERE status = 'PUBLISHED';

CREATE TABLE IF NOT EXISTS app.mr_classification_rule_entry (
    id              BIGSERIAL PRIMARY KEY,
    rule_set_id     BIGINT NOT NULL REFERENCES app.mr_classification_rule_set (id) ON DELETE CASCADE,
    target_type     INTEGER NOT NULL,
    keyword         VARCHAR(500) NOT NULL,
    match_scope     VARCHAR(16) NOT NULL DEFAULT 'ANY',
    match_mode      VARCHAR(16) NOT NULL DEFAULT 'CONTAINS',
    weight          NUMERIC(8,3) NOT NULL DEFAULT 1,
    negative        BOOLEAN NOT NULL DEFAULT FALSE,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    priority        INTEGER NOT NULL DEFAULT 100,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_mr_classification_rule_target_type CHECK (target_type >= 1 AND target_type <= 15),
    CONSTRAINT ck_mr_classification_rule_scope CHECK (match_scope IN ('TITLE', 'BODY', 'ANY')),
    CONSTRAINT ck_mr_classification_rule_mode CHECK (match_mode IN ('CONTAINS', 'EXACT', 'REGEX')),
    CONSTRAINT ck_mr_classification_rule_priority CHECK (priority >= 0),
    CONSTRAINT uk_mr_classification_rule_entry UNIQUE (
        rule_set_id, target_type, keyword, match_scope, match_mode, negative
    )
);

CREATE INDEX IF NOT EXISTS idx_mr_classification_rule_set_type
    ON app.mr_classification_rule_entry (rule_set_id, enabled, target_type, priority);
