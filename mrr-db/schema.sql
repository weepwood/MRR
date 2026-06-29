-- ============================================================
-- MRR Database Schema (SQLite)
-- 完整建表入口，包含所有表结构和种子数据
-- ============================================================

-- 1. 核心业务表：扫描记录
CREATE TABLE IF NOT EXISTS main.mr_scan (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    BRXH            TEXT,
    BAH             TEXT,
    sjh             TEXT,
    filename        TEXT,
    btype           INTEGER,
    pages           INTEGER,
    openerno        TEXT,
    uploaddate      TEXT,
    uploadflag      INTEGER,
    folder          TEXT,
    oss_url         TEXT,
    file_size       INTEGER,
    checksum_md5    TEXT,
    migration_status TEXT DEFAULT 'not_migrated',
    migrated_at     DATETIME
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
    bah      TEXT,
    cid      TEXT,
    openerno TEXT,
    date     TEXT,
    type     TEXT,
    pages    INTEGER,
    sjh      TEXT
);

-- 4. 认证授权表：角色
CREATE TABLE IF NOT EXISTS main.mr_auth_role (
    code        TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    permissions TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0
);

-- 5. 认证授权表：用户
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

-- 6. 运维表：访问日志
CREATE TABLE IF NOT EXISTS main.access_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT,
    client_ip       TEXT,
    request_uri     TEXT,
    method          TEXT,
    user_agent      TEXT,
    access_time     DATETIME,
    query_string    TEXT,
    request_body    TEXT,
    response_status TEXT,
    execute_time    INTEGER,
    referer         TEXT
);

-- 7. 运维表：图片迁移日志
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

-- 8. 运维表：迁移任务
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

-- ============================================
-- 索引
-- ============================================

CREATE INDEX IF NOT EXISTS idx_mr_scan_bah ON main.mr_scan (BAH);
CREATE INDEX IF NOT EXISTS idx_mr_scan_brxh ON main.mr_scan (BRXH);
CREATE INDEX IF NOT EXISTS idx_mr_scan_sjh ON main.mr_scan (sjh);
CREATE INDEX IF NOT EXISTS idx_mr_scan_migration_status ON main.mr_scan (migration_status);
CREATE INDEX IF NOT EXISTS idx_mr_scan_oss_url ON main.mr_scan (oss_url);

CREATE INDEX IF NOT EXISTS idx_mr_statistics_bah ON main.mr_statistics (bah);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_date ON main.mr_statistics (date);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_type ON main.mr_statistics (type);
CREATE INDEX IF NOT EXISTS idx_mr_statistics_sjh ON main.mr_statistics (sjh);

CREATE INDEX IF NOT EXISTS idx_mr_patient_idcard ON main.mr_patient (idcard);

CREATE INDEX IF NOT EXISTS idx_mr_auth_user_username ON main.mr_auth_user (username);
CREATE INDEX IF NOT EXISTS idx_mr_auth_user_role_code ON main.mr_auth_user (role_code);

CREATE INDEX IF NOT EXISTS idx_access_log_access_time ON main.access_log (access_time);
CREATE INDEX IF NOT EXISTS idx_access_log_client_ip ON main.access_log (client_ip);
CREATE INDEX IF NOT EXISTS idx_access_log_request_uri ON main.access_log (request_uri);
CREATE INDEX IF NOT EXISTS idx_access_log_method ON main.access_log (method);
CREATE INDEX IF NOT EXISTS idx_access_log_response_status ON main.access_log (response_status);
CREATE INDEX IF NOT EXISTS idx_access_log_method_status ON main.access_log (method, response_status);

CREATE INDEX IF NOT EXISTS idx_migration_status ON main.image_migration_log (migration_status);
CREATE INDEX IF NOT EXISTS idx_migration_scan_id ON main.image_migration_log (scan_id);
CREATE INDEX IF NOT EXISTS idx_migration_created_at ON main.image_migration_log (created_at);
CREATE INDEX IF NOT EXISTS idx_migration_status_created ON main.image_migration_log (migration_status, created_at);

CREATE INDEX IF NOT EXISTS idx_migration_job_status ON main.migration_job (status);
CREATE INDEX IF NOT EXISTS idx_migration_job_created_at ON main.migration_job (created_at);

-- ============================================
-- 种子数据：角色
-- ============================================
INSERT OR IGNORE INTO main.mr_auth_role (code, name, description, permissions, sort_order)
VALUES
    ('ADMIN',  'System Administrator', 'Full user and permission management access',
     'user:manage,role:manage,record:manage,log:read,system:read,test:read,statistics:read', 1),
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
