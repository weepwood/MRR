-- Run as a PostgreSQL administrator, connected to the MRR business database:
--   psql -U postgres -d <实际库名> -f monitoring/postgresql/enable-monitoring.sql
-- 库名不写死：授权对象取 current_database()，现场库名与文档示例不同时不会报错也不会授错库。
-- PostgreSQL must be restarted after adding pg_stat_statements to
-- shared_preload_libraries in postgresql.conf.

-- 先挡掉连错库：在 postgres / template 库上执行会静默建错扩展、授错库，比直接报错更难排查。
DO
$$
DECLARE
    target_db text := current_database();
BEGIN
    IF target_db IN ('postgres', 'template0', 'template1') THEN
        RAISE EXCEPTION '需要先连接到 MRR 业务库再执行（当前连接库：%）', target_db
            USING HINT = 'psql -U postgres -d <实际库名> -f monitoring/postgresql/enable-monitoring.sql';
    END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public;

DO
$$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO mrr_monitor', current_database());
END
$$;

GRANT pg_monitor TO mrr_monitor;
