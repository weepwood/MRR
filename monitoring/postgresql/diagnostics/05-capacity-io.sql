-- ============================================================================
-- MRR 容量与 IO 画像（只读）
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/05-capacity-io.sql
--
-- 用于回答：缓存够不够、是不是在刷盘、临时文件从哪来、WAL 是否异常增长。
-- pg_stat_io 需要 PostgreSQL 16+；read_time / write_time 依赖 track_io_timing = on。
-- ============================================================================

\pset pager off
\echo '### 1 数据库大小与对象分布'

SELECT
    current_database()                                              AS database_name,
    pg_size_pretty(pg_database_size(current_database()))            AS database_size,
    pg_size_pretty(sum(pg_total_relation_size(relid)))              AS tables_total,
    pg_size_pretty(sum(pg_indexes_size(relid)))                     AS indexes_total,
    count(*)                                                        AS user_tables
FROM pg_stat_user_tables;

\echo ''
\echo '### 2 最大的 15 张表（含索引与 TOAST）'

SELECT
    schemaname,
    relname                                                         AS table_name,
    pg_size_pretty(pg_relation_size(relid))                         AS heap_size,
    pg_size_pretty(pg_indexes_size(relid))                          AS index_size,
    pg_size_pretty(pg_total_relation_size(relid))                   AS total_size,
    n_live_tup
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 15;

\echo ''
\echo '### 3 库级缓存命中率（OLTP 目标 >= 99%；低于 95% 说明 shared_buffers 或查询模式有问题）'

SELECT
    datname,
    blks_hit,
    blks_read,
    round(100 * blks_hit::numeric / NULLIF(blks_hit + blks_read, 0), 3) AS cache_hit_pct,
    round(100 * blks_read::numeric / NULLIF(blks_hit + blks_read, 0), 3) AS disk_read_pct
FROM pg_stat_database
WHERE datname = current_database();

\echo ''
\echo '### 4 临时文件写入（排序/哈希溢出，定位 work_mem 与索引问题）'

SELECT
    datname,
    pg_size_pretty(temp_bytes)                                      AS temp_bytes_total,
    temp_files,
    round(temp_bytes::numeric / NULLIF(temp_files, 0) / 1024, 1)    AS avg_temp_kb_per_file,
    stats_reset
FROM pg_stat_database
WHERE datname = current_database();

\echo ''
\echo '### 5 IO 分布（按后端类型，read_time 为空表示 track_io_timing 未开启）'

SELECT
    backend_type,
    object,
    context,
    reads,
    round(read_time::numeric, 1)                                    AS read_ms,
    writes,
    round(write_time::numeric, 1)                                   AS write_ms,
    hits,
    evictions,
    extends
FROM pg_stat_io
WHERE reads > 0 OR writes > 0 OR evictions > 0
ORDER BY coalesce(read_time, 0) + coalesce(write_time, 0) DESC, reads + writes DESC
LIMIT 15;

\echo ''
\echo '### 6 WAL 目录占用与归档状态（单机部署没有备库时，归档失败不应阻塞主库写入）'

SELECT pg_size_pretty(coalesce(sum(size), 0)) AS wal_total FROM pg_ls_waldir();

SELECT
    archived_count,
    failed_count,
    last_archived_time,
    last_failed_time,
    stats_reset
FROM pg_stat_archiver;

\echo ''
\echo '### 7 关键内存与并发参数（对照服务器物理内存评估，不在此处直接修改）'

SELECT name, setting, unit
FROM pg_settings
WHERE name IN (
    'shared_buffers', 'work_mem', 'maintenance_work_mem', 'effective_cache_size',
    'max_connections', 'max_worker_processes', 'max_parallel_workers',
    'max_parallel_workers_per_gather', 'temp_buffers', 'wal_buffers',
    'checkpoint_timeout', 'max_wal_size', 'min_wal_size', 'random_page_cost'
)
ORDER BY name;
