-- 将 mr_patient 病区字段由错误拼写 binqu 更正为 bingqu。
-- 保留上一版本迁移不变，避免已执行 Flyway 迁移的数据库出现校验和冲突。

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'mr_patient'
          AND column_name = 'binqu'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'mr_patient'
          AND column_name = 'bingqu'
    ) THEN
        ALTER TABLE app.mr_patient RENAME COLUMN binqu TO bingqu;
    END IF;
END
$$;

COMMENT ON COLUMN app.mr_patient.bingqu IS '病区';
