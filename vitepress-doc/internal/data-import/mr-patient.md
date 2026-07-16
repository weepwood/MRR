# `mr_patient` 患者数据导入

本文对应历史患者 CSV：

```csv
brxh,id,bah,name,idcard,ruyuan,admissiontime,department,chuangwei,bingqu,keshicode,bingqucode
```

适用于 PostgreSQL 16、业务 Schema 为 `app` 的 MRR 数据库。执行前必须先让后端完成 Flyway 迁移，确认 `ruyuan`、`bingqu`、`chuangwei` 三列已经存在。

## 字段映射

| CSV 列 | 目标列 | 处理规则 |
| --- | --- | --- |
| `bah` | `app.mr_patient.bah` | 只去除首尾空格，保留原始长度和前导零 |
| `name` | `name` | 空字符串转 `NULL` |
| `idcard` | `idcard` | 空字符串转 `NULL`，按敏感信息保护 |
| `ruyuan` | `ruyuan` | 目标类型为 `DATE`，只保留日期部分 |
| `admissiontime` | `admissiontime` | 目标仍为文本，建议统一为 `YYYY-MM-DD HH:MM` |
| `department` | `department` | 空字符串转 `NULL` |
| `bingqu` | `bingqu` | 字段名必须是 `bingqu`，不是 `binqu` |
| `chuangwei` | `chuangwei` | 空字符串转 `NULL` |
| `brxh`、源 `id`、`keshicode`、`bingqucode` | 不导入 | 当前正式表没有对应业务列；不要把源 `id` 写入数据库自增主键 |

> `ruyuan` 的数据库类型是 `DATE`。即使 CSV 写成 `2026-07-01 08:30`，落库后也只保留 `2026-07-01`；完整入院时间应继续放在 `admissiontime`。

## 导入前检查

在目标数据库执行：

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'mr_patient'
ORDER BY ordinal_position;
```

至少应看到：

```text
id, idcard, bah, admissiontime, department, name, ruyuan, bingqu, chuangwei
```

备份目标表：

```powershell
pg_dump -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -t app.mr_patient `
  -f D:/MRR-Backup/mr_patient_before_import.sql
```

## 完整 `psql` 脚本

将下列内容保存为 `import-mr-patient.sql`。`\copy` 是 `psql` 元命令，必须使用 `psql -f` 执行；不要直接粘贴到 pgAdmin Query Tool。

```sql
\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';

CREATE TEMP TABLE import_mr_patient_raw (
    brxh              text,
    source_id          text,
    bah                text,
    name               text,
    idcard             text,
    ruyuan_text        text,
    admissiontime_text text,
    department         text,
    chuangwei          text,
    bingqu             text,
    keshicode          text,
    bingqucode         text
);

\copy import_mr_patient_raw FROM 'D:/MRR-Data/mr_patient.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

CREATE TEMP TABLE import_mr_patient_normalized AS
SELECT DISTINCT
    NULLIF(BTRIM(bah), '') AS bah,
    NULLIF(BTRIM(name), '') AS name,
    NULLIF(BTRIM(idcard), '') AS idcard,
    CASE
        WHEN NULLIF(BTRIM(ruyuan_text), '') IS NULL THEN NULL
        ELSE app.try_parse_date(LEFT(BTRIM(ruyuan_text), 10))
    END AS ruyuan,
    CASE
        WHEN NULLIF(BTRIM(admissiontime_text), '') IS NULL THEN NULL
        ELSE REPLACE(BTRIM(admissiontime_text), '/', '-')
    END AS admissiontime,
    NULLIF(BTRIM(department), '') AS department,
    NULLIF(BTRIM(bingqu), '') AS bingqu,
    NULLIF(BTRIM(chuangwei), '') AS chuangwei
FROM import_mr_patient_raw;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM import_mr_patient_normalized
        WHERE bah IS NULL
    ) THEN
        RAISE EXCEPTION 'mr_patient.csv 存在空病案号 bah';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_patient_raw
        WHERE NULLIF(BTRIM(ruyuan_text), '') IS NOT NULL
          AND app.try_parse_date(LEFT(BTRIM(ruyuan_text), 10)) IS NULL
    ) THEN
        RAISE EXCEPTION 'mr_patient.csv 存在无法解析的 ruyuan 日期';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_patient_raw
        WHERE NULLIF(BTRIM(admissiontime_text), '') IS NOT NULL
          AND BTRIM(admissiontime_text) !~ '^\d{4}[-/]\d{2}[-/]\d{2}[ T]\d{2}:\d{2}(:\d{2})?$'
    ) THEN
        RAISE EXCEPTION 'admissiontime 必须是 YYYY-MM-DD HH:MM 或 YYYY-MM-DD HH:MM:SS';
    END IF;
END
$$;

BEGIN;

INSERT INTO app.mr_patient (
    bah,
    name,
    idcard,
    ruyuan,
    admissiontime,
    department,
    bingqu,
    chuangwei
)
SELECT
    s.bah,
    s.name,
    s.idcard,
    s.ruyuan,
    s.admissiontime,
    s.department,
    s.bingqu,
    s.chuangwei
FROM import_mr_patient_normalized s
WHERE NOT EXISTS (
    SELECT 1
    FROM app.mr_patient p
    WHERE p.bah IS NOT DISTINCT FROM s.bah
      AND p.name IS NOT DISTINCT FROM s.name
      AND p.idcard IS NOT DISTINCT FROM s.idcard
      AND p.ruyuan IS NOT DISTINCT FROM s.ruyuan
      AND p.admissiontime IS NOT DISTINCT FROM s.admissiontime
      AND p.department IS NOT DISTINCT FROM s.department
      AND p.bingqu IS NOT DISTINCT FROM s.bingqu
      AND p.chuangwei IS NOT DISTINCT FROM s.chuangwei
);

COMMIT;

ANALYZE app.mr_patient;

SELECT
    (SELECT count(*) FROM import_mr_patient_raw) AS csv_rows,
    (SELECT count(*) FROM import_mr_patient_normalized) AS normalized_rows,
    (SELECT count(*) FROM app.mr_patient) AS target_total_rows;
```

执行：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -v ON_ERROR_STOP=1 `
  -f D:/MRR-Scripts/import-mr-patient.sql
```

## 导入后校验

### 检查同一病案号的重复患者行

```sql
SELECT bah, name, idcard, count(*)
FROM app.mr_patient
GROUP BY bah, name, idcard
HAVING count(*) > 1
ORDER BY count(*) DESC, bah;
```

### 检查字段格式

```sql
SELECT count(*) AS empty_bah
FROM app.mr_patient
WHERE NULLIF(BTRIM(bah), '') IS NULL;

SELECT count(*) AS non_iso_admissiontime
FROM app.mr_patient
WHERE admissiontime IS NOT NULL
  AND admissiontime !~ '^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$';
```

### 抽样核对

```sql
SELECT id, bah, name, idcard, ruyuan, admissiontime,
       department, bingqu, chuangwei
FROM app.mr_patient
ORDER BY id
LIMIT 100;
```

身份证号不得复制到普通日志、截图或公开问题单中。排障时只保留末四位或使用不可逆脱敏值。
