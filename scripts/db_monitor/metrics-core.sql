-- ============================================================================
-- MRR 轻量监控：基础指标（不依赖 pg_stat_statements）
-- 输出固定三列 metric|value|label，供 scripts/db_monitor/collect.ps1 解析。
-- 只读，可用 mrr_monitor 账号执行；全部为单行聚合，不含任何患者标识。
-- 膨胀估算只用一次 pg_stats 扫描（CTE 被两处引用会物化），避免重复开销。
-- ============================================================================

WITH bloat AS (
    WITH stat AS (
        SELECT c.relpages,
               c.reltuples::bigint AS reltuples,
               (SELECT sum((1 - st.null_frac) * st.avg_width)
                  FROM pg_stats st
                 WHERE st.schemaname = n.nspname AND st.tablename = c.relname) AS data_width
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE c.relkind = 'r'
           AND n.nspname NOT IN ('pg_catalog', 'information_schema')
    ), calc AS (
        SELECT relpages,
               ceil(reltuples * (coalesce(data_width, 0) + 24)
                    / current_setting('block_size')::int) AS est_pages
          FROM stat
         WHERE relpages > 128 AND reltuples > 1000
    )
    SELECT greatest(relpages - est_pages, 0)::numeric / NULLIF(relpages, 0) AS bloat_ratio,
           greatest(relpages - est_pages, 0)::numeric * 8192                AS bloat_bytes
      FROM calc
)
SELECT 'connections_total' AS metric, count(*)::text AS value, '个' AS label
  FROM pg_stat_activity WHERE pid <> pg_backend_pid()
UNION ALL
SELECT 'connections_active', count(*)::text, '个'
  FROM pg_stat_activity WHERE state = 'active' AND pid <> pg_backend_pid()
UNION ALL
SELECT 'connections_idle_in_tx', count(*)::text, '个'
  FROM pg_stat_activity WHERE state LIKE 'idle in transaction%' AND pid <> pg_backend_pid()
UNION ALL
SELECT 'connections_waiting', count(*)::text, '个'
  FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND pid <> pg_backend_pid()
UNION ALL
SELECT 'connections_pct',
       round(100 * count(*)::numeric / NULLIF(current_setting('max_connections')::int, 0), 1)::text, '%'
  FROM pg_stat_activity
UNION ALL
SELECT 'max_connections', current_setting('max_connections'), '个'
UNION ALL
SELECT 'database_size_bytes', pg_database_size(current_database())::text, 'B'
UNION ALL
SELECT 'cache_hit_pct',
       coalesce(round(100 * sum(blks_hit)::numeric / NULLIF(sum(blks_hit + blks_read), 0), 3), 100)::text, '%'
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'temp_bytes_total', coalesce(sum(temp_bytes), 0)::text, 'B'
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'deadlocks_total', coalesce(sum(deadlocks), 0)::text, '次'
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'xact_commit_total', coalesce(sum(xact_commit), 0)::text, '次'
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'xact_rollback_total', coalesce(sum(xact_rollback), 0)::text, '次'
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'longest_xact_seconds',
       coalesce(round(max(extract(epoch FROM now() - xact_start)), 1), 0)::text, 's'
  FROM pg_stat_activity WHERE xact_start IS NOT NULL AND pid <> pg_backend_pid()
UNION ALL
SELECT 'xacts_over_60s', count(*)::text, '个'
  FROM pg_stat_activity
 WHERE xact_start IS NOT NULL AND now() - xact_start > interval '60 seconds'
   AND pid <> pg_backend_pid()
UNION ALL
SELECT 'dead_tuple_ratio',
       coalesce(round(max(CASE WHEN n_live_tup + n_dead_tup > 1000
                               THEN n_dead_tup::numeric / (n_live_tup + n_dead_tup) END), 3), 0)::text, '比例'
  FROM pg_stat_user_tables
 WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
UNION ALL
SELECT 'tables_needing_vacuum',
       (count(*) FILTER (WHERE n_dead_tup > 10000
                           AND n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0) > 0.2))::text, '张'
  FROM pg_stat_user_tables
 WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
UNION ALL
SELECT 'biggest_table_bytes',
       coalesce(max(pg_total_relation_size(relid)), 0)::text, 'B'
  FROM pg_stat_user_tables
 WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
UNION ALL
SELECT 'unused_index_count', count(*)::text, '个'
  FROM pg_stat_user_indexes s
  JOIN pg_index i ON i.indexrelid = s.indexrelid
 WHERE s.idx_scan = 0
   AND NOT i.indisunique
   AND NOT i.indisprimary
   AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = s.indexrelid)
   AND s.schemaname NOT IN ('pg_catalog', 'information_schema')
UNION ALL
SELECT 'bloat_max_ratio', coalesce(round(max(bloat_ratio), 3), 0)::text, '比例' FROM bloat
UNION ALL
SELECT 'bloat_max_bytes', coalesce(round(max(bloat_bytes)), 0)::text, 'B' FROM bloat
UNION ALL
SELECT 'server_uptime_seconds',
       coalesce(round(extract(epoch FROM now() - pg_postmaster_start_time())), 0)::text, 's';
