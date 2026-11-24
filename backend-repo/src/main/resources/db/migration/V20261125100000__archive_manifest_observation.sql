-- Versioned, bounded archive observations; mr_scan remains the file fact table.
CREATE TABLE app.mrr_archive_manifest (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    archive_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    cancelled_by BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','COMPLETED','PARTIAL','CANCELLED','FAILED')),
    upper_scan_id INTEGER NOT NULL CHECK (upper_scan_id >= 0),
    cursor_page INTEGER,
    cursor_scan_id INTEGER NOT NULL DEFAULT 0,
    processed INTEGER NOT NULL DEFAULT 0 CHECK (processed BETWEEN 0 AND 5000),
    verified INTEGER NOT NULL DEFAULT 0 CHECK (verified >= 0),
    anomalies INTEGER NOT NULL DEFAULT 0 CHECK (anomalies >= 0),
    digest_chain VARCHAR(64) DEFAULT '',
    failures INTEGER NOT NULL DEFAULT 0 CHECK (failures BETWEEN 0 AND 3),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK (processed = verified + anomalies),
    CHECK ((status = 'RUNNING') = (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK (digest_chain IS NULL OR digest_chain = '' OR digest_chain ~ '^[0-9a-f]{64}$')
);
-- One explicitly requested active observation globally; no unbounded queue.
CREATE UNIQUE INDEX ux_mrr_archive_manifest_active
    ON app.mrr_archive_manifest ((1)) WHERE status IN ('PENDING','RUNNING');
CREATE INDEX idx_mrr_archive_manifest_archive
    ON app.mrr_archive_manifest (archive_id, id DESC);
CREATE TABLE app.mrr_archive_manifest_anomaly (
    manifest_id BIGINT NOT NULL REFERENCES app.mrr_archive_manifest(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 5000),
    scan_id INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    bytes_read BIGINT NOT NULL CHECK (bytes_read >= 0),
    sha256 VARCHAR(64),
    checked_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (manifest_id, ordinal)
);
