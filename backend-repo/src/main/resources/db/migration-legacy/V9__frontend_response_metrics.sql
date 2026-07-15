CREATE TABLE IF NOT EXISTS app.frontend_response_metric (
    request_id         VARCHAR(64) PRIMARY KEY,
    route_pattern      VARCHAR(255) NOT NULL,
    method             VARCHAR(10) NOT NULL,
    http_status        INTEGER,
    business_code      INTEGER,
    success            BOOLEAN NOT NULL,
    client_duration_ms BIGINT NOT NULL CHECK (client_duration_ms >= 0),
    server_duration_ms BIGINT CHECK (server_duration_ms >= 0),
    occurred_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_frontend_response_metric_occurred_at
    ON app.frontend_response_metric (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_frontend_response_metric_route_time
    ON app.frontend_response_metric (route_pattern, method, occurred_at DESC);
