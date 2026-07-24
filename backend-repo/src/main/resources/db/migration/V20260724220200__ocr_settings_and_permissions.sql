-- OCR 默认配置与管理员权限。OCR 功能默认关闭。

INSERT INTO app.mr_system_settings (setting_key, setting_value, description, updated_by)
VALUES
    ('ocrEnabled', 'false', '是否启用本地 OCR 能力', 'system'),
    ('ocrProfile', '', '服务端白名单 OCR 配置名称', 'system'),
    ('ocrLanguages', 'chi_sim+eng', 'OCR 识别语言', 'system'),
    ('ocrMaxConcurrency', '1', 'OCR 最大并发数', 'system'),
    ('ocrPageTimeoutSeconds', '30', 'OCR 单页超时秒数', 'system'),
    ('ocrMaxOutputBytes', '4194304', 'OCR 标准输出和错误输出总上限', 'system'),
    ('ocrAutoProcessNewScans', 'false', '新扫描图片是否自动进入 OCR 队列', 'system'),
    ('ocrLowConfidenceThreshold', '0.70', 'OCR 低质量阈值', 'system'),
    ('classificationBatchReviewThreshold', '0.92', '高置信度批量确认服务端最低阈值', 'system')
ON CONFLICT (setting_key) DO NOTHING;

UPDATE app.mr_auth_role
SET permissions = CONCAT_WS(',', NULLIF(TRIM(permissions), ''),
    'ocr:read',
    'ocr:run',
    'ocr:config',
    'classification:read',
    'classification:review',
    'classification:batch-review',
    'classification:override')
WHERE code = 'ADMIN'
  AND POSITION('ocr:config' IN permissions) = 0;
