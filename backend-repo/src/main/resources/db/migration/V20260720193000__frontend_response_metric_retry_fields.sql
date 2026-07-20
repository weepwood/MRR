ALTER TABLE frontend_response_metric
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count BETWEEN 0 AND 5),
    ADD COLUMN IF NOT EXISTS retry_outcome VARCHAR(16) CHECK (
        retry_outcome IS NULL OR retry_outcome IN ('succeeded', 'failed', 'canceled')
    );

COMMENT ON COLUMN frontend_response_metric.retry_count IS '浏览器为该请求执行的自动重试次数';
COMMENT ON COLUMN frontend_response_metric.retry_outcome IS '发生重试后的最终结果：succeeded、failed、canceled';
