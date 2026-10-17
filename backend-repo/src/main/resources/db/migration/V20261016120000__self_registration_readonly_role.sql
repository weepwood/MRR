-- 自助注册账号的默认角色收紧为只读。
--
-- 背景：register() 原先把申请单落库为 DOCTOR。虽然审核通过需要管理员显式指定角色，
-- 但「仅把 pending 改成 active」这类操作会沿用既有 role_code，等于让自助注册者直接
-- 获得病历编辑与下载能力，不符合最小权限原则。
--
-- 本迁移新增只读角色 READER（仅 record:read），由注册流程作为 pending 账号的默认角色。
-- 管理员在审核通过时仍可显式指定其它角色。
INSERT INTO app.mr_auth_role (code, name, description, permissions, sort_order)
VALUES
    ('READER', 'Read Only', 'Self-registered accounts: read medical records only until an administrator grants more', 'record:read', 10)
ON CONFLICT (code) DO NOTHING;
