-- psql -X -v ON_ERROR_STOP=1 -f build-code-key-indexes.sql
-- 独立执行，不可加 -1 / BEGIN，不进入 Flyway 长事务。先在等量副本评估空间与耗时。
SET lock_timeout='5s';
SET statement_timeout='2h';
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
    JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='app'
    AND c.relname IN ('idx_mr_scan_bah_numeric_cursor','idx_mr_scan_sjh_numeric_cursor') AND NOT i.indisvalid)
  THEN RAISE EXCEPTION 'Invalid governance index exists: inspect and DROP INDEX CONCURRENTLY that named index before retry'; END IF;
END $$;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_bah_numeric_cursor ON app.mr_scan(app.numeric_code_key(bah),id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_sjh_numeric_cursor ON app.mr_scan(app.numeric_code_key(sjh),id);
SELECT c.relname,i.indisvalid,i.indisready,pg_size_pretty(pg_relation_size(c.oid)) AS index_size
FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='app' AND c.relname IN ('idx_mr_scan_bah_numeric_cursor','idx_mr_scan_sjh_numeric_cursor');
-- 每条独立回退，仅删除本脚本索引，不修改数据：
-- DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_bah_numeric_cursor;
-- DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_sjh_numeric_cursor;
