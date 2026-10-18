-- 运行错误采集和可恢复日志采集共享投影收据，开关切换及失败重试不重复计数。
CREATE TABLE app.runtime_error_projection_receipt (
    event_id UUID PRIMARY KEY,
    projected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE app.runtime_error_projection_receipt IS '错误事件幂等投影收据；与错误聚合同语句提交，长期保留';
