-- Query observability. shared_preload_libraries must also include
-- pg_stat_statements at the PostgreSQL server level.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- Generic statistics keyword search. The expression must stay identical to
-- the StatisticsMapper predicate for PostgreSQL to use this index.
CREATE INDEX IF NOT EXISTS idx_mr_statistics_keyword_trgm
    ON app.mr_statistics
    USING gin ((
        coalesce(cid, '')
        || chr(1) || coalesce(openerno, '')
        || chr(1) || coalesce(date, '')
        || chr(1) || coalesce(type, '')
    ) public.gin_trgm_ops);

-- Generic log keyword search shared by the general and image-audit lists.
CREATE INDEX IF NOT EXISTS idx_access_log_keyword_common_trgm
    ON app.access_log
    USING gin ((
        coalesce(username, '')
        || chr(1) || coalesce(client_ip, '')
        || chr(1) || coalesce(request_uri, '')
        || chr(1) || coalesce(query_string, '')
        || chr(1) || coalesce(user_agent, '')
    ) public.gin_trgm_ops);

-- referer participates only in the general log keyword search.
CREATE INDEX IF NOT EXISTS idx_access_log_referer_trgm
    ON app.access_log USING gin (referer public.gin_trgm_ops);
