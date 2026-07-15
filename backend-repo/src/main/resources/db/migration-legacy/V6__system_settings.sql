-- ============================================================
-- V6: 创建系统设置表（键值对存储）
-- ============================================================

CREATE TABLE IF NOT EXISTS app.mr_system_settings (
    id              SERIAL PRIMARY KEY,
    setting_key     VARCHAR(128) NOT NULL UNIQUE,
    setting_value   TEXT,
    description     VARCHAR(512),
    updated_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64)
);

COMMENT ON TABLE  app.mr_system_settings             IS '系统设置表 — 键值对存储系统级配置';
COMMENT ON COLUMN app.mr_system_settings.setting_key  IS '设置键（唯一）';
COMMENT ON COLUMN app.mr_system_settings.setting_value IS '设置值';
COMMENT ON COLUMN app.mr_system_settings.description  IS '设置项说明';
COMMENT ON COLUMN app.mr_system_settings.updated_by   IS '最后修改用户';
