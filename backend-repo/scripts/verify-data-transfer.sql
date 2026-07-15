\set ON_ERROR_STOP on

DO $$
BEGIN
    IF to_regclass('app.data_transfer_job') IS NULL THEN
        RAISE EXCEPTION 'app.data_transfer_job is missing';
    END IF;
    IF to_regclass('app.data_transfer_file') IS NULL THEN
        RAISE EXCEPTION 'app.data_transfer_file is missing';
    END IF;
    IF to_regclass('app.data_transfer_error') IS NULL THEN
        RAISE EXCEPTION 'app.data_transfer_error is missing';
    END IF;
    IF to_regclass('app.stg_mr_statistics_import') IS NULL THEN
        RAISE EXCEPTION 'app.stg_mr_statistics_import is missing';
    END IF;
    IF to_regclass('app.stg_mr_scan_import') IS NULL THEN
        RAISE EXCEPTION 'app.stg_mr_scan_import is missing';
    END IF;
    IF to_regclass('app.v_data_transfer_job_summary') IS NULL THEN
        RAISE EXCEPTION 'app.v_data_transfer_job_summary is missing';
    END IF;
END;
$$;

DO $$
DECLARE
    statistics_persistence "char";
    scan_persistence "char";
BEGIN
    SELECT relpersistence INTO statistics_persistence
    FROM pg_class
    WHERE oid = 'app.stg_mr_statistics_import'::regclass;

    SELECT relpersistence INTO scan_persistence
    FROM pg_class
    WHERE oid = 'app.stg_mr_scan_import'::regclass;

    IF statistics_persistence <> 'u' OR scan_persistence <> 'u' THEN
        RAISE EXCEPTION 'staging tables must be UNLOGGED';
    END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'v_data_transfer_job_summary'
          AND column_name = 'options'
    ) THEN
        RAISE EXCEPTION 'job summary view does not expose options';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'app'
          AND table_name = 'stg_mr_scan_import'
          AND column_name = 'file_size_raw'
    ) THEN
        RAISE EXCEPTION 'legacy scan file_size staging column is missing';
    END IF;
END;
$$;

DO $$
BEGIN
    IF to_regprocedure('app.backfill_scan_source_record_keys(integer,integer)') IS NULL THEN
        RAISE EXCEPTION 'scan source key backfill function is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE n.nspname = 'app'
          AND c.relname = 'ux_mr_statistics_source_row_hash'
          AND i.indisvalid
          AND i.indisready
    ) THEN
        RAISE EXCEPTION 'statistics deduplication index is missing or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE n.nspname = 'app'
          AND c.relname = 'ux_mr_scan_source_record_key'
          AND i.indisvalid
          AND i.indisready
    ) THEN
        RAISE EXCEPTION 'scan deduplication index is missing or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE n.nspname = 'app'
          AND c.relname = 'idx_mr_scan_import_job'
          AND i.indisvalid
          AND i.indisready
    ) THEN
        RAISE EXCEPTION 'scan import job index is missing or invalid';
    END IF;
END;
$$;

SELECT
    'data-transfer-schema-ok' AS result,
    (SELECT COUNT(*) FROM app.data_transfer_job) AS existing_jobs,
    (SELECT COUNT(*) FROM app.mr_statistics WHERE source_row_hash IS NOT NULL) AS statistics_keys,
    (SELECT COUNT(*) FROM app.mr_scan WHERE source_record_key IS NOT NULL) AS scan_keys;
