-- Preserve completed repair evidence; an explicitly proposed recurrence starts a new approval.
ALTER TABLE app.mrr_data_quality_repair_order
    ADD COLUMN reopened_from BIGINT REFERENCES app.mrr_data_quality_repair_order(id);
ALTER TABLE app.mrr_data_quality_repair_order
    DROP CONSTRAINT mrr_data_quality_repair_order_status_check,
    ADD CONSTRAINT ck_quality_repair_status CHECK (status IN ('PROPOSED','APPROVED','APPLIED','VERIFIED','CANCELLED','COMPENSATED','SUPERSEDED')),
    ADD CONSTRAINT ck_quality_repair_superseded CHECK (status <> 'SUPERSEDED' OR (approved_by IS NOT NULL AND applied_revision IS NOT NULL)),
    ADD CONSTRAINT ck_quality_repair_lineage CHECK (reopened_from IS NULL OR reopened_from < id);
CREATE UNIQUE INDEX ux_quality_repair_reopened_from ON app.mrr_data_quality_repair_order(reopened_from) WHERE reopened_from IS NOT NULL;
CREATE INDEX idx_quality_repair_scan_history ON app.mrr_data_quality_repair_order(scan_id,id DESC);
ALTER TABLE app.mrr_data_quality_repair_event
    DROP CONSTRAINT mrr_data_quality_repair_event_action_check,
    ADD CONSTRAINT ck_quality_repair_event_action CHECK (action IN ('PROPOSED','APPROVED','APPLIED','VERIFIED','CANCELLED','COMPENSATED','SUPERSEDED'));
