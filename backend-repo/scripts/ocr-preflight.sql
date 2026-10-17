-- psql -v after_id=0 -v upper_id=2147483647 -v sample_limit=2000 -f ocr-preflight.sql
-- 只读、有限范围检查；只输出聚合数量，不输出患者字段。不是全库精确统计。
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
SET LOCAL lock_timeout = '3s';
WITH candidates AS MATERIALIZED (
    SELECT id, archive_id, bah, sjh
    FROM app.mr_scan
    WHERE id > :after_id AND id <= :upper_id AND btype = 0 AND uploadflag <> 0
    ORDER BY id LIMIT LEAST(GREATEST(:sample_limit, 1), 10000)
)
SELECT COUNT(*) AS sampled_unclassified,
       COUNT(*) FILTER (WHERE s.archive_id IS NULL) AS missing_archive,
       COUNT(*) FILTER (WHERE s.archive_id IS NOT NULL AND a.id IS NULL) AS orphan_archive,
       COUNT(*) FILTER (WHERE a.id IS NOT NULL AND
           (NULLIF(BTRIM(s.bah), '') IS DISTINCT FROM a.bah OR
            NULLIF(BTRIM(s.sjh), '') IS DISTINCT FROM a.sjh)) AS code_conflicts,
       COALESCE(MAX(s.id), :after_id) AS next_after_id
FROM candidates s LEFT JOIN app.mr_archive a ON a.id = s.archive_id;
ROLLBACK;
