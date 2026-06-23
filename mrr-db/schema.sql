create table main.access_log
(
    id              INTEGER primary key autoincrement,
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

create table main.mr_patient
(
    id            integer not null,
    idcard        TEXT,
    BAH           TEXT,
    admissiontime TEXT,
    department    TEXT,
    name          TEXT
);

create table main.mr_scan
(
    id         INTEGER not null primary key,
    BRXH       TEXT,
    BAH        TEXT,
    sjh        TEXT,
    filename   TEXT,
    btype      INTEGER,
    pages      INTEGER,
    openerno   TEXT,
    uploaddate TEXT,
    uploadflag INTEGER,
    folder     TEXT
);

create table main.mr_statistics
(
    bah      TEXT,
    cid      TEXT,
    openerno TEXT,
    date     TEXT,
    type     TEXT,
    pages    integer,
    sjh      TEXT
);

create table main.mr_user
(
    id    INTEGER not null primary key,
    name  TEXT,
    age   INTEGER,
    email TEXT
);

create table main.mr_auth_role
(
    code        TEXT primary key,
    name        TEXT not null,
    description TEXT,
    permissions TEXT not null,
    sort_order  INTEGER not null default 0
);

create table main.mr_auth_user
(
    id            INTEGER not null primary key autoincrement,
    username      TEXT not null unique,
    display_name  TEXT,
    password_hash TEXT not null,
    role_code     TEXT not null,
    status        TEXT not null default 'active',
    last_login_at DATETIME,
    created_at    DATETIME not null default CURRENT_TIMESTAMP,
    updated_at    DATETIME not null default CURRENT_TIMESTAMP
);

create index if not exists idx_mr_auth_user_username on main.mr_auth_user (username);
create index if not exists idx_mr_auth_user_role_code on main.mr_auth_user (role_code);

insert or ignore into main.mr_auth_role (code, name, description, permissions, sort_order)
values
    ('ADMIN', '系统管理员', '拥有完整的用户与权限管理能力', 'user:manage,role:read,role:manage,record:read,record:manage,log:read,system:read', 1),
    ('DOCTOR', '医生', '负责病案查询与业务处理', 'record:read,record:edit,search:read,statistics:read', 2),
    ('NURSE', '护士', '负责基础查询与病案协助', 'record:read,search:read', 3);

insert or ignore into main.mr_auth_user (username, display_name, password_hash, role_code, status)
values
    ('br_admin', '系统管理员', 'c6c49412188f4bd8969b7f3997afe001df2cfe77a15e7bb115f102be0a9849cd', 'ADMIN', 'active'),
    ('admin', '系统管理员', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', 'active'),
    ('doctor1', '值班医生', 'f348d5628621f3d8f59c8cabda0f8eb0aa7e0514a90be7571020b1336f26c113', 'DOCTOR', 'active'),
    ('nurse1', '门诊护士', '35608f3146571aa100227a3e68290979ba8a452179a080f888625106076e7de2', 'NURSE', 'active');
