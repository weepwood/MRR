-- ============================================================================
-- MRR 表膨胀与 autovacuum 健康度（只读）
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/03-bloat-and-vacuum.sql
--
-- 膨胀为估算值（基于 pg_stats 平均行宽），用于排序定位，不作为精确回收字节数。
-- 真正回收空间需 VACUUM FULL / pg_repack，属 P1 变更，必须停机窗口或在线工具 + 回滚方案。
-- ============================================================================

\pset pager off
\echo '### 1 死元组占比 TOP 20（autovacuum 跟不上写入，直接推高扫描成本）'

SELECT
    schemaname,
    relname                                                     AS table_name,
    n_live_tup,
    n_dead_tup,
    round(100 * n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0), 1) AS dead_pct,
    last_vacuum,
    last_autovacuum,
    last_autoanalyze,
    n_mod_since_analyze,
    pg_size_pretty(pg_total_relation_size(relid))               AS total_size
FROM pg_stat_user_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  AND n_dead_tup > 1000
ORDER BY n_dead_tup DESC
LIMIT 20;

\echo ''
\echo '### 2 膨胀估算 TOP 15（实际页数远大于按行宽推算的页数）'

WITH stat AS (
    SELECT
        c.oid,
        n.nspname,
        c.relname,
        c.relpages,
        c.reltuples::bigint AS reltuples,
        (SELECT sum((1 - s.null_frac) * s.avg_width)
           FROM pg_stats s
          WHERE s.schemaname = n.nspname AND s.tablename = c.relname) AS data_width
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind = 'r'
      AND n.nspname NOT IN ('pg_catalog', 'information_schema')
), calc AS (
    SELECT
        nspname,
        relname,
        relpages,
        reltuples,
        ceil(reltuples * (coalesce(data_width, 0) + 24)
             / current_setting('block_size')::int) AS est_pages
    FROM stat
    WHERE relpages > 128 AND reltuples > 1000
)
SELECT
    nspname                                                         AS schema_name,
    relname                                                         AS table_name,
    pg_size_pretty(relpages::bigint * 8192)                         AS actual_size,
    pg_size_pretty(greatest(relpages - est_pages, 0)::bigint * 8192) AS est_bloat_size,
    round(100 * greatest(relpages - est_pages, 0)::numeric / NULLIF(relpages, 0), 1) AS est_bloat_pct,
    relpages,
    est_pages
FROM calc
WHERE greatest(relpages - est_pages, 0)::numeric / NULLIF(relpages, 0) > 0.20
ORDER BY greatest(relpages - est_pages, 0)::bigint DESC
LIMIT 15;

\echo ''
\echo '### 3 大表最近一次 autovacuum 距今（NULL 表示从未被自动清理过）'

SELECT
    schemaname,
    relname                                                     AS table_name,
    pg_size_pretty(pg_total_relation_size(relid))               AS total_size,
    last_autovacuum,
    CASE WHEN last_autovacuum IS NULL THEN NULL
         ELSE round(extract(epoch FROM now() - last_autovacuum) / 86400.0, 1)
    END                                                         AS days_since_autovacuum,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  AND pg_total_relation_size(relid) > 100 * 1024 * 1024
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 15;

\echo ''
\echo '### 4 autovacuum 关键参数现状（对照表规模判断是否需要单表覆盖参数）'

SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
    'autovacuum', 'autovacuum_naptime', 'autovacuum_max_workers',
    'autovacuum_vacuum_scale_factor', 'autovacuum_vacuum_threshold',
    'autovacuum_analyze_scale_factor', 'autovacuum_vacuum_cost_delay',
    'autovacuum_vacuum_cost_limit', 'maintenance_work_mem', 'vacuum_cost_limit'
)
ORDER BY name;

\echo ''
\echo '### 5 正在运行的 vacuum / analyze（确认维护任务没有卡住）'

SELECT
    p.pid,
    a.query,
    p.phase,
    p.heap_blks_total,
    p.heap_blks_scanned,
    p.heap_blks_vacuumed,
    p.index_vacuum_count,
    round(100 * p.heap_blks_scanned::numeric / NULLIF(p.heap_blks_total, 0), 1) AS scanned_pct
FROM pg_stat_progress_vacuum p
JOIN pg_stat_activity a ON a.pid = p.pid
ORDER BY p.pid;
