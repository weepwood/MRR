-- Run as a PostgreSQL administrator after creating the mrr_monitor login role.
-- PostgreSQL must be restarted after adding pg_stat_statements to
-- shared_preload_libraries in postgresql.conf.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public;

GRANT CONNECT ON DATABASE imgapi TO mrr_monitor;
GRANT pg_monitor TO mrr_monitor;
