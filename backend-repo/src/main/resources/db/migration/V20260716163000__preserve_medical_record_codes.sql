-- ============================================================
-- 保留病案号和上架号的原始格式
--
-- 编号同时参与本地影像目录定位，不能将短数字自动补齐为 8 位。
-- 本迁移只改变后续规范化和扫描记录触发器行为，不尝试删除既有前导零，
-- 因为无法可靠区分原始前导零和历史自动补零。
-- ============================================================

CREATE OR REPLACE FUNCTION app.normalize_medical_record_code(p_value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
SELECT NULLIF(BTRIM(p_value), '')
$$;

COMMENT ON FUNCTION app.normalize_medical_record_code(TEXT)
    IS '规范化病案号/上架号：仅去除首尾空格并将空白转为 NULL；保留原始长度和前导零';

-- mr_scan 中的 bah/sjh 是影像定位信息的一部分。触发器只解析 archive_id，
-- 不再把规范化后的编号写回扫描记录，避免改变真实文件夹路径。
CREATE OR REPLACE FUNCTION app.resolve_scan_archive_reference()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    normalized_bah TEXT;
    normalized_sjh TEXT;
BEGIN
    normalized_bah := app.normalize_medical_record_code(NEW.bah);
    normalized_sjh := app.normalize_medical_record_code(NEW.sjh);

    IF NEW.archive_id IS NULL THEN
        NEW.archive_id := app.resolve_archive_id(
            normalized_bah,
            normalized_sjh,
            normalized_sjh IS NOT NULL
        );
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION app.resolve_scan_archive_reference()
    IS '根据病案号和上架号解析 archive_id，但不改写 mr_scan 中用于影像路径定位的原始编号';

-- 迁移期自检：短数字不补零，合法前导零保持不变，空白仍转换为 NULL。
DO $$
BEGIN
    IF app.normalize_medical_record_code('123') IS DISTINCT FROM '123' THEN
        RAISE EXCEPTION '短编号被意外补零';
    END IF;

    IF app.normalize_medical_record_code('00000123') IS DISTINCT FROM '00000123' THEN
        RAISE EXCEPTION '原始前导零被意外修改';
    END IF;

    IF app.normalize_medical_record_code('   ') IS NOT NULL THEN
        RAISE EXCEPTION '空白编号未转换为 NULL';
    END IF;
END;
$$;
