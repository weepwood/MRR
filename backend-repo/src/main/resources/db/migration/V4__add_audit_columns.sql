-- ============================================================
-- V4: 访问日志增加审计字段
-- ============================================================

ALTER TABLE app.access_log ADD COLUMN IF NOT EXISTS audit_action      TEXT;
ALTER TABLE app.access_log ADD COLUMN IF NOT EXISTS audit_target      TEXT;
ALTER TABLE app.access_log ADD COLUMN IF NOT EXISTS audit_description TEXT;

COMMENT ON COLUMN app.access_log.audit_action      IS '审计动作类型: LIST/DOWNLOAD/VIEW_IMAGE/VIEW_OSS_IMAGE';
COMMENT ON COLUMN app.access_log.audit_target      IS '审计目标（病案号/图片ID）';
COMMENT ON COLUMN app.access_log.audit_description IS '审计描述';
