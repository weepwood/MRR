-- Backfill image-audit metadata written before LogInterceptor populated audit columns.
WITH derived AS (
    SELECT
        id,
        CASE
            WHEN POSITION('/download/' IN request_uri) > 0 THEN 'DOWNLOAD'
            WHEN POSITION('/oss-image/' IN request_uri) > 0 THEN 'VIEW_OSS_IMAGE'
            WHEN request_uri LIKE '/api/v1/img/image/%' THEN 'VIEW_IMAGE'
            ELSE 'LIST'
        END AS audit_action,
        CASE
            WHEN request_uri LIKE '/api/v1/img/image/%' THEN SPLIT_PART(request_uri, '/', 6)
            ELSE SUBSTRING(request_uri FROM '[^/]+$')
        END AS audit_target,
        CASE
            WHEN POSITION('/download/' IN request_uri) > 0 THEN '下载病案图片压缩包'
            WHEN POSITION('/oss-image/' IN request_uri) > 0 THEN '查看 OSS 病案图片'
            WHEN request_uri LIKE '/api/v1/img/image/%' THEN '查看本地病案图片'
            ELSE '查询病案图片列表'
        END AS audit_description
    FROM app.access_log
    WHERE request_uri LIKE '/api/v1/img/%'
      AND POSITION('/hello' IN request_uri) = 0
)
UPDATE app.access_log AS access_log
SET audit_action = CASE
        WHEN NULLIF(BTRIM(access_log.audit_action), '') IS NULL THEN derived.audit_action
        ELSE access_log.audit_action
    END,
    audit_target = CASE
        WHEN NULLIF(BTRIM(access_log.audit_target), '') IS NULL THEN derived.audit_target
        ELSE access_log.audit_target
    END,
    audit_description = CASE
        WHEN NULLIF(BTRIM(access_log.audit_description), '') IS NULL THEN derived.audit_description
        ELSE access_log.audit_description
    END
FROM derived
WHERE access_log.id = derived.id
  AND (
      NULLIF(BTRIM(access_log.audit_action), '') IS NULL
      OR NULLIF(BTRIM(access_log.audit_target), '') IS NULL
      OR NULLIF(BTRIM(access_log.audit_description), '') IS NULL
  );

CREATE INDEX IF NOT EXISTS idx_access_log_image_audit_action_time
    ON app.access_log (audit_action, access_time DESC)
    WHERE audit_action IN ('LIST', 'DOWNLOAD', 'VIEW_IMAGE', 'VIEW_OSS_IMAGE');
