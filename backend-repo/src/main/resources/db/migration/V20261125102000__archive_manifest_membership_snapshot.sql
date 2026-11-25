-- Capture only the selected archive's active scan membership, not the entire scan table.
-- Historical observations remain explicitly legacy (snapshot_count NULL).
ALTER TABLE app.mrr_archive_manifest
    ADD COLUMN snapshot_count INTEGER CHECK (snapshot_count BETWEEN 0 AND 50000);
ALTER TABLE app.mrr_archive_manifest DROP CONSTRAINT mrr_archive_manifest_processed_check;
ALTER TABLE app.mrr_archive_manifest ADD CONSTRAINT ck_manifest_processed_bound
    CHECK (processed BETWEEN 0 AND COALESCE(snapshot_count,5000));
ALTER TABLE app.mrr_archive_manifest_anomaly DROP CONSTRAINT mrr_archive_manifest_anomaly_ordinal_check;
ALTER TABLE app.mrr_archive_manifest_anomaly ADD CONSTRAINT ck_manifest_anomaly_ordinal
    CHECK (ordinal BETWEEN 1 AND 50000);
CREATE TABLE app.mrr_archive_manifest_member (
    manifest_id BIGINT NOT NULL REFERENCES app.mrr_archive_manifest(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 50000),
    scan_id INTEGER NOT NULL,
    pages INTEGER,
    fingerprint TEXT NOT NULL,
    PRIMARY KEY (manifest_id, ordinal),
    UNIQUE (manifest_id, scan_id)
);
CREATE INDEX idx_manifest_retention ON app.mrr_archive_manifest(updated_at,id)
    WHERE status NOT IN ('PENDING','RUNNING');
CREATE INDEX idx_quality_repair_archive ON app.mrr_data_quality_repair_order(target_archive_id,id DESC);
