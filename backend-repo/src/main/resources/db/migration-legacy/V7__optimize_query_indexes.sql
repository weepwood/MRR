-- Indexes aligned with the application's actual PostgreSQL query patterns.

-- username already has an index created by its UNIQUE constraint.
DROP INDEX IF EXISTS app.idx_mr_auth_user_username;

-- Statistics filters and sorting normalize the legacy TEXT date value first.
CREATE INDEX IF NOT EXISTS idx_mr_statistics_date_normalized
    ON app.mr_statistics ((replace(date, '/', '-')));

-- Exact patient lookup by medical record number.
CREATE INDEX IF NOT EXISTS idx_mr_patient_bah
    ON app.mr_patient (bah);

-- Folder lookup is ordered by id in ScanMapper.
CREATE INDEX IF NOT EXISTS idx_mr_scan_folder_id
    ON app.mr_scan (folder, id);

-- Global and per-folder OSS migration batches only read pending rows in id order.
CREATE INDEX IF NOT EXISTS idx_mr_scan_pending_id
    ON app.mr_scan (id)
    WHERE uploadflag <> 0
      AND (oss_url IS NULL OR oss_url = '');

CREATE INDEX IF NOT EXISTS idx_mr_scan_pending_folder_id
    ON app.mr_scan (folder, id)
    WHERE uploadflag <> 0
      AND (oss_url IS NULL OR oss_url = '');

-- Latest migration result for a scan.
CREATE INDEX IF NOT EXISTS idx_migration_scan_created
    ON app.image_migration_log (scan_id, created_at DESC);
