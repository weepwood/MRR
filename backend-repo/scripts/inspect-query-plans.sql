-- 仅在已脱敏等量副本执行。使用只读数据库账号，不连接生产。
-- psql -X -v ON_ERROR_STOP=1 -v archive_id=1 -v code=123 -f inspect-query-plans.sql
-- 可选：-v deep_offset=100000 -v after_id=123456 -v analyze_legacy=1
-- analyze_legacy=1 才执行兼容查询、深分页及精确 COUNT；默认仅 EXPLAIN。
-- after_id 必须由相同精确条件和 offset 的上一条结果确定，不能把任意 id 当作等价页。
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
\echo '=== current archive and exact cursor plans ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT id,pages FROM app.mr_scan WHERE archive_id=:'archive_id'::bigint AND uploadflag<>0 ORDER BY pages,id LIMIT 1001;
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
\echo '=== same exact predicate: OFFSET for comparison ==='
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
BEGIN READ ONLY;
SET LOCAL statement_timeout='3s';
SET LOCAL lock_timeout='1s';
\echo '=== opt-in actual exact predicate OFFSET ==='
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT * FROM app.mr_scan WHERE app.numeric_code_key(bah)=app.numeric_code_key(:'code') ORDER BY id LIMIT 51 OFFSET :'deep_offset'::integer;
ROLLBACK;
\endif
