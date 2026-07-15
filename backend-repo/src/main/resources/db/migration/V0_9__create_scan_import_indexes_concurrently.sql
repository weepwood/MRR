-- mr_scan 可能已有数千万行。CONCURRENTLY 避免在索引扫描期间阻塞普通写入。
-- 并发构建若被异常中断，PostgreSQL 可能留下同名无效索引；先删除再创建，确保 Flyway 重试可恢复。
DROP INDEX CONCURRENTLY IF EXISTS app.ux_mr_scan_source_record_key;
CREATE UNIQUE INDEX CONCURRENTLY ux_mr_scan_source_record_key
    ON app.mr_scan (source_record_key)
    WHERE source_record_key IS NOT NULL;

DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_import_job;
CREATE INDEX CONCURRENTLY idx_mr_scan_import_job
    ON app.mr_scan (import_job_id)
    WHERE import_job_id IS NOT NULL;
