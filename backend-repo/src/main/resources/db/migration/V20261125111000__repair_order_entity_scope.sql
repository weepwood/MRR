-- Extend the existing order lifecycle to one additional explicit entity type.
ALTER TABLE app.mrr_data_quality_repair_order
    ADD COLUMN entity_type VARCHAR(32) NOT NULL DEFAULT 'mr_scan',
    ADD COLUMN entity_id BIGINT;
UPDATE app.mrr_data_quality_repair_order SET entity_id=scan_id;
ALTER TABLE app.mrr_data_quality_repair_order
    ALTER COLUMN entity_id SET NOT NULL,
    ALTER COLUMN scan_id DROP NOT NULL,
    ADD CONSTRAINT ck_quality_repair_entity CHECK (
        entity_id > 0 AND (
            (entity_type='mr_scan' AND scan_id IS NOT NULL AND entity_id=scan_id)
            OR (entity_type='mr_archive_box_record' AND scan_id IS NULL)
        )
    );
DROP INDEX app.ux_quality_repair_active_scan;
DROP INDEX app.idx_quality_repair_scan_history;
CREATE UNIQUE INDEX ux_quality_repair_active_entity
    ON app.mrr_data_quality_repair_order(entity_type,entity_id)
    WHERE status IN ('PROPOSED','APPROVED','APPLIED','VERIFIED');
CREATE INDEX idx_quality_repair_entity_history
    ON app.mrr_data_quality_repair_order(entity_type,entity_id,id DESC);
