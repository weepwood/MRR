-- 为统计明细补充患者与住院信息，字段保持可空以兼容历史数据。
ALTER TABLE mr_statistics
    ADD COLUMN IF NOT EXISTS patientname VARCHAR(100),
    ADD COLUMN IF NOT EXISTS inpatientdepartment VARCHAR(100),
    ADD COLUMN IF NOT EXISTS patientid VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dischargedate VARCHAR(32);

COMMENT ON COLUMN mr_statistics.patientname IS '病人姓名';
COMMENT ON COLUMN mr_statistics.inpatientdepartment IS '住院科室';
COMMENT ON COLUMN mr_statistics.patientid IS '病人ID';
COMMENT ON COLUMN mr_statistics.dischargedate IS '出院日期';

CREATE INDEX IF NOT EXISTS idx_mr_statistics_patientname
    ON mr_statistics (patientname);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_inpatientdepartment
    ON mr_statistics (inpatientdepartment);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_patientid
    ON mr_statistics (patientid);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_dischargedate
    ON mr_statistics (dischargedate);
