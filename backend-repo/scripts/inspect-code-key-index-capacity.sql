-- Read-only sizing evidence. No COUNT, DDL, OS filesystem reads or production connection defaults.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SET LOCAL lock_timeout='1s';
SELECT current_setting('server_version_num')::integer / 10000 AS postgres_major,
       current_setting('default_tablespace') AS configured_index_tablespace,
       current_setting('temp_tablespaces') AS configured_temp_tablespaces,
       current_setting('maintenance_work_mem') AS maintenance_work_mem,
       current_setting('max_parallel_maintenance_workers') AS maintenance_workers;
SELECT c.reltuples::bigint AS estimated_rows,
       pg_relation_size(c.oid) AS heap_bytes,
       pg_indexes_size(c.oid) AS all_index_bytes,
       pg_total_relation_size(c.oid) AS total_bytes,
       COALESCE(t.spcname,dt.spcname) AS heap_tablespace,
       CASE WHEN c.reltuples >= 0 THEN
           ceil(c.reltuples::numeric * 1892573184 / 30000000)::bigint
       END AS two_index_reference_bytes
FROM pg_class c
JOIN pg_database d ON d.datname=current_database()
JOIN pg_tablespace dt ON dt.oid=d.dattablespace
LEFT JOIN pg_tablespace t ON t.oid=c.reltablespace
WHERE c.oid='app.mr_scan'::regclass;
-- The reference is the measured 30m delta, not a free-space measurement or hard upper bound.
SELECT wanted.name, c.relkind,
       i.indisvalid, i.indisready, i.indislive,
       CASE WHEN c.relkind='i' THEN pg_relation_size(c.oid) END AS existing_bytes,
       CASE WHEN c.oid IS NOT NULL THEN COALESCE(t.spcname,dt.spcname) END AS effective_tablespace
FROM (VALUES ('idx_mr_scan_bah_numeric_cursor'),('idx_mr_scan_sjh_numeric_cursor')) wanted(name)
JOIN pg_database d ON d.datname=current_database()
JOIN pg_tablespace dt ON dt.oid=d.dattablespace
LEFT JOIN pg_namespace n ON n.nspname='app'
LEFT JOIN pg_class c ON c.relnamespace=n.oid AND c.relname=wanted.name
LEFT JOIN pg_index i ON i.indexrelid=c.oid
LEFT JOIN pg_tablespace t ON t.oid=c.reltablespace
ORDER BY wanted.name;
SELECT phase,blocks_done,blocks_total,tuples_done,tuples_total
FROM pg_stat_progress_create_index WHERE relid='app.mr_scan'::regclass;
ROLLBACK;
