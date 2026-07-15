-- mr_scan 可能已有数千万行。CONCURRENTLY 避免在索引扫描期间阻塞普通写入。
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS ux_mr_scan_source_record_key
    ON app.mr_scan (source_record_key)
    WHERE source_record_key IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_import_job
    ON app.mr_scan (import_job_id)
    WHERE import_job_id IS NOT NULL;
