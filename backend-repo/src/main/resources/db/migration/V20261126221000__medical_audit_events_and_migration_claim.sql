-- Separate experimental event ledger. Existing access_log and external audit semantics unchanged.
CREATE TABLE app.medical_audit_event (
    event_id varchar(40) PRIMARY KEY,
    correlation_id varchar(36) NOT NULL,
    request_id varchar(64) NOT NULL,
    phase varchar(8) NOT NULL CHECK (phase IN ('INTENT','OUTCOME')),
    actor_id bigint NOT NULL,
    method varchar(12) NOT NULL,
    route varchar(256) NOT NULL,
    target varchar(128),
    observed_at timestamp NOT NULL,
    observation varchar(32) NOT NULL,
    http_status varchar(3),
    UNIQUE (correlation_id, phase),
    CHECK ((phase='INTENT' AND observation='ADMITTED' AND http_status IS NULL)
        OR (phase='OUTCOME' AND observation IN ('HTTP_COMPLETED','HTTP_EXCEPTION') AND http_status IS NOT NULL))
);
CREATE INDEX idx_medical_audit_event_observed ON app.medical_audit_event(observed_at);
ALTER TABLE app.mr_scan ADD COLUMN migration_claim_id varchar(36);
COMMENT ON COLUMN app.mr_scan.migration_claim_id IS 'Unique migration attempt fence; not an authorization credential';
