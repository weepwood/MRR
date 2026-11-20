-- 错误组维护最近 50 次发生时间（与 error_ids 同口径截断），
-- 供"近 24 小时频次"列与菜单角标统计，无需额外记录表。
ALTER TABLE app.system_error_event
    ADD COLUMN IF NOT EXISTS recent_seen_at TIMESTAMP WITHOUT TIME ZONE[] NOT NULL DEFAULT ARRAY[]::TIMESTAMP WITHOUT TIME ZONE[];

COMMENT ON COLUMN app.system_error_event.recent_seen_at IS '同一错误组最近 50 次发生时间，随 upsert 截断，用于近窗口频次统计';

-- 存量组回填一次最近发生时间，避免上线后频次列长时间为 0。
UPDATE app.system_error_event
SET recent_seen_at = ARRAY[last_seen_at]
WHERE CARDINALITY(recent_seen_at) = 0;
