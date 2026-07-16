CREATE OR REPLACE FUNCTION app.backfill_scan_archive_ids(
    p_after_id INTEGER DEFAULT 0,
    p_batch_size INTEGER DEFAULT 10000
)
RETURNS TABLE(last_id INTEGER, scanned_count INTEGER, updated_count INTEGER)
LANGUAGE plpgsql
AS $$
BEGIN
    IF p_batch_size < 1 OR p_batch_size > 100000 THEN
        RAISE EXCEPTION 'p_batch_size must be between 1 and 100000';
    END IF;

    RETURN QUERY
    WITH batch AS (
        SELECT s.id, s.bah, s.sjh
        FROM app.mr_scan s
        WHERE s.id > p_after_id
          AND s.archive_id IS NULL
        ORDER BY s.id
        LIMIT p_batch_size
    ),
    resolved AS (
        SELECT
            b.id,
            app.resolve_archive_id(
                b.bah,
                b.sjh,
                app.normalize_medical_record_code(b.sjh) IS NOT NULL
            ) AS archive_id
        FROM batch b
    ),
    updated AS (
        UPDATE app.mr_scan s
        SET archive_id = r.archive_id
        FROM resolved r
        WHERE s.id = r.id
          AND r.archive_id IS NOT NULL
        RETURNING s.id
    )
    SELECT
        COALESCE((SELECT MAX(b.id) FROM batch b), p_after_id),
        (SELECT COUNT(*)::INTEGER FROM batch),
        (SELECT COUNT(*)::INTEGER FROM updated);
END;
$$;

COMMENT ON FUNCTION app.backfill_scan_archive_ids(INTEGER, INTEGER)
    IS '按主键游标回填未关联扫描记录；有效上架号可创建最小病案主数据';
