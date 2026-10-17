SET LOCAL lock_timeout = '3s';
ALTER TABLE app.mr_scan ADD COLUMN type_version BIGINT NOT NULL DEFAULT 0;
CREATE FUNCTION app.bump_scan_type_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.btype IS DISTINCT FROM OLD.btype THEN
        NEW.type_version := OLD.type_version + 1;
    ELSIF NEW.type_version IS DISTINCT FROM OLD.type_version AND NEW.type_version <> OLD.type_version + 1 THEN
        RAISE EXCEPTION 'invalid type version';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_scan_type_version BEFORE UPDATE OF btype,type_version ON app.mr_scan
FOR EACH ROW EXECUTE FUNCTION app.bump_scan_type_version();

ALTER TABLE app.mr_smart_classification_job_item ADD COLUMN original_type_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app.mr_image_classification_suggestion
    ADD COLUMN original_type_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ocr_result_version VARCHAR(36),
    ADD COLUMN auto_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reason_code VARCHAR(32);
ALTER TABLE app.mr_image_type_audit
    ALTER COLUMN operator_user_id TYPE BIGINT,
    ADD COLUMN type_version BIGINT,
    ADD COLUMN ocr_result_version VARCHAR(36),
    ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'HUMAN';
CREATE INDEX idx_type_audit_latest ON app.mr_image_type_audit(scan_id,type_version DESC);

-- A published version is immutable at the API boundary. Restoring a version creates a new draft.
ALTER TABLE app.mr_classification_rule_set ADD COLUMN created_by_id BIGINT;
ALTER TABLE app.mr_ocr_lexicon_set ADD COLUMN created_by_id BIGINT;
