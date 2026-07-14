#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${POSTGRES_MONITOR_PASSWORD:-}" ]]; then
  echo "POSTGRES_MONITOR_PASSWORD is required" >&2
  exit 1
fi

escaped_password=${POSTGRES_MONITOR_PASSWORD//\'/\'\'}

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mrr_monitor') THEN
        CREATE ROLE mrr_monitor LOGIN PASSWORD '${escaped_password}';
    ELSE
        ALTER ROLE mrr_monitor WITH LOGIN PASSWORD '${escaped_password}';
    END IF;
END
\$\$;
GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO mrr_monitor;
GRANT pg_monitor TO mrr_monitor;
EOSQL
