-- 修复旧版本将 /api/v1/img/search 的审计对象记录为字面量 search 的问题。
-- 仅能恢复 query_string 中仍保留 bah / sjh 原值的记录；已被旧版本写成 [REDACTED] 的值无法还原。
WITH parsed AS (
    SELECT
        id,
        NULLIF((regexp_match(query_string, '(^|&)bah=([^&]+)'))[2], '') AS bah,
        NULLIF((regexp_match(query_string, '(^|&)sjh=([^&]+)'))[2], '') AS sjh
    FROM app.access_log
    WHERE audit_target = 'search'
      AND request_uri = '/api/v1/img/search'
      AND query_string IS NOT NULL
)
UPDATE app.access_log log
SET audit_target = CASE
    WHEN parsed.sjh IS NOT NULL AND parsed.bah IS NOT NULL THEN parsed.bah || ':' || parsed.sjh
    WHEN parsed.bah IS NOT NULL THEN parsed.bah
    WHEN parsed.sjh IS NOT NULL THEN 'sjh:' || parsed.sjh
    ELSE log.audit_target
END
FROM parsed
WHERE log.id = parsed.id
  AND (parsed.bah IS NOT NULL OR parsed.sjh IS NOT NULL)
  AND COALESCE(parsed.bah, '') NOT LIKE '%[REDACTED]%'
  AND COALESCE(parsed.sjh, '') NOT LIKE '%[REDACTED]%';
