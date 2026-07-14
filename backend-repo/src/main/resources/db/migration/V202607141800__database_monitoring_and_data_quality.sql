-- PostgreSQL statement statistics. The deployment user must be allowed to create extensions.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE TABLE IF NOT EXISTS mrr_data_quality_run (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    triggered_by VARCHAR(32) NOT NULL DEFAULT 'scheduled',
    check_count INTEGER NOT NULL DEFAULT 0,
    total_issues BIGINT NOT NULL DEFAULT 0,
    critical_count BIGINT NOT NULL DEFAULT 0,
    warning_count BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS mrr_data_quality_check_result (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES mrr_data_quality_run(id) ON DELETE CASCADE,
    check_code VARCHAR(80) NOT NULL,
    check_name VARCHAR(160) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    issue_count BIGINT NOT NULL DEFAULT 0,
    sampled_count INTEGER NOT NULL DEFAULT 0,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (run_id, check_code)
);

CREATE TABLE IF NOT EXISTS mrr_data_quality_issue (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES mrr_data_quality_run(id) ON DELETE CASCADE,
    check_code VARCHAR(80) NOT NULL,
    check_name VARCHAR(160) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    entity_type VARCHAR(80),
    entity_id VARCHAR(160),
    bah VARCHAR(32),
    sjh VARCHAR(32),
    detail TEXT,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_data_quality_run_started_at
    ON mrr_data_quality_run (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_quality_run_status
    ON mrr_data_quality_run (status);
CREATE INDEX IF NOT EXISTS idx_data_quality_check_run
    ON mrr_data_quality_check_result (run_id, severity, issue_count DESC);
CREATE INDEX IF NOT EXISTS idx_data_quality_issue_run
    ON mrr_data_quality_issue (run_id, severity, check_code);
CREATE INDEX IF NOT EXISTS idx_data_quality_issue_codes
    ON mrr_data_quality_issue (bah, sjh);
