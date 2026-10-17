SET LOCAL lock_timeout='3s';
ALTER TABLE app.mr_image_classification_suggestion
    ADD COLUMN auto_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
      CHECK(auto_status IN ('PENDING','APPLIED','REVIEW_REQUIRED','SKIPPED'));
ALTER TABLE app.mr_image_type_audit DROP CONSTRAINT ck_mr_image_type_audit_operation;
ALTER TABLE app.mr_image_type_audit ADD CONSTRAINT ck_mr_image_type_audit_operation CHECK(operation IN (
    'MANUAL_EDIT','ACCEPT_SUGGESTION','BATCH_ACCEPT','OVERRIDE_SUGGESTION','AUTO_APPLY_UNCLASSIFIED','ROLLBACK_AUTO'));
ALTER TABLE app.mr_image_type_audit
    ADD COLUMN batch_id VARCHAR(36) REFERENCES app.mr_ocr_batch(id),
    ADD COLUMN policy_checksum CHAR(64),
    ADD COLUMN reverts_audit_id BIGINT REFERENCES app.mr_image_type_audit(id);
CREATE UNIQUE INDEX uk_type_audit_revert ON app.mr_image_type_audit(reverts_audit_id) WHERE reverts_audit_id IS NOT NULL;
CREATE INDEX idx_type_audit_batch ON app.mr_image_type_audit(batch_id,id) WHERE batch_id IS NOT NULL;
ALTER TABLE app.mr_ocr_batch ADD COLUMN policy_checksum CHAR(64);
ALTER TABLE app.mr_ocr_batch ADD COLUMN auto_applied_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app.mr_ocr_batch ADD COLUMN review_required_count INTEGER NOT NULL DEFAULT 0;
INSERT INTO app.mr_system_settings(setting_key,setting_value,description,updated_by)
VALUES('classificationAutoApplyEnabled','false','已验收类别的自动归类总开关；仍需服务端离线证据及批次授权','system')
ON CONFLICT(setting_key) DO NOTHING;
UPDATE app.mr_auth_role SET permissions=CONCAT_WS(',',NULLIF(permissions,''),'classification:auto-apply')
WHERE code='ADMIN' AND POSITION('classification:auto-apply' IN COALESCE(permissions,''))=0;
