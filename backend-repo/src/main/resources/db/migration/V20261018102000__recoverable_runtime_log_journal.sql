CREATE TABLE app.runtime_log_source (
    source_key VARCHAR(64) PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    byte_offset BIGINT NOT NULL DEFAULT 0,
    observed_bytes BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    failure_code VARCHAR(64),
    invalid_lines BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app.runtime_log_event (
    occurred_at TIMESTAMPTZ NOT NULL,
    event_id UUID NOT NULL,
    level VARCHAR(8) NOT NULL,
    logger_name VARCHAR(255) NOT NULL,
    request_id VARCHAR(80),
    error_id VARCHAR(32),
    message_summary VARCHAR(2100) NOT NULL,
    stack_trace TEXT NOT NULL DEFAULT '',
    source_key VARCHAR(64) NOT NULL,
    source_offset BIGINT NOT NULL,
    projected BOOLEAN NOT NULL,
    PRIMARY KEY (occurred_at, event_id)
) PARTITION BY RANGE (occurred_at);
CREATE INDEX idx_runtime_log_request ON app.runtime_log_event(request_id, occurred_at DESC,event_id DESC);
CREATE INDEX idx_runtime_log_level ON app.runtime_log_event(level, occurred_at DESC, event_id DESC);
CREATE INDEX idx_runtime_log_logger ON app.runtime_log_event(logger_name,occurred_at DESC,event_id DESC);
CREATE INDEX idx_runtime_log_error ON app.runtime_log_event(error_id,occurred_at DESC,event_id DESC) WHERE error_id IS NOT NULL;
CREATE INDEX idx_runtime_log_pending_projection ON app.runtime_log_event(source_key) WHERE NOT projected;
COMMENT ON TABLE app.runtime_log_event IS '脱敏运行日志明细；按月分区，长期保留，禁止自动套用访问日志清理策略';
CREATE TABLE app.runtime_log_capacity_sample (
    measured_at TIMESTAMPTZ PRIMARY KEY,
    database_bytes BIGINT NOT NULL,
    buffer_bytes BIGINT NOT NULL,
    disk_free_bytes BIGINT NOT NULL,
    possible_gap BOOLEAN NOT NULL
);
