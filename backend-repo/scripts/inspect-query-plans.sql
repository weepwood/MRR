-- 仅在已脱敏等量副本执行。使用只读数据库账号，不连接生产。
-- psql -X -v ON_ERROR_STOP=1 -v archive_id=1 -v code=123 -f inspect-query-plans.sql
-- 可选：-v deep_offset=100000 -v after_id=123456 -v analyze_legacy=1
-- analyze_legacy=1 才执行兼容查询、深分页及精确 COUNT；默认仅 EXPLAIN。
-- after_id 仅用于独立游标探测，不能作为等价页证据。
-- 可选 compare_exact=1（默认 BAH，compare_sjh=1 改为 SJH）自动验证同页后执行对比。
-- 计划可能包含筛选值，仅在院内保存，脱敏后共享。超时即停止，不自动重试或放宽预算。
\if :{?deep_offset}
\else
\set deep_offset 0
\endif
\if :{?after_id}
\else
\set after_id 0
\endif
\if :{?analyze_legacy}
\else
\set analyze_legacy 0
\endif

\set ON_ERROR_STOP on
\if :{?compare_exact}
\else
\set compare_exact 0
\endif
\if :{?compare_sjh}
\else
\set compare_sjh 0
\endif

BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='1s';
\echo '=== table estimates and indexes (no patient rows) ==='
SELECT c.relname, c.reltuples::bigint AS estimated_rows,
       pg_total_relation_size(c.oid) AS total_bytes,
       s.last_analyze, s.last_autoanalyze
FROM pg_class c
JOIN pg_namespace n ON n.oid=c.relnamespace
LEFT JOIN pg_stat_user_tables s ON s.relid=c.oid
WHERE n.nspname='app' AND c.relname IN ('mr_scan','mr_archive')
ORDER BY c.relname;
SELECT t.relname AS table_name, i.relname AS index_name,
       x.indisvalid, x.indisready, pg_get_indexdef(i.oid) AS definition
FROM pg_index x JOIN pg_class t ON t.oid=x.indrelid
JOIN pg_class i ON i.oid=x.indexrelid
JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname='app' AND t.relname IN ('mr_scan','mr_archive')
ORDER BY t.relname,i.relname;
\echo '=== bounded archive probe, actual archive plan and independent cursor probes (not equivalent-page evidence) ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE archive_id=:'archive_id'::bigint AND uploadflag<>0 ORDER BY pages,id LIMIT 1001;
-- Actual archive Mapper has no LIMIT: plan only, do not execute an unbounded result.
EXPLAIN (SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE archive_id=:'archive_id'::bigint AND uploadflag<>0 ORDER BY pages,id;
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE app.numeric_code_key(bah)=app.numeric_code_key(:'code') AND id>:'after_id'::integer ORDER BY id LIMIT 51;
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE app.numeric_code_key(sjh)=app.numeric_code_key(:'code') AND id>:'after_id'::integer ORDER BY id LIMIT 51;
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT id FROM app.mr_archive WHERE app.numeric_code_key(sjh)=app.numeric_code_key(:'code') LIMIT 2;
ROLLBACK;

-- Planning and execution are deliberately separated. A timeout in one actual
-- legacy measurement must not be mistaken for a successful zero-row query.
BEGIN READ ONLY;
SET LOCAL statement_timeout='3s';
SET LOCAL lock_timeout='1s';
\echo '=== legacy contains search, OFFSET and COUNT: planned cost only ==='
EXPLAIN (SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE bah LIKE '%' || :'code' || '%' ORDER BY id LIMIT 50 OFFSET :'deep_offset'::integer;
EXPLAIN (SETTINGS,FORMAT JSON)
SELECT count(*) FROM app.mr_scan WHERE bah LIKE '%' || :'code' || '%';
EXPLAIN (SETTINGS,FORMAT JSON)
SELECT count(*) FROM app.mr_scan;
\echo '=== exact predicate OFFSET planned cost only (not equivalent-page evidence) ==='
EXPLAIN (SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE app.numeric_code_key(bah)=app.numeric_code_key(:'code') ORDER BY id LIMIT 51 OFFSET :'deep_offset'::integer;
ROLLBACK;

\if :analyze_legacy
BEGIN READ ONLY;
SET LOCAL statement_timeout='3s';
SET LOCAL lock_timeout='1s';
\echo '=== opt-in actual legacy page ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE bah LIKE '%' || :'code' || '%' ORDER BY id LIMIT 50 OFFSET :'deep_offset'::integer;
ROLLBACK;
BEGIN READ ONLY;
SET LOCAL statement_timeout='3s';
SET LOCAL lock_timeout='1s';
\echo '=== opt-in actual exact count, same legacy predicate ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT count(*) FROM app.mr_scan WHERE bah LIKE '%' || :'code' || '%';
ROLLBACK;
\endif

\if :compare_exact
\if :compare_sjh
\set comparison_column sjh
\else
\set comparison_column bah
\endif
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout='3s';
SET LOCAL lock_timeout='1s';
-- BEGIN EXACT PAGE EVIDENCE SQL
WITH boundary AS MATERIALIZED (
    SELECT id FROM app.mr_scan
    WHERE id>0 AND app.numeric_code_key(:"comparison_column")=app.numeric_code_key(:'code')
      AND :'deep_offset'::integer>0
    ORDER BY id LIMIT 1 OFFSET GREATEST(:'deep_offset'::integer-1,0)
), offset_page AS MATERIALIZED (
    SELECT id FROM app.mr_scan
    WHERE id>0 AND app.numeric_code_key(:"comparison_column")=app.numeric_code_key(:'code')
    ORDER BY id LIMIT 51 OFFSET :'deep_offset'::integer
), cursor_page AS MATERIALIZED (
    SELECT id FROM app.mr_scan
    WHERE id>COALESCE((SELECT id FROM boundary),0)
      AND app.numeric_code_key(:"comparison_column")=app.numeric_code_key(:'code')
    ORDER BY id LIMIT 51
)
SELECT (:'deep_offset'::integer=0 OR EXISTS (SELECT 1 FROM boundary))
       AND EXISTS (SELECT 1 FROM offset_page)
       AND ARRAY(SELECT id FROM offset_page ORDER BY id)=ARRAY(SELECT id FROM cursor_page ORDER BY id) AS comparable,
       COALESCE((SELECT id FROM boundary),0) AS matched_after_id
-- END EXACT PAGE EVIDENCE SQL
\gset comparison_
\if :comparison_comparable
\echo '=== SAME_PAGE_VERIFIED: exact predicate OFFSET (warm snapshot) ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan
WHERE id>0 AND app.numeric_code_key(:"comparison_column")=app.numeric_code_key(:'code')
ORDER BY id LIMIT 51 OFFSET :'deep_offset'::integer;
\echo '=== SAME_PAGE_VERIFIED: exact predicate cursor (warm snapshot) ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan
WHERE id>:'comparison_matched_after_id'::integer
  AND app.numeric_code_key(:"comparison_column")=app.numeric_code_key(:'code')
ORDER BY id LIMIT 51;
\else
\echo 'NOT_COMPARABLE: missing boundary or empty/different page; no performance comparison'
\endif
ROLLBACK;
\endif
