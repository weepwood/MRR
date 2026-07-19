-- Correlate audit records with API responses and distributed traces.
ALTER TABLE app.access_log
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(32),
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS audit_result VARCHAR(16),
    ADD COLUMN IF NOT EXISTS persisted_via VARCHAR(16) DEFAULT 'DATABASE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_access_log_event_id
    ON app.access_log (event_id)
    WHERE event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_access_log_request_id
    ON app.access_log (request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_access_log_trace_id
    ON app.access_log (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_access_log_error_code
    ON app.access_log (error_code)
    WHERE error_code IS NOT NULL;

COMMENT ON COLUMN app.access_log.event_id IS '审计事件唯一标识，用于数据库和本地持久化队列去重';
COMMENT ON COLUMN app.access_log.request_id IS '返回给调用方的请求关联标识';
COMMENT ON COLUMN app.access_log.trace_id IS '分布式追踪标识';
COMMENT ON COLUMN app.access_log.error_code IS '稳定的机器可读错误码';
COMMENT ON COLUMN app.access_log.audit_result IS '审计结果: SUCCESS/FAILED';
COMMENT ON COLUMN app.access_log.persisted_via IS '首次持久化方式: DATABASE/SPOOL';
