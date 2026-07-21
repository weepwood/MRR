\set ON_ERROR_STOP on

-- 1. Flyway 迁移是否已应用
SELECT installed_rank, version, description, success
FROM app.flyway_schema_history
WHERE version IN ('0.0.1', '0.1', '0.2', '0.3', '0.4')
ORDER BY installed_rank;

-- 2. 旧库兼容迁移必须为统计表提供非空且唯一的行标识
SELECT
    COUNT(*) FILTER (WHERE id IS NULL) AS null_id_count,
    COUNT(*) - COUNT(DISTINCT id) AS duplicate_id_count
FROM app.mr_statistics;

-- 3. 非空上架号必须唯一
SELECT sjh, COUNT(*) AS duplicate_count
FROM app.mr_archive
WHERE sjh IS NOT NULL
GROUP BY sjh
HAVING COUNT(*) > 1;

-- 4. 查看三张业务表的病案主键关联覆盖率
SELECT *
FROM app.v_archive_link_quality
ORDER BY source_table;

-- 5. 检查已经关联的记录是否指向不存在的病案
SELECT 'mr_statistics' AS source_table, COUNT(*) AS broken_links
FROM app.mr_statistics s
LEFT JOIN app.mr_archive a ON a.id = s.archive_id
WHERE s.archive_id IS NOT NULL AND a.id IS NULL
UNION ALL
SELECT 'mr_scan', COUNT(*)
FROM app.mr_scan s
LEFT JOIN app.mr_archive a ON a.id = s.archive_id
WHERE s.archive_id IS NOT NULL AND a.id IS NULL
UNION ALL
SELECT 'mr_archive_box_record', COUNT(*)
FROM app.mr_archive_box_record b
LEFT JOIN app.mr_archive a ON a.id = b.archive_id
WHERE b.archive_id IS NOT NULL AND a.id IS NULL;

-- 6. 查看仍未验证的外键；完成批量回填并确认无脏数据后再 VALIDATE。
SELECT
    conrelid::regclass AS table_name,
    conname,
    convalidated
FROM pg_constraint
WHERE conname IN (
    'fk_mr_statistics_archive',
    'fk_mr_scan_archive',
    'fk_archive_box_record_archive'
)
ORDER BY table_name::TEXT;

-- 7. 抽样检查病案汇总
SELECT *
FROM app.v_archive_summary
ORDER BY id
LIMIT 20;

-- mr_scan 回填示例：在运维脚本中保存 last_id 并循环调用，直到 scanned_count = 0。
-- SELECT * FROM app.backfill_scan_archive_ids(0, 10000);
-- SELECT * FROM app.backfill_scan_archive_ids(:last_id, 10000);

-- 全量回填完成后执行：
-- ALTER TABLE app.mr_statistics VALIDATE CONSTRAINT fk_mr_statistics_archive;
-- ALTER TABLE app.mr_scan VALIDATE CONSTRAINT fk_mr_scan_archive;
-- ALTER TABLE app.mr_archive_box_record VALIDATE CONSTRAINT fk_archive_box_record_archive;
