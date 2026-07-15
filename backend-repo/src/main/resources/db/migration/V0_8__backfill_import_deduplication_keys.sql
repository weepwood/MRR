-- ============================================================
-- 历史数据导入去重键
-- ============================================================

-- mr_statistics 约 20 万行，可在迁移中一次完成。
-- 若历史表本身已有完全重复记录，只给每组最早记录写入唯一指纹，避免唯一索引冲突。
WITH normalized AS (
    SELECT
        id,
        md5(concat_ws(chr(31),
            COALESCE(app.normalize_medical_record_code(sjh), ''),
            COALESCE(app.normalize_medical_record_code(bah), ''),
            COALESCE(NULLIF(BTRIM(cid), ''), ''),
            COALESCE(app.try_parse_date(date)::TEXT, ''),
            COALESCE(NULLIF(BTRIM(type), ''), '')
        )) AS row_hash
    FROM app.mr_statistics
    WHERE source_row_hash IS NULL
), ranked AS (
    SELECT
        id,
        row_hash,
        ROW_NUMBER() OVER (PARTITION BY row_hash ORDER BY id) AS duplicate_rank
    FROM normalized
)
UPDATE app.mr_statistics s
SET source_row_hash = r.row_hash
FROM ranked r
WHERE s.id = r.id
  AND r.duplicate_rank = 1;

COMMENT ON COLUMN app.mr_statistics.source_row_hash
    IS '规范化业务字段 MD5；历史重复组仅最早记录持有指纹，新导入据此防止重复';

-- mr_scan 可能达到 3000 万行，只创建游标批处理函数，不在 Flyway 中更新全表。
CREATE OR REPLACE FUNCTION app.backfill_scan_source_record_keys(
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
        SELECT
            s.id,
            s.folder,
            s.brxh,
            s.bah,
            s.filename
        FROM app.mr_scan s
        WHERE s.id > p_after_id
          AND s.source_record_key IS NULL
        ORDER BY s.id
        LIMIT p_batch_size
    ), normalized AS (
        SELECT
            b.id,
            CASE
                WHEN NULLIF(BTRIM(b.folder), '') IS NULL
                  OR NULLIF(BTRIM(b.brxh), '') IS NULL
                  OR NULLIF(BTRIM(b.filename), '') IS NULL
                    THEN NULL
                ELSE md5(concat_ws(chr(31),
                    BTRIM(b.folder),
                    BTRIM(b.brxh),
                    COALESCE(app.normalize_medical_record_code(b.bah), ''),
                    BTRIM(b.filename)
                ))
            END AS record_key
        FROM batch b
    ), ranked AS (
        SELECT
            n.id,
            n.record_key,
            ROW_NUMBER() OVER (PARTITION BY n.record_key ORDER BY n.id) AS duplicate_rank
        FROM normalized n
        WHERE n.record_key IS NOT NULL
    ), updated AS (
        UPDATE app.mr_scan s
        SET source_record_key = r.record_key
        FROM ranked r
        WHERE s.id = r.id
          AND r.duplicate_rank = 1
          AND NOT EXISTS (
              SELECT 1
              FROM app.mr_scan existing
              WHERE existing.source_record_key = r.record_key
          )
        RETURNING s.id
    )
    SELECT
        COALESCE((SELECT MAX(id) FROM batch), p_after_id)::INTEGER,
        COALESCE((SELECT COUNT(*) FROM batch), 0)::INTEGER,
        COALESCE((SELECT COUNT(*) FROM updated), 0)::INTEGER;
END;
$$;

COMMENT ON FUNCTION app.backfill_scan_source_record_keys(INTEGER, INTEGER)
    IS '按 id 游标分批回填扫描记录去重键；重复历史行保持 NULL，避免覆盖或唯一冲突';

COMMENT ON COLUMN app.mr_scan.source_record_key
    IS '文件夹、病人序号、病案号和文件名生成的 MD5；用于 CSV 重复导入保护';
