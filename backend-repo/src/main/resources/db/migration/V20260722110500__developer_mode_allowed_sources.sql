UPDATE mr_system_settings
SET description = '开发者模式：仅允许可信客户端通过本机 Nginx 只读访问旧版影像档案袋'
WHERE setting_key = 'developerModeEnabled';

INSERT INTO mr_system_settings (
    setting_key,
    setting_value,
    description,
    updated_by
)
VALUES (
    'developerModeAllowedSources',
    E'127.0.0.1\n::1',
    '开发者模式允许访问的客户端单 IP 或 CIDR 网段，每行一个',
    'flyway'
)
ON CONFLICT (setting_key) DO NOTHING;
