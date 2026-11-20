-- ============================================================================
-- MRR 锁等待与长事务（只读）
-- 运行：psql "$MRR_DB_DSN" -v ON_ERROR_STOP=1 -f monitoring/postgresql/diagnostics/04-locks-and-transactions.sql
--
-- 用途：响应“页面卡住 / 保存超时 / 报表一直转圈”类告警，先确认是不是锁或长事务。
-- 处置纪律：不直接 pg_terminate_backend 生产会话，先定位持有者业务动作并通知；
-- 终止会话会回滚业务事务，可能造成导入/导出任务中断。
-- ============================================================================

\pset pager off
\echo '### 1 锁等待链（blocked -> blocking），含等待时长'

SELECT
    blocked.pid                                                     AS blocked_pid,
    blocked.usename                                                 AS blocked_user,
    blocked.application_name,
    round(extract(epoch FROM now() - blocked.query_start), 1)       AS blocked_seconds,
    blocked.wait_event_type,
    blocked.wait_event,
    left(regexp_replace(blocked.query, '\s+', ' ', 'g'), 90)        AS blocked_query,
    blocking.pid                                                    AS blocking_pid,
    blocking.usename                                                AS blocking_user,
    blocking.state                                                  AS blocking_state,
    round(extract(epoch FROM now() - blocking.xact_start), 1)        AS blocking_xact_seconds,
    left(regexp_replace(blocking.query, '\s+', ' ', 'g'), 90)        AS blocking_query
FROM pg_stat_activity blocked
JOIN LATERAL unnest(pg_blocking_pids(blocked.pid)) AS b(pid) ON true
JOIN pg_stat_activity blocking ON blocking.pid = b.pid
WHERE blocked.wait_event_type = 'Lock'
ORDER BY blocked_seconds DESC;

\echo ''
\echo '### 2 运行超过 60 秒的事务（长时间持锁会阻塞 autovacuum 回收）'

SELECT
    pid,
    usename,
    application_name,
    state,
    round(extract(epoch FROM now() - xact_start), 1)                 AS xact_seconds,
    round(extract(epoch FROM now() - query_start), 1)                AS query_seconds,
    wait_event_type,
    wait_event,
    left(regexp_replace(query, '\s+', ' ', 'g'), 100)                AS query_sample
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
  AND now() - xact_start > interval '60 seconds'
  AND pid <> pg_backend_pid()
ORDER BY xact_start
LIMIT 20;

\echo ''
\echo '### 3 idle in transaction 会话（最隐蔽的锁持有者，常见于未提交的调试或异常分支）'

SELECT
    pid,
    usename,
    application_name,
    client_addr,
    round(extract(epoch FROM now() - state_change), 1)               AS idle_seconds,
    round(extract(epoch FROM now() - xact_start), 1)                 AS xact_seconds,
    left(regexp_replace(query, '\s+', ' ', 'g'), 100)                AS last_query
FROM pg_stat_activity
WHERE state IN ('idle in transaction', 'idle in transaction (aborted)')
  AND now() - state_change > interval '60 seconds'
ORDER BY state_change
LIMIT 20;

\echo ''
\echo '### 4 活跃会话等待事件分布（区分 CPU、IO、锁、客户端、提交）'

SELECT
    coalesce(wait_event_type, 'CPU/Running')                        AS wait_type,
    coalesce(wait_event, '-')                                       AS wait_event,
    count(*)                                                        AS sessions
FROM pg_stat_activity
WHERE state = 'active'
  AND pid <> pg_backend_pid()
GROUP BY 1, 2
ORDER BY sessions DESC
LIMIT 20;

\echo ''
\echo '### 5 未决两阶段事务（异常退出后残留的 prepared transaction 会一直持锁）'

SELECT gid, prepared, owner, database, round(extract(epoch FROM now() - prepared), 1) AS seconds
FROM pg_prepared_xacts
ORDER BY prepared;
