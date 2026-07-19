-- 已知种子账号的默认密码写在仓库初始化资料中。
-- 仅当用户名与默认 BCrypt 哈希同时匹配时强制改密，避免影响已经修改过密码的正式账号。
UPDATE app.mr_auth_user
SET must_change_password = TRUE,
    temporary_password_expires_at = NULL,
    password_changed_at = NULL,
    updated_at = NOW()
WHERE (username = 'br_admin' AND password_hash = '$2a$12$1MBK2EugjPA4k45jzanZwOfP8WAHFW7Upb.EnqBIWhFoYp3tKx4za')
   OR (username = 'admin' AND password_hash = '$2a$12$NiQwMT54aA1Un.FOjzVWR.5J/PDIGQHwp0nMEGatrdCerqfSvbHqS')
   OR (username = 'doctor1' AND password_hash = '$2a$12$Y2mlZdwNkLhUj5PeKKXVPuqlXW/HUX9rJ0uWtqOQQDm0ipmBYB21q')
   OR (username = 'nurse1' AND password_hash = '$2a$12$YUwhuNhN4rj0p6jZP6RcoOD/IbVx/uZuhB597kngo/L3fxZL5vNbS');

COMMENT ON COLUMN app.mr_auth_user.must_change_password IS
    '首次登录或管理员重置后是否必须修改密码；公开默认凭据仍未变更的种子账号也会被强制改密';
