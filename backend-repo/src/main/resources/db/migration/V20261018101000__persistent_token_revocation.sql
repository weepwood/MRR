CREATE TABLE app.token_revocation (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_token_revocation_expiry ON app.token_revocation(expires_at);
COMMENT ON TABLE app.token_revocation IS '持久化 JWT 撤销事实；仅保存不可逆 jti 摘要，重启不恢复已登出会话';
