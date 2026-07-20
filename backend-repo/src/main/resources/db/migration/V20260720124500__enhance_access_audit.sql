-- 完善单机全接口访问审计：保留真实 URI、接口模板、请求链路和异常信息。
ALTER TABLE app.access_log
    ADD COLUMN IF NOT EXISTS request_id TEXT,
    ADD COLUMN IF NOT EXISTS endpoint_template TEXT,
    ADD COLUMN IF NOT EXISTS error_message TEXT;

COMMENT ON COLUMN app.access_log.request_id IS '单次请求链路 ID，与 X-Request-Id 响应头对应';
COMMENT ON COLUMN app.access_log.request_uri IS '客户端实际访问路径，包含路径参数但不包含 Query String';
COMMENT ON COLUMN app.access_log.endpoint_template IS 'Spring MVC 匹配的接口模板，用于按接口聚合统计';
COMMENT ON COLUMN app.access_log.query_string IS '可审计查询参数；密码、令牌、密钥、签名等凭据值保存 SHA-256 摘要';
COMMENT ON COLUMN app.access_log.request_body IS '可审计文本请求体；敏感凭据字段保存摘要，二进制内容不入库';
COMMENT ON COLUMN app.access_log.referer IS '请求来源页面；业务参数保留，敏感凭据值保存摘要';
COMMENT ON COLUMN app.access_log.error_message IS '请求处理异常的类型与截断消息';

CREATE UNIQUE INDEX IF NOT EXISTS uk_access_log_request_id
    ON app.access_log (request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_access_log_endpoint_template
    ON app.access_log (endpoint_template);

CREATE INDEX IF NOT EXISTS idx_access_log_audit_target
    ON app.access_log (audit_target)
    WHERE audit_target IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_access_log_error_status_time
    ON app.access_log (access_time DESC, response_status)
    WHERE response_status LIKE '4%' OR response_status LIKE '5%';
