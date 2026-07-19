INSERT INTO mr_system_settings (
    setting_key,
    setting_value,
    description,
    updated_by
)
VALUES (
    'developerModeEnabled',
    'false',
    '开发者模式：允许无有效 JWT 的受保护 API 使用虚拟管理员会话，并开放任意 Origin 跨域调试',
    'flyway'
)
ON CONFLICT (setting_key) DO NOTHING;
