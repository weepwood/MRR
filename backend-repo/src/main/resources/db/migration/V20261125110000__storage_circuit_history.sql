-- Redacted event history; circuit_id identifies one runtime circuit, not a host.
CREATE TABLE app.mrr_storage_circuit_event (
    circuit_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence > 0),
    source_kind VARCHAR(8) NOT NULL CHECK (source_kind IN ('NAS','HTTP','NGINX','OSS')),
    node_ordinal INTEGER NOT NULL CHECK (node_ordinal > 0),
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('OPENED','PROBE_STARTED','METADATA_ONLY','RECOVERED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (circuit_id, sequence)
);
CREATE INDEX idx_storage_circuit_event_time ON app.mrr_storage_circuit_event(occurred_at DESC,circuit_id,sequence);
