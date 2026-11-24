-- psql -X -v ON_ERROR_STOP=1 -f build-code-key-indexes.sql
-- 独立执行，不可加 -1 / BEGIN，不进入 Flyway 长事务。先在等量副本评估空间与耗时。
\set ON_ERROR_STOP on
SET lock_timeout='5s';
SET statement_timeout='2h';
DO $$ BEGIN
  -- IF NOT EXISTS checks only the object name, not its definition.
  IF EXISTS (
    SELECT 1
    FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    LEFT JOIN pg_index i ON i.indexrelid=c.oid
    LEFT JOIN pg_am am ON am.oid=c.relam
    WHERE n.nspname='app'
      AND c.relname IN ('idx_mr_scan_bah_numeric_cursor','idx_mr_scan_sjh_numeric_cursor')
      AND NOT COALESCE((
        i.indisvalid AND i.indisready AND i.indislive
        AND i.indrelid='app.mr_scan'::regclass
        AND am.amname='btree'
        AND i.indpred IS NULL
        AND i.indnkeyatts=2 AND i.indnatts=2
        AND i.indoption[0]=0 AND i.indoption[1]=0
        AND pg_get_indexdef(i.indexrelid,2,false)='id'
        AND CASE c.relname
          WHEN 'idx_mr_scan_bah_numeric_cursor' THEN
            pg_get_indexdef(i.indexrelid,1,false) IN ('app.numeric_code_key(bah)','numeric_code_key(bah)')
          WHEN 'idx_mr_scan_sjh_numeric_cursor' THEN
            pg_get_indexdef(i.indexrelid,1,false) IN ('app.numeric_code_key(sjh)','numeric_code_key(sjh)')
          ELSE false END
        AND EXISTS (
          SELECT 1 FROM pg_depend dep
          WHERE dep.classid='pg_class'::regclass
            AND dep.objid=i.indexrelid
            AND dep.refclassid='pg_proc'::regclass
            AND dep.refobjid='app.numeric_code_key(text)'::regprocedure
        )
      ),false)
  ) THEN
    RAISE EXCEPTION 'Invalid governance index exists: inspect the named object and resolve the conflict in an approved maintenance window before retry';
  END IF;
END $$;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_bah_numeric_cursor ON app.mr_scan(app.numeric_code_key(bah),id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_mr_scan_sjh_numeric_cursor ON app.mr_scan(app.numeric_code_key(sjh),id);
SELECT c.relname,i.indisvalid,i.indisready,pg_size_pretty(pg_relation_size(c.oid)) AS index_size
FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='app' AND c.relname IN ('idx_mr_scan_bah_numeric_cursor','idx_mr_scan_sjh_numeric_cursor');
-- 每条独立回退，仅删除本脚本索引，不修改数据：
-- DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_bah_numeric_cursor;
-- DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_sjh_numeric_cursor;
