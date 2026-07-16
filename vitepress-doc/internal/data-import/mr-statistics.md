# `mr_statistics` 统计数据导入

本文对应整理后的统计 CSV：

```csv
bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate,brxh
```

`mr_statistics` 导入后，数据库触发器会同步创建或更新 `mr_archive`，并自动填写 `archive_id`。因此不要在 CSV 中自行提供 `id` 或 `archive_id`。

## 字段映射

| CSV 列 | 目标列 | 处理规则 |
| --- | --- | --- |
| `bah` | `bah` | 只去除首尾空格，不补零 |
| `cid` | `cid` | 设备 ID |
| `openerno` | `openerno` | 操作人编号 |
| `date` | `date` | 转为 `YYYY-MM-DD` 文本 |
| `type` | `type` | 空值写为 `未扫描` |
| `pages` | `pages` | 空值允许为 `NULL`，非空时必须为非负整数 |
| `sjh` | `sjh` | 上架号；空字符串转 `NULL`，非空值应一对一 |
| `patientname` | `patientname` | 病人姓名 |
| `inpatientdepartment` | `inpatientdepartment` | 住院科室 |
| `patientid` | `patientid` | 病人 ID |
| `dischargedate` | `dischargedate` | 转为 `YYYY-MM-DD` 文本 |
| `brxh` | 不导入 | 当前 `mr_statistics` 没有该列；不要因多一列造成错位 |

## 导入顺序

推荐在 `mr_scan` 和装箱数据之前导入本表：

1. 导入 `mr_patient`。
2. 导入 `mr_statistics`，建立 `mr_archive` 主档。
3. 导入 `mr_archive_box_record`。
4. 最后导入数千万行的 `mr_scan`。

## 导入前检查

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'mr_statistics'
ORDER BY ordinal_position;

SELECT tgname, tgenabled
FROM pg_trigger
WHERE tgrelid = 'app.mr_statistics'::regclass
  AND NOT tgisinternal;
```

应存在 `trg_mr_statistics_sync_archive`。如果触发器缺失，应先修复 Flyway 迁移，不能继续生产导入。

检查目标表是否已有重复上架号：

```sql
SELECT sjh, count(*)
FROM app.mr_statistics
WHERE NULLIF(BTRIM(sjh), '') IS NOT NULL
GROUP BY sjh
HAVING count(*) > 1;
```

若已有重复结果，先人工确认，避免本次更新同时改动多行。

## 完整 `psql` 脚本

保存为 `import-mr-statistics.sql`：

```sql
\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';

CREATE TEMP TABLE import_mr_statistics_raw (
    bah                   text,
    cid                   text,
    openerno              text,
    date_text             text,
    type_text             text,
    pages_text            text,
    sjh                   text,
    patientname           text,
    inpatientdepartment   text,
    patientid             text,
    dischargedate_text    text,
    brxh                  text
);

\copy import_mr_statistics_raw FROM 'D:/MRR-Data/mr_statistics.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

CREATE TEMP TABLE import_mr_statistics_normalized AS
SELECT
    NULLIF(BTRIM(bah), '') AS bah,
    NULLIF(BTRIM(cid), '') AS cid,
    NULLIF(BTRIM(openerno), '') AS openerno,
    CASE
        WHEN NULLIF(BTRIM(date_text), '') IS NULL THEN NULL
        ELSE TO_CHAR(app.try_parse_date(BTRIM(date_text)), 'YYYY-MM-DD')
    END AS archive_date,
    COALESCE(NULLIF(BTRIM(type_text), ''), '未扫描') AS archive_type,
    CASE
        WHEN NULLIF(BTRIM(pages_text), '') IS NULL THEN NULL
        ELSE BTRIM(pages_text)::integer
    END AS pages,
    NULLIF(BTRIM(sjh), '') AS sjh,
    NULLIF(BTRIM(patientname), '') AS patientname,
    NULLIF(BTRIM(inpatientdepartment), '') AS inpatientdepartment,
    NULLIF(BTRIM(patientid), '') AS patientid,
    CASE
        WHEN NULLIF(BTRIM(dischargedate_text), '') IS NULL THEN NULL
        ELSE TO_CHAR(app.try_parse_date(BTRIM(dischargedate_text)), 'YYYY-MM-DD')
    END AS dischargedate
FROM import_mr_statistics_raw;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_raw
        WHERE NULLIF(BTRIM(pages_text), '') IS NOT NULL
          AND BTRIM(pages_text) !~ '^\d+$'
    ) THEN
        RAISE EXCEPTION 'pages 存在非负整数以外的值';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_raw
        WHERE NULLIF(BTRIM(date_text), '') IS NOT NULL
          AND app.try_parse_date(BTRIM(date_text)) IS NULL
    ) THEN
        RAISE EXCEPTION 'date 存在无法解析的日期';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_raw
        WHERE NULLIF(BTRIM(dischargedate_text), '') IS NOT NULL
          AND app.try_parse_date(BTRIM(dischargedate_text)) IS NULL
    ) THEN
        RAISE EXCEPTION 'dischargedate 存在无法解析的日期';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_normalized
        WHERE bah IS NULL AND sjh IS NULL
    ) THEN
        RAISE EXCEPTION '存在 bah 与 sjh 同时为空的记录';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_normalized
        WHERE sjh IS NULL
          AND bah ~ '^\d+$'
          AND bah::numeric >= 10000000
    ) THEN
        RAISE EXCEPTION '病案号达到 10000000 时必须同时提供 sjh';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_statistics_normalized
        WHERE sjh IS NOT NULL
        GROUP BY sjh
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'CSV 内存在重复的非空 sjh';
    END IF;
END
$$;

BEGIN;

-- 非空 sjh 视为同一条统计主记录，重复执行时更新而不是新增。
UPDATE app.mr_statistics t
SET
    bah = s.bah,
    cid = s.cid,
    openerno = s.openerno,
    date = s.archive_date,
    type = s.archive_type,
    pages = s.pages,
    patientname = s.patientname,
    inpatientdepartment = s.inpatientdepartment,
    patientid = s.patientid,
    dischargedate = s.dischargedate
FROM import_mr_statistics_normalized s
WHERE s.sjh IS NOT NULL
  AND t.sjh = s.sjh;

INSERT INTO app.mr_statistics (
    bah,
    cid,
    openerno,
    date,
    type,
    pages,
    sjh,
    patientname,
    inpatientdepartment,
    patientid,
    dischargedate
)
SELECT
    s.bah,
    s.cid,
    s.openerno,
    s.archive_date,
    s.archive_type,
    s.pages,
    s.sjh,
    s.patientname,
    s.inpatientdepartment,
    s.patientid,
    s.dischargedate
FROM import_mr_statistics_normalized s
WHERE
    (
        s.sjh IS NOT NULL
        AND NOT EXISTS (
            SELECT 1
            FROM app.mr_statistics t
            WHERE t.sjh = s.sjh
        )
    )
    OR
    (
        s.sjh IS NULL
        AND NOT EXISTS (
            SELECT 1
            FROM app.mr_statistics t
            WHERE t.sjh IS NULL
              AND t.bah IS NOT DISTINCT FROM s.bah
              AND t.cid IS NOT DISTINCT FROM s.cid
              AND t.openerno IS NOT DISTINCT FROM s.openerno
              AND t.date IS NOT DISTINCT FROM s.archive_date
              AND t.type IS NOT DISTINCT FROM s.archive_type
              AND t.pages IS NOT DISTINCT FROM s.pages
              AND t.patientid IS NOT DISTINCT FROM s.patientid
        )
    );

COMMIT;

ANALYZE app.mr_statistics;
ANALYZE app.mr_archive;

SELECT
    (SELECT count(*) FROM import_mr_statistics_raw) AS csv_rows,
    (SELECT count(*) FROM app.mr_statistics) AS target_total_rows,
    (SELECT count(*) FROM app.mr_statistics WHERE archive_id IS NULL) AS unlinked_rows;
```

执行：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -v ON_ERROR_STOP=1 `
  -f D:/MRR-Scripts/import-mr-statistics.sql
```

## 导入后校验

### 数量与关联覆盖率

```sql
SELECT * FROM app.v_archive_link_quality;

SELECT count(*) AS statistics_rows
FROM app.mr_statistics;

SELECT count(*) AS archive_rows
FROM app.mr_archive;
```

### 检查上架号和高位病案号

```sql
SELECT sjh, count(*)
FROM app.mr_statistics
WHERE sjh IS NOT NULL
GROUP BY sjh
HAVING count(*) > 1;

SELECT id, bah, sjh
FROM app.mr_statistics
WHERE sjh IS NULL
  AND bah ~ '^\d+$'
  AND bah::numeric >= 10000000
ORDER BY id
LIMIT 100;
```

### 类型和页数分布

```sql
SELECT type, count(*), sum(COALESCE(pages, 0)) AS total_pages
FROM app.mr_statistics
GROUP BY type
ORDER BY type;

SELECT count(*) AS invalid_pages
FROM app.mr_statistics
WHERE pages < 0;
```

`mr_statistics.type` 是业务文本；图片分类的 `0`～`15` 数值范围属于 `mr_scan.btype`，不要混用两套类型字段。
