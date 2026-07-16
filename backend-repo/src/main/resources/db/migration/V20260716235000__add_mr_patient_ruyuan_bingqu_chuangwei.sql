-- 扩展患者信息：入院日期、病区、床位。
-- 所有字段允许为空，以兼容现有 mr_patient 数据。

ALTER TABLE app.mr_patient
    ADD COLUMN IF NOT EXISTS ruyuan DATE,
    ADD COLUMN IF NOT EXISTS bingqu TEXT,
    ADD COLUMN IF NOT EXISTS chuangwei TEXT;

COMMENT ON COLUMN app.mr_patient.ruyuan IS '入院日期';
COMMENT ON COLUMN app.mr_patient.bingqu IS '病区';
COMMENT ON COLUMN app.mr_patient.chuangwei IS '床位';
