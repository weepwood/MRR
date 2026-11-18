-- ============================================================================
-- MRR 轻量监控：查询级指标（依赖 pg_stat_statements 预加载）
-- 输出固定三列 metric|value|label。扩展不可用时整份查询失败，
-- collect.ps1 会记录 statements_available=0 并在看板上显式标注，而不是静默留空。
-- ============================================================================

SELECT 'stmt_calls_total' AS metric, coalesce(sum(calls), 0)::text AS value, '次' AS label
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
UNION ALL
SELECT 'stmt_exec_ms_total',
       coalesce(round(sum(total_exec_time)::numeric, 1), 0)::text, 'ms'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
UNION ALL
SELECT 'stmt_rows_total', coalesce(sum(rows), 0)::text, '行'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
UNION ALL
SELECT 'stmt_worst_mean_ms',
       coalesce(round(max(mean_exec_time)::numeric, 1), 0)::text, 'ms'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
   AND calls > 0
UNION ALL
SELECT 'stmt_slow_200ms',
       (count(*) FILTER (WHERE calls > 0 AND mean_exec_time > 200))::text, '条'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
UNION ALL
SELECT 'stmt_slow_1s',
       (count(*) FILTER (WHERE calls > 0 AND mean_exec_time > 1000))::text, '条'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%'
UNION ALL
SELECT 'stmt_temp_mb',
       coalesce(round(sum(temp_blks_written)::numeric * 8 / 1024, 2), 0)::text, 'MB'
  FROM pg_stat_statements
 WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
   AND query NOT ILIKE '%pg_stat_statements%';
