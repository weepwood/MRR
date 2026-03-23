create table main.access_log
(
    id              INTEGER
        primary key autoincrement,
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
    id         INTEGER not null
        primary key,
    BRXH       TEXT,
    BAH        TEXT,
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
    pages    integer
);

create table main.mr_user
(
    id    INTEGER not null
        primary key,
    name  TEXT,
    age   INTEGER,
    email TEXT
);

