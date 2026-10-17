-- 修复编号更新后保留旧 archive_id；仅作用于之后的写入，不批量改写历史扫描。
CREATE OR REPLACE FUNCTION app.resolve_scan_archive_reference()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.archive_id IS NULL THEN
            NEW.archive_id := app.resolve_archive_id(
                app.normalize_medical_record_code(NEW.bah),
                app.normalize_medical_record_code(NEW.sjh),
                app.normalize_medical_record_code(NEW.sjh) IS NOT NULL);
        END IF;
    ELSIF NEW.bah IS DISTINCT FROM OLD.bah
       OR NEW.sjh IS DISTINCT FROM OLD.sjh
       OR NEW.archive_id IS NULL THEN
        NEW.archive_id := app.resolve_archive_id(
            app.normalize_medical_record_code(NEW.bah),
            app.normalize_medical_record_code(NEW.sjh),
            app.normalize_medical_record_code(NEW.sjh) IS NOT NULL);
    END IF;
    IF NEW.archive_id IS NOT NULL AND app.normalize_medical_record_code(NEW.bah) IS NOT NULL
       AND EXISTS (SELECT 1 FROM app.mr_archive a WHERE a.id = NEW.archive_id
                   AND a.bah IS NOT NULL
                   AND app.numeric_code_key(a.bah) IS DISTINCT FROM app.numeric_code_key(NEW.bah)) THEN
        -- SJH 命中不代表 BAH 也一致；冲突保留原值并进入未关联待排查状态。
        NEW.archive_id := NULL;
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION app.resolve_scan_archive_reference()
    IS '保留原始编号；编号发生变化时重新解析唯一主档，无法唯一解析则清除旧关联';
