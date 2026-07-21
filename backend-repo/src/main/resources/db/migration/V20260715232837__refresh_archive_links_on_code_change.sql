-- 当扫描记录或装箱记录的 BAH/SJH 被修正时，必须重新解析 archive_id，
-- 不能继续保留旧病案关联。
CREATE OR REPLACE FUNCTION app.resolve_scan_archive_reference()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.bah := app.normalize_medical_record_code(NEW.bah);
    NEW.sjh := app.normalize_medical_record_code(NEW.sjh);

    IF TG_OP = 'INSERT'
       OR NEW.archive_id IS NULL
       OR NEW.bah IS DISTINCT FROM OLD.bah
       OR NEW.sjh IS DISTINCT FROM OLD.sjh THEN
        NEW.archive_id := app.resolve_archive_id(NEW.bah, NEW.sjh, NEW.sjh IS NOT NULL);
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION app.resolve_box_archive_reference()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.bah := app.normalize_medical_record_code(NEW.bah);
    NEW.sjh := app.normalize_medical_record_code(NEW.sjh);

    IF TG_OP = 'INSERT'
       OR NEW.archive_id IS NULL
       OR NEW.bah IS DISTINCT FROM OLD.bah
       OR NEW.sjh IS DISTINCT FROM OLD.sjh THEN
        NEW.archive_id := app.resolve_archive_id(NEW.bah, NEW.sjh, NEW.sjh IS NOT NULL);
    END IF;

    RETURN NEW;
END;
$$;
