-- 统一数据关系工作台和质量检查中的编号兼容比较规则。
-- 保留原始编号；仅在比较时把纯数字的前导零差异折叠为同一个 key。

CREATE OR REPLACE FUNCTION app.numeric_code_key(p_value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
SELECT CASE
    WHEN NULLIF(BTRIM(p_value), '') IS NULL THEN NULL
    WHEN BTRIM(p_value) ~ '^[0-9]+$'
        THEN COALESCE(NULLIF(LTRIM(BTRIM(p_value), '0'), ''), '0')
    ELSE BTRIM(p_value)
END
$$;

COMMENT ON FUNCTION app.numeric_code_key(TEXT)
    IS '病案号/上架号比较键：空白转 NULL，纯数字忽略前导零，其他编号仅去除首尾空格';

-- 这些表规模相对可控，可直接增加表达式索引。
-- mr_scan 约三千万行，不在 Flyway 中同步创建表达式索引，避免阻塞生产迁移。
CREATE INDEX IF NOT EXISTS idx_mr_archive_bah_match_key
    ON app.mr_archive (app.numeric_code_key(bah));

CREATE INDEX IF NOT EXISTS idx_mr_archive_sjh_match_key
    ON app.mr_archive (app.numeric_code_key(sjh));

CREATE INDEX IF NOT EXISTS idx_mr_statistics_bah_match_key
    ON app.mr_statistics (app.numeric_code_key(bah));

CREATE INDEX IF NOT EXISTS idx_mr_statistics_sjh_match_key
    ON app.mr_statistics (app.numeric_code_key(sjh));

CREATE INDEX IF NOT EXISTS idx_mr_patient_bah_match_key
    ON app.mr_patient (app.numeric_code_key(bah));

CREATE INDEX IF NOT EXISTS idx_archive_box_bah_match_key
    ON app.mr_archive_box_record (app.numeric_code_key(bah));

CREATE INDEX IF NOT EXISTS idx_archive_box_sjh_match_key
    ON app.mr_archive_box_record (app.numeric_code_key(sjh));
