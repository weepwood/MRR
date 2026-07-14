CREATE TABLE IF NOT EXISTS app.system_availability_period (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_availability_status
        CHECK (status IN ('UP', 'DOWN')),
    CONSTRAINT ck_system_availability_period_time
        CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_system_availability_open_period
    ON app.system_availability_period ((1))
    WHERE ended_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_system_availability_started_at
    ON app.system_availability_period (started_at DESC);

COMMENT ON TABLE app.system_availability_period IS 'MRR 服务运行状态区间，仅在状态切换时新增记录';
COMMENT ON COLUMN app.system_availability_period.last_heartbeat_at IS '后端最后一次成功写入心跳的时间，用于重启后推断停机区间';
