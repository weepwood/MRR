-- ============================================================================
-- MRR 慢查询画像（只读）
-- 依赖：pg_stat_statements 已加入 shared_preload_libraries 并重启，扩展已安装。
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/01-slow-queries.sql
--
-- 隐私提示：query 列对参数化语句只保留 $1/$2 占位符，但非参数化语句（拼接 SQL、
-- 工具类语句）可能残留字面量。本文件输出仅限内网排障使用，不得整段贴入
-- Issue / PR / 工单 / 截图；对外引用请只保留 query_sample 的结构化摘要。
-- 建议在服务端设置 track_utility = off，避免 COPY/VACUUM 等工具语句进入统计。
-- ============================================================================

\pset pager off
\echo '### 1 累计耗时 TOP 20（总成本视角，回答“时间花在哪类语句上”）'

SELECT
    round(total_exec_time::numeric, 1)                                                    AS total_ms,
    round(mean_exec_time::numeric, 1)                                                     AS mean_ms,
    round((100 * total_exec_time / NULLIF(sum(total_exec_time) OVER (), 0))::numeric, 1)  AS pct_total,
    calls,
    round(rows::numeric / NULLIF(calls, 0), 1)                                            AS rows_per_call,
    round(shared_blks_hit::numeric * 100
          / NULLIF(shared_blks_hit + shared_blks_read, 0), 1)                             AS cache_hit_pct,
    temp_blks_written * 8                                                                 AS temp_kb,
    pg_size_pretty(wal_bytes)                                                             AS wal,
    left(regexp_replace(query, '\s+', ' ', 'g'), 160)                                     AS query_sample
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND calls > 0
ORDER BY total_exec_time DESC
LIMIT 20;

\echo ''
\echo '### 2 单次平均耗时 TOP 20（单条语句质量视角，calls >= 5 才有统计意义）'

SELECT
    round(mean_exec_time::numeric, 1)                          AS mean_ms,
    round(max_exec_time::numeric, 1)                           AS max_ms,
    calls,
    round(stddev_exec_time::numeric, 1)                        AS stddev_ms,
    round(rows::numeric / NULLIF(calls, 0), 1)                 AS rows_per_call,
    temp_blks_written * 8                                      AS temp_kb,
    left(regexp_replace(query, '\s+', ' ', 'g'), 160)          AS query_sample
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND calls >= 5
ORDER BY mean_exec_time DESC
LIMIT 20;

\echo ''
\echo '### 3 高调用 / 低产出行数（N+1 与循环单查嫌疑：每次只取回 1 行却调用上万次）'

SELECT
    calls,
    round(rows::numeric / NULLIF(calls, 0), 2)                 AS rows_per_call,
    round(total_exec_time::numeric, 1)                         AS total_ms,
    round(mean_exec_time::numeric, 1)                          AS mean_ms,
    left(regexp_replace(query, '\s+', ' ', 'g'), 160)          AS query_sample
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND calls >= 1000
  AND rows::numeric / NULLIF(calls, 0) < 1.2
ORDER BY total_exec_time DESC
LIMIT 15;

\echo ''
\echo '### 4 重复规划（plans/calls 接近 1：预编译未复用，或动态 SQL 每次重解析）'

SELECT
    calls,
    plans,
    round(plans::numeric / NULLIF(calls, 0), 3)                AS plans_per_call,
    round(total_plan_time::numeric, 1)                         AS total_plan_ms,
    round(mean_plan_time::numeric, 3)                          AS mean_plan_ms,
    left(regexp_replace(query, '\s+', ' ', 'g'), 140)          AS query_sample
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND calls >= 100
  AND plans > calls * 0.8
ORDER BY total_plan_time DESC
LIMIT 15;

\echo ''
\echo '### 5 排序 / 哈希溢出到临时文件 TOP 15（work_mem 不足或缺少可用索引）'

SELECT
    temp_blks_written * 8                                      AS temp_kb,
    calls,
    round(mean_exec_time::numeric, 1)                          AS mean_ms,
    round(total_exec_time::numeric, 1)                         AS total_ms,
    left(regexp_replace(query, '\s+', ' ', 'g'), 160)          AS query_sample
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT ILIKE '%pg_stat_statements%'
  AND temp_blks_written > 0
ORDER BY temp_blks_written DESC
LIMIT 15;
