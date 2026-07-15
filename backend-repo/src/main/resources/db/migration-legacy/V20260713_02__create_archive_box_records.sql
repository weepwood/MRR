-- 实体病案装箱位置表：一条记录对应一份病案当前的装箱位置。
CREATE TABLE IF NOT EXISTS mr_archive_box_record (
    id              BIGSERIAL PRIMARY KEY,
    bah             VARCHAR(64),
    sjh             VARCHAR(64),
    box_no          VARCHAR(64),
    expected_box_no VARCHAR(64),
    status          VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    remark          VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_box_record_code
        CHECK (
            NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL
            OR NULLIF(TRIM(COALESCE(sjh, '')), '') IS NOT NULL
        ),
    CONSTRAINT chk_archive_box_record_status
        CHECK (status IN ('NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER')),
    CONSTRAINT chk_archive_box_record_box
        CHECK (
            status = 'MISSING'
            OR NULLIF(TRIM(COALESCE(box_no, '')), '') IS NOT NULL
        ),
    CONSTRAINT uq_archive_box_record_sjh UNIQUE (sjh)
);

COMMENT ON TABLE mr_archive_box_record IS '实体病案装箱位置记录';
COMMENT ON COLUMN mr_archive_box_record.bah IS '病案号，纯数字不足 8 位时由业务层补零';
COMMENT ON COLUMN mr_archive_box_record.sjh IS '上架号，纯数字不足 8 位时由业务层补零';
COMMENT ON COLUMN mr_archive_box_record.box_no IS '实际存放箱号，缺失状态可为空';
COMMENT ON COLUMN mr_archive_box_record.expected_box_no IS '原计划或应存放箱号';
COMMENT ON COLUMN mr_archive_box_record.status IS 'NORMAL/MISSING/MISPLACED/CONFLICT/OTHER';
COMMENT ON COLUMN mr_archive_box_record.remark IS '异常说明或补充备注';

CREATE INDEX IF NOT EXISTS idx_archive_box_record_bah
    ON mr_archive_box_record (bah);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_box_no
    ON mr_archive_box_record (box_no);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_expected_box_no
    ON mr_archive_box_record (expected_box_no);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_status
    ON mr_archive_box_record (status);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_updated_at
    ON mr_archive_box_record (updated_at DESC);

-- 导入 zx.csv 中的首批装箱数据。上架号按项目既有规则统一为 8 位。
INSERT INTO mr_archive_box_record
    (sjh, box_no, expected_box_no, status, remark)
VALUES
    ('00787273', NULL, '5740262389', 'MISSING', '原应在箱号 5740262389，未找到实际存放位置'),
    ('00789144', NULL, '5740262095', 'MISSING', '原应在箱号 5740262095，未找到实际存放位置'),
    ('00796111', NULL, '5740262318', 'MISSING', '原应在箱号 5740262318，未找到实际存放位置'),
    ('00799040', NULL, '5740262477', 'MISSING', '原应在箱号 5740262477，未找到实际存放位置'),
    ('00802071', NULL, '5740262064', 'MISSING', '原应在箱号 5740262064，未找到实际存放位置'),
    ('00812670', NULL, '5740262892', 'MISSING', '原应在箱号 5740262892，未找到实际存放位置'),
    ('00814796', NULL, '5740262685', 'MISSING', '原应在箱号 5740262685，未找到实际存放位置'),
    ('00815812', '5740262326', '5740262880', 'MISPLACED', '原应在箱号 5740262880，实际在箱号 5740262326（来源：装箱.csv其他范围）'),
    ('00815813', '5740262880', '5740262326', 'MISPLACED', '原应在箱号 5740262326，实际在箱号 5740262880（来源：装箱.csv其他范围）'),
    ('00815814', '5740262880', '5740262326', 'MISPLACED', '原应在箱号 5740262326，实际在箱号 5740262880（来源：装箱.csv其他范围）'),
    ('00817595', '5740262916', '5740259799', 'CONFLICT', '装箱箱号: 5740262916，特殊装箱箱号: 5740259799'),
    ('00787263', '5740262389', NULL, 'NORMAL', NULL),
    ('00787264', '5740262389', NULL, 'NORMAL', NULL),
    ('00787265', '5740262389', NULL, 'NORMAL', NULL),
    ('00787266', '5740262389', NULL, 'NORMAL', NULL),
    ('00787267', '5740262389', NULL, 'NORMAL', NULL),
    ('00787268', '5740262389', NULL, 'NORMAL', NULL),
    ('00787269', '5740262389', NULL, 'NORMAL', NULL)
ON CONFLICT (sjh) DO NOTHING;
