-- Explicit recovery of a failed fixed-membership observation; preserve its cursor.
ALTER TABLE app.mrr_archive_manifest
    ADD COLUMN recovery_count INTEGER NOT NULL DEFAULT 0 CHECK (recovery_count BETWEEN 0 AND 3);
CREATE TABLE app.mrr_archive_manifest_recovery (
    manifest_id BIGINT NOT NULL REFERENCES app.mrr_archive_manifest(id) ON DELETE CASCADE,
    attempt INTEGER NOT NULL CHECK (attempt BETWEEN 1 AND 3),
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    processed INTEGER NOT NULL CHECK (processed BETWEEN 0 AND 50000),
    resumed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (manifest_id,attempt)
);
