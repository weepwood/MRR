-- Reset br_admin password to br_password
-- PasswordUtil.sha256('br_password')
INSERT INTO app.mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES (
    'br_admin',
    'System Administrator',
    'c6c49412188f4bd8969b7f3997afe001df2cfe77a15e7bb115f102be0a9849cd',
    'ADMIN',
    'active'
)
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    role_code = EXCLUDED.role_code,
    status = EXCLUDED.status;
