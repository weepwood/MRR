-- ============================================================================
-- MRR 连接画像（只读）
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/06-connections.sql
--
-- 连接耗尽的根因通常不是 max_connections 太小，而是：连接池配置与实例数不匹配、
-- 长事务占用、连接泄漏、或某个批量任务并发过高。先看画像再谈加参数。
-- 期望：后端 Hikari maximum-pool-size 默认 20，单实例占用应稳定在 5-20 之间。
-- ============================================================================

\pset pager off
\echo '### 1 连接总览（已用 / 上限 / 预留 / 剩余可用）'

SELECT
    count(*)                                                                        AS used,
    current_setting('max_connections')::int                                          AS max_connections,
    current_setting('superuser_reserved_connections')::int                           AS reserved_superuser,
    current_setting('max_connections')::int
      - current_setting('superuser_reserved_connections')::int
      - count(*)                                                                     AS available_for_app,
    round(100 * count(*)::numeric / current_setting('max_connections')::int, 1)       AS used_pct
FROM pg_stat_activity;

\echo ''
\echo '### 2 按状态分布（idle 多说明池过大或连接泄漏；active 多说明并发真实）'

SELECT
    coalesce(state, 'unknown')                                      AS state,
    count(*)                                                        AS sessions,
    round(max(extract(epoch FROM now() - state_change)), 1)         AS max_seconds_in_state
FROM pg_stat_activity
GROUP BY 1
ORDER BY sessions DESC;

\echo ''
\echo '### 3 按应用 / 用户分布（识别是后端、psql 手工会话还是第三方工具）'

SELECT
    coalesce(usename, '-')                                          AS db_user,
    coalesce(application_name, '-')                                 AS application_name,
    coalesce(client_addr::text, 'local')                            AS client_addr,
    count(*)                                                        AS sessions,
    count(*) FILTER (WHERE state = 'active')                        AS active,
    count(*) FILTER (WHERE state = 'idle')                          AS idle
FROM pg_stat_activity
GROUP BY 1, 2, 3
ORDER BY sessions DESC
LIMIT 20;

\echo ''
\echo '### 4 最老的 10 个会话（判断是否存在长期不释放的连接）'

SELECT
    pid,
    usename,
    application_name,
    state,
    round(extract(epoch FROM now() - backend_start), 1)             AS connection_seconds,
    round(extract(epoch FROM now() - coalesce(xact_start, backend_start)), 1) AS xact_seconds,
    left(regexp_replace(coalesce(query, ''), '\s+', ' ', 'g'), 80)  AS last_query
FROM pg_stat_activity
WHERE pid <> pg_backend_pid()
ORDER BY backend_start
LIMIT 10;

\echo ''
\echo '### 5 连接上限相关参数'

SELECT name, setting, unit
FROM pg_settings
WHERE name IN ('max_connections', 'superuser_reserved_connections',
               'reserved_connections', 'idle_session_timeout',
               'idle_in_transaction_session_timeout', 'statement_timeout', 'lock_timeout')
ORDER BY name;
