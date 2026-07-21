-- Run in imgapi after pg_stat_statements has collected a representative load.
-- The report intentionally ignores one-off queries and its own statements.

-- Highest total database time: best targets for overall capacity gains.
SELECT calls,
       round(total_exec_time::numeric, 2) AS total_exec_ms,
       round(mean_exec_time::numeric, 2) AS mean_exec_ms,
       rows,
       shared_blks_hit,
       shared_blks_read,
       temp_blks_written,
       round(shared_blk_read_time::numeric, 2) AS read_time_ms,
       left(query, 1000) AS query
FROM public.pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND calls >= 3
  AND query NOT ILIKE '%pg_stat_statements%'
ORDER BY total_exec_time DESC
LIMIT 20;

-- Highest mean latency: best targets for slow endpoint investigation.
SELECT calls,
       round(mean_exec_time::numeric, 2) AS mean_exec_ms,
       round(max_exec_time::numeric, 2) AS max_exec_ms,
       rows,
       shared_blks_read,
       temp_blks_written,
       left(query, 1000) AS query
FROM public.pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND calls >= 3
  AND query NOT ILIKE '%pg_stat_statements%'
ORDER BY mean_exec_time DESC
LIMIT 20;

-- Reset only at the start of a deliberate measurement window.
-- SELECT public.pg_stat_statements_reset();
