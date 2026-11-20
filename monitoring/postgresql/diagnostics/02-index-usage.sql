-- ============================================================================
-- MRR 索引使用与冗余（只读）
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/02-index-usage.sql
--
-- 说明：统计自上次 pg_stat_reset 起累计，重建/重启不会清零，但 reset 会清零。
-- 判定“未使用”需同时满足：运行时间 >= 30 天、idx_scan = 0、非唯一/非约束索引。
-- 删除索引属 P1 变更，必须走调优流程并保留重建脚本。
-- ============================================================================

\pset pager off
\echo '### 1 从未被扫描的索引（候选清理对象，按占用空间排序）'

SELECT
    s.schemaname,
    s.relname                                                 AS table_name,
    s.indexrelname                                            AS index_name,
    pg_size_pretty(pg_relation_size(s.indexrelid))            AS index_size,
    pg_size_pretty(pg_relation_size(s.relid))                 AS table_size,
    s.idx_scan,
    (SELECT age(datfrozenxid) FROM pg_database WHERE datname = current_database()) AS db_xid_age
FROM pg_stat_user_indexes s
JOIN pg_index i ON i.indexrelid = s.indexrelid
WHERE s.idx_scan = 0
  AND NOT i.indisunique
  AND NOT i.indisprimary
  AND NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = s.indexrelid)
  AND s.schemaname NOT IN ('pg_catalog', 'information_schema')
ORDER BY pg_relation_size(s.indexrelid) DESC
LIMIT 30;

\echo ''
\echo '### 2 顺序扫描代价最高的表（seq_tup_read 大 = 大表全表读，索引缺口线索）'

SELECT
    schemaname,
    relname                                                   AS table_name,
    seq_scan,
    seq_tup_read,
    round(seq_tup_read::numeric / NULLIF(seq_scan, 0), 0)      AS avg_rows_per_seq_scan,
    idx_scan,
    n_live_tup,
    pg_size_pretty(pg_total_relation_size(relid))             AS total_size
FROM pg_stat_user_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
  AND seq_tup_read > 100000
ORDER BY seq_tup_read DESC
LIMIT 20;

\echo ''
\echo '### 3 索引体积超过表本体（索引冗余 / 重复前缀线索）'

SELECT
    n.nspname                                                 AS schema_name,
    c.relname                                                 AS table_name,
    pg_size_pretty(pg_relation_size(c.oid))                   AS heap_size,
    pg_size_pretty(pg_indexes_size(c.oid))                    AS index_size,
    round(pg_indexes_size(c.oid)::numeric
          / NULLIF(pg_relation_size(c.oid), 0), 2)            AS index_to_heap_ratio,
    (SELECT count(*) FROM pg_index i WHERE i.indrelid = c.oid) AS index_count
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE c.relkind = 'r'
  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
  AND pg_relation_size(c.oid) > 0
  AND pg_indexes_size(c.oid) > pg_relation_size(c.oid)
ORDER BY pg_indexes_size(c.oid) DESC
LIMIT 15;

\echo ''
\echo '### 4 索引个数异常多的表（> 6 个，写入放大与维护成本来源）'

SELECT
    s.schemaname,
    s.relname                                                 AS table_name,
    count(*)                                                  AS index_count,
    pg_size_pretty(sum(pg_relation_size(s.indexrelid)))       AS total_index_size,
    pg_size_pretty(pg_relation_size(s.relid))                 AS heap_size
FROM pg_stat_user_indexes s
GROUP BY s.schemaname, s.relid, s.relname
HAVING count(*) > 6
ORDER BY count(*) DESC
LIMIT 15;
