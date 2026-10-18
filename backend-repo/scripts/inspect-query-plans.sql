-- 仅在已脱敏等量副本执行。
-- psql -X -v ON_ERROR_STOP=1 -v archive_id=1 -v code=123 -f inspect-query-plans.sql
-- ANALYZE 实际执行 SELECT；输出可能包含筛选值，只留在院内，脱敏后共享。
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='1s';
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT id,pages FROM app.mr_scan WHERE archive_id=:'archive_id'::bigint AND uploadflag<>0 ORDER BY pages,id LIMIT 1001;
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT id FROM app.mr_scan WHERE app.numeric_code_key(bah)=app.numeric_code_key(:'code') AND id>0 ORDER BY id LIMIT 51;
EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON)
SELECT id FROM app.mr_archive WHERE app.numeric_code_key(sjh)=app.numeric_code_key(:'code') LIMIT 2;
ROLLBACK;
