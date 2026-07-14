package com.zjcxph.imgapi.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMonitorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DatabaseMonitorService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", jdbcTemplate.queryForMap("""
                SELECT current_database() AS name,
                       current_setting('server_version') AS version,
                       pg_database_size(current_database()) AS size_bytes,
                       EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - pg_postmaster_start_time()))::bigint AS uptime_seconds,
                       current_setting('max_connections')::int AS max_connections
                """));
        result.put("connections", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE state = 'active') AS active,
                       COUNT(*) FILTER (WHERE state = 'idle') AS idle,
                       COUNT(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
                       COUNT(*) FILTER (WHERE wait_event IS NOT NULL) AS waiting
                FROM pg_stat_activity
                WHERE datname = current_database()
                """));
        result.put("transactions", jdbcTemplate.queryForMap("""
                SELECT xact_commit AS commits,
                       xact_rollback AS rollbacks,
                       deadlocks,
                       temp_files,
                       temp_bytes,
                       CASE WHEN blks_hit + blks_read = 0 THEN 100
                            ELSE ROUND((blks_hit::numeric * 100) / (blks_hit + blks_read), 2)
                       END AS cache_hit_ratio
                FROM pg_stat_database
                WHERE datname = current_database()
                """));
        result.put("contention", jdbcTemplate.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE wait_event_type = 'Lock') AS lock_waiters,
                       COUNT(*) FILTER (
                           WHERE xact_start IS NOT NULL
                             AND CURRENT_TIMESTAMP - xact_start > INTERVAL '60 seconds'
                       ) AS long_transactions,
                       COALESCE(EXTRACT(EPOCH FROM MAX(CURRENT_TIMESTAMP - xact_start))::bigint, 0) AS longest_transaction_seconds
                FROM pg_stat_activity
                WHERE datname = current_database()
                  AND pid <> pg_backend_pid()
                """));
        result.put("hikari", getHikariOverview());
        result.put("tables", getLargestTables(10));
        return result;
    }

    public List<Map<String, Object>> getSlowQueries(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try {
            return jdbcTemplate.queryForList("""
                    SELECT queryid::text AS query_id,
                           calls,
                           ROUND(total_exec_time::numeric, 2) AS total_exec_time_ms,
                           ROUND(mean_exec_time::numeric, 2) AS mean_exec_time_ms,
                           rows,
                           shared_blks_hit,
                           shared_blks_read,
                           temp_blks_written,
                           wal_bytes,
                           LEFT(REGEXP_REPLACE(query, '\\s+', ' ', 'g'), 500) AS query_template
                    FROM pg_stat_statements
                    WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
                      AND query NOT ILIKE '%pg_stat_statements%'
                    ORDER BY total_exec_time DESC
                    LIMIT ?
                    """, safeLimit);
        } catch (Exception e) {
            logger.warn("Unable to query pg_stat_statements: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getLargestTables(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.queryForList("""
                SELECT schemaname,
                       relname AS table_name,
                       pg_total_relation_size(relid) AS total_bytes,
                       pg_relation_size(relid) AS table_bytes,
                       pg_indexes_size(relid) AS index_bytes,
                       n_live_tup AS live_rows,
                       n_dead_tup AS dead_rows,
                       seq_scan,
                       idx_scan,
                       last_autovacuum,
                       last_autoanalyze
                FROM pg_stat_user_tables
                ORDER BY pg_total_relation_size(relid) DESC
                LIMIT ?
                """, safeLimit);
    }

    private Map<String, Object> getHikariOverview() {
        Map<String, Object> hikari = new LinkedHashMap<>();
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            hikari.put("available", false);
            return hikari;
        }
        hikari.put("available", true);
        hikari.put("maximum_pool_size", hikariDataSource.getMaximumPoolSize());
        hikari.put("minimum_idle", hikariDataSource.getMinimumIdle());
        try {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                hikari.put("active", pool.getActiveConnections());
                hikari.put("idle", pool.getIdleConnections());
                hikari.put("total", pool.getTotalConnections());
                hikari.put("pending", pool.getThreadsAwaitingConnection());
            }
        } catch (Exception e) {
            logger.debug("Hikari pool MXBean is not available yet: {}", e.getMessage());
        }
        return hikari;
    }
}
