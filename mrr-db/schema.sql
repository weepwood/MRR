-- ============================================================
-- MRR Database Schema (SQLite)
-- 完整建表入口，包含所有表结构和种子数据
-- 用于开发/测试环境（生产环境使用 PostgreSQL schema）
-- ============================================================

-- 1. 核心业务表：扫描记录（含 OSS 迁移字段）
CREATE TABLE IF NOT EXISTS main.mr_scan (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    BRXH             TEXT,
    BAH              TEXT,
    sjh              TEXT,
    filename         TEXT,
    btype            INTEGER,
    pages            INTEGER,
    openerno         TEXT,
    uploaddate       TEXT,
    uploadflag       INTEGER,
    folder           TEXT,
    oss_url          TEXT,
    file_size        INTEGER,
    checksum_md5     TEXT,
    migration_status TEXT DEFAULT 'not_migrated',
    migrated_at      DATETIME
);

-- 2. 核心业务表：患者信息
CREATE TABLE IF NOT EXISTS main.mr_patient (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    idcard        TEXT,
    BAH           TEXT,
    admissiontime TEXT,
    department    TEXT,
    name          TEXT
);

-- 3. 核心业务表：统计
CREATE TABLE IF NOT EXISTS main.mr_statistics (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    bah                   TEXT,
    cid                   TEXT,
    openerno              TEXT,
    date                  TEXT,
    type                  TEXT,
    pages                 INTEGER,
    sjh                   TEXT,
    patientname           TEXT,
    inpatientdepartment   TEXT,
    patientid             TEXT,
    dischargedate         TEXT
);

-- 4. 核心业务表：实体病案装箱位置
CREATE TABLE IF NOT EXISTS main.mr_archive_box_record (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    bah             TEXT,
    sjh             TEXT UNIQUE,
    box_no          TEXT,
    expected_box_no TEXT,
    status          TEXT NOT NULL DEFAULT 'NORMAL'
                    CHECK (status IN ('NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER')),
    remark          TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL
        OR NULLIF(TRIM(COALESCE(sjh, '')), '') IS NOT NULL
    ),
    CHECK (
        status = 'MISSING'
        OR NULLIF(TRIM(COALESCE(box_no, '')), '') IS NOT NULL
    )
);

-- 5. 认证授权表：角色
CREATE TABLE IF NOT EXISTS main.mr_auth_role (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    permissions TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

-- 6. 认证授权表：用户
CREATE TABLE IF NOT EXISTS main.mr_auth_user (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL UNIQUE,
    display_name  TEXT,
    password_hash TEXT NOT NULL,
    role_code     TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'active',
    last_login_at DATETIME,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. 运维表：访问日志
CREATE TABLE IF NOT EXISTS main.access_log (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    username          TEXT,
    client_ip         TEXT,
    request_uri       TEXT,
    method            TEXT,
    user_agent        TEXT,
    access_time       DATETIME,
    query_string      TEXT,
    request_body      TEXT,
    response_status   TEXT,
    execute_time      INTEGER,
    referer           TEXT,
    audit_action      TEXT,
    audit_target      TEXT,
    audit_description TEXT
);

-- 8. 运维表：图片迁移日志
CREATE TABLE IF NOT EXISTS main.image_migration_log (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    scan_id          INTEGER NOT NULL,
    local_path       TEXT NOT NULL,
    oss_url          TEXT,
    migration_status TEXT NOT NULL DEFAULT 'pending',
    error_message    TEXT,
    file_size        INTEGER,
    checksum_md5     TEXT,
    migrated_at      DATETIME,
    verified_at      DATETIME,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 9. 运维表：迁移任务
CREATE TABLE IF NOT EXISTS main.migration_job (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    status          TEXT NOT NULL DEFAULT 'pending',
    total_count     INTEGER NOT NULL DEFAULT 0,
    processed_count INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    rate            REAL DEFAULT 0,
    error_message   TEXT,
    created_by      TEXT,
    started_at      DATETIME,
    completed_at    DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. 系统表：系统设置
CREATE TABLE IF NOT EXISTS main.mr_system_settings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key     TEXT    NOT NULL UNIQUE,
    setting_value   TEXT,
    description     TEXT,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      TEXT
);

CREATE TABLE IF NOT EXISTS main.mr_archive_search_history (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id        INTEGER NOT NULL,
    bah            TEXT,
    sjh            TEXT,
    success        INTEGER NOT NULL,
    image_count    INTEGER NOT NULL DEFAULT 0 CHECK (image_count >= 0),
    query_count    INTEGER NOT NULL DEFAULT 1 CHECK (query_count >= 0),
    failure_reason TEXT,
    favorite       INTEGER NOT NULL DEFAULT 0,
    searched_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (NULLIF(TRIM(COALESCE(bah, '')), '') IS NOT NULL OR NULLIF(TRIM(COALESCE(sjh, '')), '') IS NOT NULL)
);

-- ============================================
-- 索引
-- ============================================

-- mr_scan 索引
CREATE INDEX IF NOT EXISTS idx_mr_scan_bah ON mr_scan (BAH);
CREATE INDEX IF NOT EXISTS idx_mr_scan_brxh ON mr_scan (BRXH);
CREATE INDEX IF NOT EXISTS idx_mr_scan_sjh ON mr_scan (sjh);
CREATE INDEX IF NOT EXISTS idx_mr_scan_folder_bah ON mr_scan (folder, BAH);
CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_status ON mr_scan (migration_status);
CREATE INDEX IF NOT EXISTS idx_mr_scan_oss_url ON mr_scan (oss_url) WHERE oss_url IS NOT NULL;

-- mr_statistics 索引
CREATE INDEX IF NOT EXISTS idx_mr_statistics_bah ON mr_statistics (bah);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_date ON mr_statistics (date);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_type ON mr_statistics (type);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_sjh ON mr_statistics (sjh);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_patientname ON mr_statistics (patientname);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_inpatientdepartment ON mr_statistics (inpatientdepartment);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_patientid ON mr_statistics (patientid);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_dischargedate ON mr_statistics (dischargedate);

-- mr_archive_box_record 索引
CREATE INDEX IF NOT EXISTS idx_archive_box_record_bah ON mr_archive_box_record (bah);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_box_no ON mr_archive_box_record (box_no);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_expected_box_no ON mr_archive_box_record (expected_box_no);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_status ON mr_archive_box_record (status);
CREATE INDEX IF NOT EXISTS idx_archive_box_record_updated_at ON mr_archive_box_record (updated_at DESC);

-- mr_patient 索引
CREATE INDEX IF NOT EXISTS idx_mr_patient_idcard ON mr_patient (idcard);
CREATE INDEX IF NOT EXISTS idx_mr_patient_bah ON mr_patient (BAH);

-- mr_auth_user 索引
CREATE INDEX IF NOT EXISTS idx_mr_auth_user_username ON mr_auth_user (username);
CREATE INDEX IF NOT EXISTS idx_mr_auth_user_role_code ON mr_auth_user (role_code);

-- access_log 索引
CREATE INDEX IF NOT EXISTS idx_access_log_access_time ON access_log (access_time);
CREATE INDEX IF NOT EXISTS idx_access_log_client_ip ON access_log (client_ip);
CREATE INDEX IF NOT EXISTS idx_access_log_request_uri ON access_log (request_uri);
CREATE INDEX IF NOT EXISTS idx_access_log_method ON access_log (method);
CREATE INDEX IF NOT EXISTS idx_access_log_response_status ON access_log (response_status);
CREATE INDEX IF NOT EXISTS idx_access_log_method_status ON access_log (method, response_status);

-- image_migration_log 索引
CREATE INDEX IF NOT EXISTS idx_migration_status ON image_migration_log (migration_status);
CREATE INDEX IF NOT EXISTS idx_migration_scan_id ON image_migration_log (scan_id);
CREATE INDEX IF NOT EXISTS idx_migration_created_at ON image_migration_log (created_at);
CREATE INDEX IF NOT EXISTS idx_migration_status_created ON image_migration_log (migration_status, created_at);

-- migration_job 索引
CREATE INDEX IF NOT EXISTS idx_migration_job_status ON migration_job (status);
CREATE INDEX IF NOT EXISTS idx_migration_job_created_at ON migration_job (created_at);
CREATE INDEX IF NOT EXISTS idx_archive_search_history_user_time ON mr_archive_search_history (user_id, searched_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_archive_search_history_user_favorite ON mr_archive_search_history (user_id, favorite, searched_at DESC);

-- ============================================
-- 首批装箱数据：来源 zx.csv
-- ============================================
INSERT OR IGNORE INTO main.mr_archive_box_record
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
    ('00787269', '5740262389', NULL, 'NORMAL', NULL);

-- ============================================
-- 种子数据：角色
-- ============================================
INSERT OR IGNORE INTO main.mr_auth_role (code, name, description, permissions, sort_order)
VALUES
    ('ADMIN',  'System Administrator', 'Full user and permission management access',
     'user:manage,role:manage,record:manage,log:read,system:read,statistics:read', 1),
    ('DOCTOR', 'Doctor', 'Query and handle medical records',
     'record:edit,search:read,statistics:read', 2),
    ('NURSE',  'Nurse', 'Assist with basic queries and records',
     'record:read,search:read', 3);

-- ============================================
-- 种子数据：用户（bcrypt 密码哈希）
-- ============================================
INSERT OR IGNORE INTO main.mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES
    ('br_admin', 'System Administrator', 'c6c49412188f4bd8969b7f3997afe001df2cfe77a15e7bb115f102be0a9849cd', 'ADMIN',  'active'),
    ('admin',    'System Administrator', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN',  'active'),
    ('doctor1',  'Duty Doctor',          'f348d5628621f3d8f59c8cabda0f8eb0aa7e0514a90be7571020b1336f26c113', 'DOCTOR', 'active'),
    ('nurse1',   'Outpatient Nurse',     '35608f3146571aa100227a3e68290979ba8a452179a080f888625106076e7de2', 'NURSE',  'active');
