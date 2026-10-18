-- This migration is non-transactional (see companion .conf). Invalid interrupted indexes are rebuilt.
SET lock_timeout='3s';
DROP INDEX CONCURRENTLY IF EXISTS app.idx_ocr_unclassified_id;
CREATE INDEX CONCURRENTLY idx_ocr_unclassified_id ON app.mr_scan(id) INCLUDE(archive_id)
WHERE btype=0 AND uploadflag<>0 AND archive_id IS NOT NULL;
DROP INDEX CONCURRENTLY IF EXISTS app.idx_ocr_unclassified_archive;
CREATE INDEX CONCURRENTLY idx_ocr_unclassified_archive ON app.mr_scan(archive_id,id)
WHERE btype=0 AND uploadflag<>0 AND archive_id IS NOT NULL;
RESET lock_timeout;
