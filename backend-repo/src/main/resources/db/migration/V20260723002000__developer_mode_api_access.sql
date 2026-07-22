DO $$
BEGIN
    IF to_regclass('app.system_setting') IS NOT NULL THEN
        INSERT INTO app.system_setting (setting_key, setting_value, description, updated_by)
        VALUES (
            'developerModeApiAccessEnabled',
            'false',
            '开发者模式下是否允许可信来源以无 Token 开发身份访问受保护 API；默认关闭',
            'system'
        )
        ON CONFLICT (setting_key) DO NOTHING;
    END IF;
END
$$;
