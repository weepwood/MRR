-- Run with psql variables, never hard-code passwords in this repository.
-- Example:
-- psql -U postgres -d postgres -v backup_password='...' -v restore_password='...' -f backup-roles.sql

\if :{?backup_password}
\else
\echo 'backup_password variable is required'
\quit
\endif

\if :{?restore_password}
\else
\echo 'restore_password variable is required'
\quit
\endif

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mrr_backup') THEN
        CREATE ROLE mrr_backup LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mrr_restore') THEN
        CREATE ROLE mrr_restore LOGIN CREATEDB;
    END IF;
END
$$;

ALTER ROLE mrr_backup PASSWORD :'backup_password';
ALTER ROLE mrr_restore PASSWORD :'restore_password';

GRANT pg_read_all_data TO mrr_backup;
GRANT CONNECT ON DATABASE imgapi TO mrr_backup;
GRANT USAGE ON SCHEMA app TO mrr_backup;
GRANT SELECT ON ALL TABLES IN SCHEMA app TO mrr_backup;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA app TO mrr_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT ON TABLES TO mrr_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT ON SEQUENCES TO mrr_backup;

COMMENT ON ROLE mrr_backup IS 'MRR logical backup account; read-only';
COMMENT ON ROLE mrr_restore IS 'MRR isolated restore-drill account; CREATEDB only';
