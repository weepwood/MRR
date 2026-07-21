-- ============================================================
-- V5: Ensure audit columns exist on access_log (schema-safe)
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'access_log' AND column_name = 'audit_action'
    ) THEN
        ALTER TABLE app.access_log ADD COLUMN audit_action TEXT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'access_log' AND column_name = 'audit_target'
    ) THEN
        ALTER TABLE app.access_log ADD COLUMN audit_target TEXT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'access_log' AND column_name = 'audit_description'
    ) THEN
        ALTER TABLE app.access_log ADD COLUMN audit_description TEXT;
    END IF;
END;
$$;
