-- Bounded operational observations only. No medical facts, source addresses or free text.
CREATE TABLE app.mrr_operations_sample (
    bucket_at TIMESTAMPTZ PRIMARY KEY,
    sampled_at TIMESTAMPTZ NOT NULL,
    readiness_checked_at TIMESTAMPTZ NOT NULL,
    mode VARCHAR(32) NOT NULL CHECK (mode IN ('READ_WRITE','READ_ONLY_DEGRADED','READ_ONLY_MAINTENANCE')),
    critical_failures INTEGER NOT NULL CHECK (critical_failures >= 0),
    heap_used_bytes BIGINT NOT NULL CHECK (heap_used_bytes >= 0),
    heap_max_bytes BIGINT NOT NULL CHECK (heap_max_bytes >= 0),
    scan_total_bytes BIGINT CHECK (scan_total_bytes >= 0),
    storage_open_nodes INTEGER CHECK (storage_open_nodes >= 0),
    storage_waiting INTEGER CHECK (storage_waiting >= 0),
    CHECK (sampled_at >= bucket_at AND sampled_at < bucket_at + INTERVAL '5 minutes')
);
