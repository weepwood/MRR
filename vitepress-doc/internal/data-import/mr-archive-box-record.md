# `mr_archive_box_record` 装箱数据导入

本文对应装箱 CSV：

```csv
bah,sjh,box_no,expected_box_no,status,remark
```

装箱记录通过 `archive_id` 关联 `mr_archive`。应先导入 `mr_statistics`，让病案主档建立完成，再导入本表。

## 状态取值

`status` 只允许以下值：

| 值 | 含义 |
| --- | --- |
| `NORMAL` | 位置正常 |
| `MISSING` | 病案缺失；此状态允许 `box_no` 为空 |
| `MISPLACED` | 实际箱号与预期不一致 |
| `CONFLICT` | 数据或位置冲突 |
| `OTHER` | 其他异常 |

空状态按 `NORMAL` 处理。除 `MISSING` 外，`box_no` 必须非空。

## 关联规则

1. 优先使用非空 `sjh` 定位病案。
2. `sjh` 非空但主档不存在时，当前数据库函数可以创建最小 `mr_archive` 记录。
3. `sjh` 为空时，只允许唯一且低于 `10000000` 的 `bah` 自动关联。
4. 高位病案号缺少 `sjh` 时必须进入异常记录，不能猜测 `archive_id`。
5. 同一 `archive_id` 在一份 CSV 中只能出现一次。

## 导入前检查

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'mr_archive_box_record'
ORDER BY ordinal_position;

SELECT count(*) AS unlinked_existing_rows
FROM app.mr_archive_box_record
WHERE archive_id IS NULL;
```

如果已有未关联装箱记录，先执行仓库提供的病案关联回填并核对结果。

## 完整 `psql` 脚本

保存为 `import-mr-archive-box-record.sql`：

```sql
\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';

CREATE TEMP TABLE import_archive_box_raw (
    bah             text,
    sjh             text,
    box_no          text,
    expected_box_no text,
    status_text     text,
    remark          text
);

\copy import_archive_box_raw FROM 'D:/MRR-Data/mr_archive_box_record.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

CREATE TEMP TABLE import_archive_box_normalized AS
SELECT
    NULLIF(BTRIM(bah), '') AS bah,
    NULLIF(BTRIM(sjh), '') AS sjh,
    NULLIF(BTRIM(box_no), '') AS box_no,
    NULLIF(BTRIM(expected_box_no), '') AS expected_box_no,
    COALESCE(NULLIF(UPPER(BTRIM(status_text)), ''), 'NORMAL') AS status,
    NULLIF(BTRIM(remark), '') AS remark
FROM import_archive_box_raw;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM import_archive_box_normalized
        WHERE bah IS NULL AND sjh IS NULL
    ) THEN
        RAISE EXCEPTION '存在 bah 与 sjh 同时为空的装箱记录';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_archive_box_normalized
        WHERE status NOT IN ('NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER')
    ) THEN
        RAISE EXCEPTION 'status 存在不支持的值';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_archive_box_normalized
        WHERE status <> 'MISSING'
          AND box_no IS NULL
    ) THEN
        RAISE EXCEPTION '除 MISSING 外，box_no 不能为空';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_archive_box_normalized
        WHERE sjh IS NULL
          AND bah ~ '^\d+$'
          AND bah::numeric >= 10000000
    ) THEN
        RAISE EXCEPTION '病案号达到 10000000 时必须同时提供 sjh';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_archive_box_normalized
        WHERE sjh IS NOT NULL
        GROUP BY sjh
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'CSV 内存在重复的非空 sjh';
    END IF;
END
$$;

BEGIN;

CREATE TEMP TABLE import_archive_box_resolved AS
SELECT
    n.*,
    app.resolve_archive_id(n.bah, n.sjh, n.sjh IS NOT NULL) AS archive_id
FROM import_archive_box_normalized n;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM import_archive_box_resolved
        WHERE archive_id IS NULL
    ) THEN
        RAISE EXCEPTION '存在无法唯一关联 mr_archive 的装箱记录';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_archive_box_resolved
        GROUP BY archive_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'CSV 内多行解析到同一个 archive_id';
    END IF;
END
$$;

-- 兼容旧记录：先按 sjh 补齐 archive_id。
UPDATE app.mr_archive_box_record t
SET
    archive_id = s.archive_id,
    bah = s.bah,
    box_no = s.box_no,
    expected_box_no = s.expected_box_no,
    status = s.status,
    remark = s.remark,
    updated_at = CURRENT_TIMESTAMP
FROM import_archive_box_resolved s
WHERE s.sjh IS NOT NULL
  AND t.sjh = s.sjh;

-- 已有关联的记录按稳定主键更新。
UPDATE app.mr_archive_box_record t
SET
    bah = s.bah,
    sjh = s.sjh,
    box_no = s.box_no,
    expected_box_no = s.expected_box_no,
    status = s.status,
    remark = s.remark,
    updated_at = CURRENT_TIMESTAMP
FROM import_archive_box_resolved s
WHERE t.archive_id = s.archive_id;

-- 只插入尚不存在的病案装箱记录。
INSERT INTO app.mr_archive_box_record (
    archive_id,
    bah,
    sjh,
    box_no,
    expected_box_no,
    status,
    remark
)
SELECT
    s.archive_id,
    s.bah,
    s.sjh,
    s.box_no,
    s.expected_box_no,
    s.status,
    s.remark
FROM import_archive_box_resolved s
WHERE NOT EXISTS (
    SELECT 1
    FROM app.mr_archive_box_record t
    WHERE t.archive_id = s.archive_id
);

COMMIT;

ANALYZE app.mr_archive_box_record;

SELECT
    (SELECT count(*) FROM import_archive_box_raw) AS csv_rows,
    (SELECT count(*) FROM app.mr_archive_box_record) AS target_total_rows,
    (SELECT count(*) FROM app.mr_archive_box_record WHERE archive_id IS NULL) AS unlinked_rows;
```

执行：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -v ON_ERROR_STOP=1 `
  -f D:/MRR-Scripts/import-mr-archive-box-record.sql
```

## `No such file or directory` 的处理

出现：

```text
psql:import-mr-archive-box-record.sql:...: No such file or directory
```

按以下顺序检查：

1. `\copy` 中的路径是执行 `psql` 的那台 Windows 主机上的路径。
2. 使用正斜杠，例如 `D:/MRR-Data/mr_archive_box_record.csv`。
3. 文件名、扩展名和目录拼写完全一致。
4. SQL 文件保存为 UTF-8，`\copy` 独占一行。
5. 在 PowerShell 中先执行 `Test-Path 'D:/MRR-Data/mr_archive_box_record.csv'`。

`COPY` 与 `\copy` 不同：`COPY` 由 PostgreSQL 服务进程读取服务器路径；`\copy` 由本机 `psql` 读取客户端路径。

## 导入后校验

```sql
SELECT status, count(*)
FROM app.mr_archive_box_record
GROUP BY status
ORDER BY status;

SELECT box_no, count(*)
FROM app.mr_archive_box_record
WHERE status <> 'MISSING'
GROUP BY box_no
ORDER BY box_no;

SELECT id, bah, sjh, box_no, expected_box_no, status, remark
FROM app.mr_archive_box_record
WHERE status <> 'NORMAL'
ORDER BY updated_at DESC
LIMIT 200;

SELECT archive_id, count(*)
FROM app.mr_archive_box_record
WHERE archive_id IS NOT NULL
GROUP BY archive_id
HAVING count(*) > 1;
```

最后一条查询正常应返回零行；若存在重复，先人工确认后再合并，不能只按 `bah` 批量删除。
