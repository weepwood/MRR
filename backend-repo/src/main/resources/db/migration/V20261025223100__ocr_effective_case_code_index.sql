-- Non-transactional concurrent index; only this new index is owned by this migration.
SET lock_timeout='3s';
DROP INDEX CONCURRENTLY IF EXISTS app.idx_archive_ocr_case_code;
CREATE INDEX CONCURRENTLY idx_archive_ocr_case_code ON app.mr_archive
    ((COALESCE(NULLIF(BTRIM(sjh),''),NULLIF(BTRIM(bah),''))));
RESET lock_timeout;
