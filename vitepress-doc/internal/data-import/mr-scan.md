# `mr_scan` 扫描影像数据导入

`mr_scan` 可能达到数千万行，是所有导入任务中风险最高的一项。不要使用单个 3000 万行 CSV 直接导入，也不要通过前端逐行提交。

本文覆盖两种来源：

1. 从旧 `img.public.mr_scan` 导出的完整数据，保留原 `id`，适合数据库迁移。
2. 只有 `sjh,bah,brxh,folder,filename,btype,filesize` 的简化 CSV，适合初始小规模数据。

## 当前目标字段

```text
id, brxh, bah, sjh, filename, btype, pages, openerno,
uploaddate, uploadflag, folder, oss_url, file_size,
checksum_md5, migration_status, migrated_at, archive_id
```

注意：

- 旧表的 `filesize` 对应新表的 `file_size`，单位必须是字节。
- `id` 是 `INTEGER`，最大值为 `2147483647`。
- `archive_id` 由导入脚本根据 `mr_archive` 计算，不来自 CSV。
- `bah`、`sjh` 和 `brxh` 只去除首尾空格，不补零；它们可能参与真实图片目录定位。
- `btype` 允许 `0`～`15`，其中 `0` 表示暂未分类。

## 推荐迁移顺序

1. 完成 Flyway 迁移。
2. 导入 `mr_patient`。
3. 导入 `mr_statistics`，建立 `mr_archive`。
4. 导入 `mr_archive_box_record`。
5. 最后分卷导入 `mr_scan`。
6. 全部导入后重置 identity 序列、执行 `ANALYZE` 和关联校验。

## 从旧数据库分卷导出

旧表常见结构：

```text
id,brxh,bah,filename,btype,pages,openerno,uploaddate,uploadflag,
folder,filesize,sjh,oss_url,checksum_md5,migration_status,migrated_at
```

先确认 ID 范围：

```sql
SELECT min(id), max(id), count(*)
FROM public.mr_scan;
```

建议先按每卷 50 万～100 万行压测。以下命令在旧 `img` 数据库的 `psql` 中执行；`\copy` 必须写成一行：

```sql
\copy (SELECT id,brxh,bah,filename,btype,pages,openerno,uploaddate,uploadflag,folder,filesize,sjh,oss_url,checksum_md5,migration_status,migrated_at FROM public.mr_scan WHERE id >= 1 AND id < 1000001 ORDER BY id) TO 'D:/MRR-Export/mr_scan_000000001_001000000.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
```

下一卷改为：

```sql
\copy (SELECT id,brxh,bah,filename,btype,pages,openerno,uploaddate,uploadflag,folder,filesize,sjh,oss_url,checksum_md5,migration_status,migrated_at FROM public.mr_scan WHERE id >= 1000001 AND id < 2000001 ORDER BY id) TO 'D:/MRR-Export/mr_scan_001000001_002000000.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
```

每个分卷至少记录：文件名、ID 起止、预期行数、文件大小、SHA-256、导入状态和失败原因。

PowerShell 计算校验和：

```powershell
Get-FileHash D:/MRR-Export/mr_scan_000000001_001000000.csv -Algorithm SHA256
```

## 完整分卷导入脚本

将下列内容保存为 `import-mr-scan-part.sql`。每次只修改 `\copy` 的分卷路径，然后执行一次。

```sql
\set ON_ERROR_STOP on
SET client_encoding TO 'UTF8';

CREATE TEMP TABLE import_mr_scan_raw (
    source_id            text,
    brxh                 text,
    bah                  text,
    filename             text,
    btype_text           text,
    pages_text           text,
    openerno             text,
    uploaddate           text,
    uploadflag_text      text,
    folder               text,
    filesize_text        text,
    sjh                  text,
    oss_url              text,
    checksum_md5         text,
    migration_status     text,
    migrated_at_text     text
);

\copy import_mr_scan_raw FROM 'D:/MRR-Export/mr_scan_000000001_001000000.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(source_id), '') IS NULL
           OR BTRIM(source_id) !~ '^\d+$'
           OR BTRIM(source_id)::numeric > 2147483647
           OR BTRIM(source_id)::numeric < 1
    ) THEN
        RAISE EXCEPTION 'id 必须是 1 到 2147483647 的整数';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        GROUP BY BTRIM(source_id)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION '当前 CSV 分卷内存在重复 id';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(btype_text), '') IS NOT NULL
          AND (
              BTRIM(btype_text) !~ '^\d+$'
              OR BTRIM(btype_text)::integer NOT BETWEEN 0 AND 15
          )
    ) THEN
        RAISE EXCEPTION 'btype 必须为空或位于 0 到 15';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(pages_text), '') IS NOT NULL
          AND BTRIM(pages_text) !~ '^\d+$'
    ) THEN
        RAISE EXCEPTION 'pages 必须为空或非负整数';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(uploadflag_text), '') IS NOT NULL
          AND BTRIM(uploadflag_text) !~ '^-?\d+$'
    ) THEN
        RAISE EXCEPTION 'uploadflag 必须为空或整数';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(filesize_text), '') IS NOT NULL
          AND BTRIM(filesize_text) !~ '^\d+$'
    ) THEN
        RAISE EXCEPTION 'filesize 必须为空或纯数字字节数，不能包含 KB/MB';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(checksum_md5), '') IS NOT NULL
          AND BTRIM(checksum_md5) !~ '^[0-9A-Fa-f]{32}$'
    ) THEN
        RAISE EXCEPTION 'checksum_md5 必须为空或 32 位十六进制';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE COALESCE(NULLIF(BTRIM(migration_status), ''), 'not_migrated')
              NOT IN ('not_migrated', 'migrated', 'verified')
    ) THEN
        RAISE EXCEPTION 'migration_status 存在不支持的值';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(filename), '') IS NULL
           OR NULLIF(BTRIM(folder), '') IS NULL
    ) THEN
        RAISE EXCEPTION 'filename 和 folder 不能为空';
    END IF;

    IF EXISTS (
        SELECT 1 FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(bah), '') IS NULL
          AND NULLIF(BTRIM(sjh), '') IS NULL
    ) THEN
        RAISE EXCEPTION 'bah 与 sjh 不能同时为空';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM import_mr_scan_raw
        WHERE NULLIF(BTRIM(sjh), '') IS NOT NULL
        GROUP BY BTRIM(sjh)
        HAVING count(DISTINCT NULLIF(BTRIM(bah), '')) > 1
    ) THEN
        RAISE EXCEPTION '同一 sjh 在当前分卷中对应多个 bah';
    END IF;
END
$$;

CREATE TEMP TABLE import_mr_scan_normalized AS
SELECT
    BTRIM(source_id)::integer AS id,
    NULLIF(BTRIM(brxh), '') AS brxh,
    NULLIF(BTRIM(bah), '') AS bah,
    NULLIF(BTRIM(sjh), '') AS sjh,
    NULLIF(BTRIM(filename), '') AS filename,
    CASE WHEN NULLIF(BTRIM(btype_text), '') IS NULL THEN 0 ELSE BTRIM(btype_text)::integer END AS btype,
    CASE WHEN NULLIF(BTRIM(pages_text), '') IS NULL THEN NULL ELSE BTRIM(pages_text)::integer END AS pages,
    NULLIF(BTRIM(openerno), '') AS openerno,
    NULLIF(BTRIM(uploaddate), '') AS uploaddate,
    CASE WHEN NULLIF(BTRIM(uploadflag_text), '') IS NULL THEN 1 ELSE BTRIM(uploadflag_text)::integer END AS uploadflag,
    NULLIF(BTRIM(folder), '') AS folder,
    NULLIF(BTRIM(oss_url), '') AS oss_url,
    CASE WHEN NULLIF(BTRIM(filesize_text), '') IS NULL THEN NULL ELSE BTRIM(filesize_text)::bigint END AS file_size,
    LOWER(NULLIF(BTRIM(checksum_md5), '')) AS checksum_md5,
    COALESCE(NULLIF(BTRIM(migration_status), ''), 'not_migrated') AS migration_status,
    CASE WHEN NULLIF(BTRIM(migrated_at_text), '') IS NULL THEN NULL ELSE BTRIM(migrated_at_text)::timestamp END AS migrated_at
FROM import_mr_scan_raw;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM import_mr_scan_normalized s
        JOIN app.mr_scan t ON t.id = s.id
        WHERE t.brxh IS DISTINCT FROM s.brxh
           OR t.bah IS DISTINCT FROM s.bah
           OR t.sjh IS DISTINCT FROM s.sjh
           OR t.filename IS DISTINCT FROM s.filename
           OR t.folder IS DISTINCT FROM s.folder
    ) THEN
        RAISE EXCEPTION '目标表已存在相同 id 但内容不同的记录';
    END IF;
END
$$;

BEGIN;

-- 先为分卷内的新 sjh 建立最小病案主档，避免对每张图片逐行创建。
INSERT INTO app.mr_archive AS target (sjh, bah)
SELECT
    s.sjh,
    MAX(s.bah) AS bah
FROM import_mr_scan_normalized s
WHERE s.sjh IS NOT NULL
GROUP BY s.sjh
ON CONFLICT (sjh) WHERE sjh IS NOT NULL DO UPDATE
SET
    bah = COALESCE(target.bah, EXCLUDED.bah),
    updated_at = CURRENT_TIMESTAMP;

CREATE TEMP TABLE import_unique_archive_bah AS
SELECT
    bah,
    MIN(id) AS archive_id
FROM app.mr_archive
WHERE bah IS NOT NULL
  AND NOT (bah ~ '^\d+$' AND bah::numeric >= 10000000)
GROUP BY bah
HAVING count(*) = 1;

CREATE TEMP TABLE import_mr_scan_resolved AS
SELECT
    s.*,
    COALESCE(by_sjh.id, by_bah.archive_id) AS archive_id
FROM import_mr_scan_normalized s
LEFT JOIN app.mr_archive by_sjh
       ON s.sjh IS NOT NULL
      AND by_sjh.sjh = s.sjh
LEFT JOIN import_unique_archive_bah by_bah
       ON s.sjh IS NULL
      AND by_bah.bah = s.bah;

INSERT INTO app.mr_scan (
    id,
    brxh,
    bah,
    sjh,
    filename,
    btype,
    pages,
    openerno,
    uploaddate,
    uploadflag,
    folder,
    oss_url,
    file_size,
    checksum_md5,
    migration_status,
    migrated_at,
    archive_id
)
SELECT
    id,
    brxh,
    bah,
    sjh,
    filename,
    btype,
    pages,
    openerno,
    uploaddate,
    uploadflag,
    folder,
    oss_url,
    file_size,
    checksum_md5,
    migration_status,
    migrated_at,
    archive_id
FROM import_mr_scan_resolved
ON CONFLICT (id) DO NOTHING;

COMMIT;

SELECT
    (SELECT count(*) FROM import_mr_scan_raw) AS csv_rows,
    (SELECT count(*) FROM import_mr_scan_resolved WHERE archive_id IS NULL) AS unresolved_in_part,
    (SELECT min(id) FROM import_mr_scan_normalized) AS part_min_id,
    (SELECT max(id) FROM import_mr_scan_normalized) AS part_max_id;
```

执行：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -v ON_ERROR_STOP=1 `
  -f D:/MRR-Scripts/import-mr-scan-part.sql `
  1>> D:/MRR-Logs/mr-scan-import.log `
  2>>&1
```

脚本可重复执行同一分卷：相同 `id` 且核心字段一致时跳过；相同 `id` 但内容不一致时中止。

## 全部分卷完成后的处理

显式导入原 `id` 后，必须把 identity 序列推进到当前最大值：

```sql
SELECT setval(
    pg_get_serial_sequence('app.mr_scan', 'id'),
    COALESCE(MAX(id), 1),
    MAX(id) IS NOT NULL
)
FROM app.mr_scan;

ANALYZE app.mr_scan;
ANALYZE app.mr_archive;
```

执行关联回填：

```powershell
./backend-repo/scripts/backfill-archive-links.ps1 -BatchSize 10000
```

校验：

```sql
SELECT count(*) AS total_rows FROM app.mr_scan;

SELECT count(*) AS unlinked_rows
FROM app.mr_scan
WHERE archive_id IS NULL;

SELECT btype, count(*)
FROM app.mr_scan
GROUP BY btype
ORDER BY btype;

SELECT count(*) AS missing_file_location
FROM app.mr_scan
WHERE NULLIF(BTRIM(folder), '') IS NULL
   OR NULLIF(BTRIM(filename), '') IS NULL;

SELECT id, brxh, bah, sjh, folder, filename, archive_id
FROM app.mr_scan
ORDER BY id
LIMIT 100;
```

还应抽样选择短编号、原有前导零编号、高位病案号和空上架号，验证数据库值与实际图片目录完全一致。

## 简化 CSV 导入

简化表头：

```csv
sjh,bah,brxh,folder,filename,btype,filesize
```

该格式没有稳定源 `id`，不适合可恢复的 3000 万行迁移。只建议用于空表初始化或小批补录。

```sql
\set ON_ERROR_STOP on

CREATE TEMP TABLE import_mr_scan_minimal (
    sjh text,
    bah text,
    brxh text,
    folder text,
    filename text,
    btype_text text,
    filesize_text text
);

\copy import_mr_scan_minimal FROM 'D:/MRR-Data/mr_scan.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM app.mr_scan LIMIT 1) THEN
        RAISE EXCEPTION '简化格式没有源 id，只允许在确认空表时使用';
    END IF;
END
$$;

INSERT INTO app.mr_scan (
    sjh, bah, brxh, folder, filename, btype, file_size,
    uploadflag, migration_status
)
SELECT
    NULLIF(BTRIM(sjh), ''),
    NULLIF(BTRIM(bah), ''),
    NULLIF(BTRIM(brxh), ''),
    NULLIF(BTRIM(folder), ''),
    NULLIF(BTRIM(filename), ''),
    COALESCE(NULLIF(BTRIM(btype_text), '')::integer, 0),
    NULLIF(BTRIM(filesize_text), '')::bigint,
    1,
    'not_migrated'
FROM import_mr_scan_minimal;
```

正式大数据迁移应优先保留旧表 `id`，这样才能可靠分卷、跳过已完成批次并定位冲突。

## 导入卡住但没有报错

查询活动：

```sql
SELECT pid, state, wait_event_type, wait_event, query_start, left(query, 200)
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY query_start;
```

当 `wait_event_type = 'Client'` 时，PostgreSQL 通常正在等待客户端继续发送或接收数据，不表示服务端 SQL 必然死锁。应同时检查：

- `psql.exe` 是否仍在运行。
- CSV 所在磁盘是否有读取活动或错误。
- 客户端与数据库之间网络是否中断。
- 日志文件是否继续增长。
- CSV 是否存在超长行、异常引号或嵌入换行。
- 目标磁盘、WAL 磁盘是否已满。

不要仅因为界面长时间无输出就强制结束数据库进程；先记录当前分卷、进程状态和最后成功 ID。
