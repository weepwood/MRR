-- Reset br_admin password to br_password
-- PasswordUtil.encode('br_password') — bcrypt
INSERT INTO app.mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES (
    'br_admin',
    'System Administrator',
    '$2a$12$1MBK2EugjPA4k45jzanZwOfP8WAHFW7Upb.EnqBIWhFoYp3tKx4za',
    'ADMIN',
    'active'
)
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    role_code = EXCLUDED.role_code,
    status = EXCLUDED.status;
