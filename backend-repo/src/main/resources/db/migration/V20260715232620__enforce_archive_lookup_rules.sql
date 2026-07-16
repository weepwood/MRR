-- 病案号从 10000000 开始不保证唯一，缺少上架号时不得自动解析或创建关联。
CREATE OR REPLACE FUNCTION app.resolve_archive_id(
    p_bah TEXT,
    p_sjh TEXT,
    p_create_when_sjh_present BOOLEAN DEFAULT FALSE
)
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    normalized_bah TEXT := app.normalize_medical_record_code(p_bah);
    normalized_sjh TEXT := app.normalize_medical_record_code(p_sjh);
    bah_search_term TEXT;
    resolved_id BIGINT;
BEGIN
    IF normalized_sjh IS NOT NULL THEN
        SELECT id
        INTO resolved_id
        FROM app.mr_archive
        WHERE sjh = normalized_sjh;

        IF resolved_id IS NULL AND p_create_when_sjh_present THEN
            INSERT INTO app.mr_archive AS target (sjh, bah)
            VALUES (normalized_sjh, normalized_bah)
            ON CONFLICT (sjh) WHERE sjh IS NOT NULL DO UPDATE
            SET
                bah = COALESCE(target.bah, EXCLUDED.bah),
                updated_at = CURRENT_TIMESTAMP
            RETURNING id INTO resolved_id;
        END IF;

        RETURN resolved_id;
    END IF;

    IF normalized_bah IS NULL THEN
        RETURN NULL;
    END IF;

    IF normalized_bah ~ '^[0-9]+$' THEN
        bah_search_term := COALESCE(NULLIF(LTRIM(normalized_bah, '0'), ''), '0');
        IF bah_search_term::NUMERIC >= 10000000 THEN
            RETURN NULL;
        END IF;
    END IF;

    -- 低于阈值的历史病案号仍只在唯一匹配时关联，避免脏数据导致误合并。
    SELECT MIN(id)
    INTO resolved_id
    FROM app.mr_archive
    WHERE bah = normalized_bah
    HAVING COUNT(*) = 1;

    RETURN resolved_id;
END;
$$;

COMMENT ON FUNCTION app.resolve_archive_id(TEXT, TEXT, BOOLEAN)
    IS '按上架号优先解析；病案号达到 10000000 时必须同时提供上架号';

CREATE OR REPLACE VIEW app.v_archive_link_quality AS
SELECT
    'mr_statistics'::TEXT AS source_table,
    COUNT(*) FILTER (WHERE archive_id IS NULL) AS unlinked_count,
    COUNT(*) AS total_count
FROM app.mr_statistics
UNION ALL
SELECT
    'mr_scan'::TEXT,
    COUNT(*) FILTER (WHERE archive_id IS NULL),
    COUNT(*)
FROM app.mr_scan
UNION ALL
SELECT
    'mr_archive_box_record'::TEXT,
    COUNT(*) FILTER (WHERE archive_id IS NULL),
    COUNT(*)
FROM app.mr_archive_box_record;

COMMENT ON VIEW app.v_archive_link_quality IS '病案主键关联覆盖率检查，用于迁移验收';
