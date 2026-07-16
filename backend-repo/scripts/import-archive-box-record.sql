\set ON_ERROR_STOP on
\pset pager off

-- 用法（Windows PowerShell）：
-- $env:PGPASSWORD='数据库密码'
-- psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
--   -v csv_file='D:/MRR-Data/mr_archive_box_record.csv' `
--   -f backend-repo/scripts/import-archive-box-record.sql
--
-- CSV 必须使用 UTF-8、逗号分隔，表头为：
-- bah,sjh,box_no,expected_box_no,status,remark

\if :{?csv_file}
\else
    \echo '错误：缺少 csv_file 参数。示例：-v csv_file=''D:/data/mr_archive_box_record.csv'''
    \quit 2
\endif

SET client_encoding TO 'UTF8';
BEGIN;

DO $$
BEGIN
    IF to_regclass('app.mr_archive_box_record') IS NULL THEN
        RAISE EXCEPTION '目标表 app.mr_archive_box_record 不存在，请先完成 Flyway 数据库迁移';
    END IF;

    IF to_regprocedure('app.normalize_medical_record_code(text)') IS NULL
       OR to_regprocedure('app.resolve_archive_id(text,text,boolean)') IS NULL THEN
        RAISE EXCEPTION '病案主数据函数不存在，请先完成病案主数据重构迁移';
    END IF;
END
$$;

CREATE TEMP TABLE tmp_archive_box_record_raw (
    source_row      BIGINT GENERATED ALWAYS AS IDENTITY,
    bah             TEXT,
    sjh             TEXT,
    box_no          TEXT,
    expected_box_no TEXT,
    status          TEXT,
    remark          TEXT
) ON COMMIT DROP;

\echo '正在读取 CSV：' :csv_file
\copy tmp_archive_box_record_raw (bah, sjh, box_no, expected_box_no, status, remark) FROM :'csv_file' WITH (FORMAT csv, HEADER true, DELIMITER ',', QUOTE '"', ESCAPE '"', ENCODING 'UTF8', NULL '')

CREATE TEMP TABLE tmp_archive_box_record_normalized ON COMMIT DROP AS
SELECT
    source_row + 1 AS csv_line,
    app.normalize_medical_record_code(bah) AS bah,
    app.normalize_medical_record_code(sjh) AS sjh,
    NULLIF(BTRIM(box_no), '') AS box_no,
    NULLIF(BTRIM(expected_box_no), '') AS expected_box_no,
    COALESCE(
        NULLIF(UPPER(BTRIM(status)), ''),
        CASE WHEN NULLIF(BTRIM(box_no), '') IS NULL THEN 'MISSING' ELSE 'NORMAL' END
    ) AS status,
    NULLIF(BTRIM(remark), '') AS remark
FROM tmp_archive_box_record_raw;

ALTER TABLE tmp_archive_box_record_normalized
    ADD COLUMN resolved_archive_id BIGINT;

UPDATE tmp_archive_box_record_normalized
SET resolved_archive_id = app.resolve_archive_id(bah, sjh, FALSE);

CREATE TEMP TABLE tmp_archive_box_bah_match ON COMMIT DROP AS
SELECT
    source.csv_line,
    source.bah,
    source.sjh,
    source.box_no,
    source.expected_box_no,
    source.status,
    source.remark,
    source.resolved_archive_id,
    matching.match_count,
    matching.match_id
FROM tmp_archive_box_record_normalized source
CROSS JOIN LATERAL (
    SELECT
        COUNT(*)::INTEGER AS match_count,
        MIN(target.id) AS match_id
    FROM app.mr_archive_box_record target
    WHERE source.sjh IS NULL
      AND (
          (source.resolved_archive_id IS NOT NULL AND target.archive_id = source.resolved_archive_id)
          OR (
              source.resolved_archive_id IS NULL
              AND target.sjh IS NULL
              AND target.bah = source.bah
          )
      )
) matching
WHERE source.sjh IS NULL;

CREATE TEMP TABLE tmp_archive_box_record_errors ON COMMIT DROP AS
WITH duplicate_counts AS (
    SELECT
        source.*,
        CASE
            WHEN source.sjh IS NOT NULL
            THEN COUNT(*) OVER (PARTITION BY source.sjh)
            ELSE 0
        END AS duplicate_sjh_count,
        CASE
            WHEN source.sjh IS NULL
            THEN COUNT(*) OVER (PARTITION BY source.bah)
            ELSE 0
        END AS duplicate_bah_only_count
    FROM tmp_archive_box_record_normalized source
)
SELECT
    source.csv_line,
    CONCAT_WS('；',
        CASE WHEN source.bah IS NULL AND source.sjh IS NULL
            THEN 'bah 和 sjh 不能同时为空' END,
        CASE WHEN source.status NOT IN ('NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER')
            THEN 'status 非法，只允许 NORMAL/MISSING/MISPLACED/CONFLICT/OTHER' END,
        CASE WHEN source.status <> 'MISSING' AND source.box_no IS NULL
            THEN 'status 不是 MISSING 时 box_no 不能为空' END,
        CASE WHEN LENGTH(source.bah) > 64 THEN 'bah 超过 64 个字符' END,
        CASE WHEN LENGTH(source.sjh) > 64 THEN 'sjh 超过 64 个字符' END,
        CASE WHEN LENGTH(source.box_no) > 64 THEN 'box_no 超过 64 个字符' END,
        CASE WHEN LENGTH(source.expected_box_no) > 64 THEN 'expected_box_no 超过 64 个字符' END,
        CASE WHEN LENGTH(source.remark) > 1000 THEN 'remark 超过 1000 个字符' END,
        CASE WHEN source.duplicate_sjh_count > 1
            THEN 'CSV 中存在规范化后重复的 sjh' END,
        CASE WHEN source.duplicate_bah_only_count > 1
            THEN 'CSV 中缺少 sjh 的相同 bah 出现多次' END,
        CASE WHEN COALESCE(match.match_count, 0) > 1
            THEN '缺少 sjh，且数据库中无法按病案唯一定位装箱记录' END
    ) AS error_message,
    source.bah,
    source.sjh,
    source.box_no,
    source.expected_box_no,
    source.status,
    source.remark
FROM duplicate_counts source
LEFT JOIN tmp_archive_box_bah_match match ON match.csv_line = source.csv_line
WHERE
    (source.bah IS NULL AND source.sjh IS NULL)
    OR source.status NOT IN ('NORMAL', 'MISSING', 'MISPLACED', 'CONFLICT', 'OTHER')
    OR (source.status <> 'MISSING' AND source.box_no IS NULL)
    OR LENGTH(source.bah) > 64
    OR LENGTH(source.sjh) > 64
    OR LENGTH(source.box_no) > 64
    OR LENGTH(source.expected_box_no) > 64
    OR LENGTH(source.remark) > 1000
    OR source.duplicate_sjh_count > 1
    OR source.duplicate_bah_only_count > 1
    OR COALESCE(match.match_count, 0) > 1;

SELECT EXISTS (
    SELECT 1 FROM tmp_archive_box_record_errors
) AS has_validation_errors
\gset

\if :has_validation_errors
    \echo '发现错误数据，未执行导入。错误明细如下：'
    SELECT *
    FROM tmp_archive_box_record_errors
    ORDER BY csv_line;
    ROLLBACK;
    \quit 3
\endif

CREATE TEMP TABLE tmp_archive_box_import_stats (
    metric TEXT PRIMARY KEY,
    value  BIGINT NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_archive_box_import_stats
SELECT 'CSV 有效行数', COUNT(*)
FROM tmp_archive_box_record_normalized;

INSERT INTO tmp_archive_box_import_stats
SELECT '按上架号新增', COUNT(*)
FROM tmp_archive_box_record_normalized source
WHERE source.sjh IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM app.mr_archive_box_record target
      WHERE target.sjh = source.sjh
  );

INSERT INTO tmp_archive_box_import_stats
SELECT '按上架号更新', COUNT(*)
FROM tmp_archive_box_record_normalized source
JOIN app.mr_archive_box_record target ON target.sjh = source.sjh
WHERE source.sjh IS NOT NULL;

INSERT INTO tmp_archive_box_import_stats
SELECT '仅病案号新增', COUNT(*)
FROM tmp_archive_box_bah_match
WHERE match_count = 0;

INSERT INTO tmp_archive_box_import_stats
SELECT '仅病案号更新', COUNT(*)
FROM tmp_archive_box_bah_match
WHERE match_count = 1;

INSERT INTO app.mr_archive_box_record AS target
    (bah, sjh, box_no, expected_box_no, status, remark)
SELECT
    source.bah,
    source.sjh,
    source.box_no,
    source.expected_box_no,
    source.status,
    source.remark
FROM tmp_archive_box_record_normalized source
WHERE source.sjh IS NOT NULL
ON CONFLICT (sjh) DO UPDATE
SET
    bah = COALESCE(EXCLUDED.bah, target.bah),
    box_no = EXCLUDED.box_no,
    expected_box_no = EXCLUDED.expected_box_no,
    status = EXCLUDED.status,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;

UPDATE app.mr_archive_box_record target
SET
    box_no = source.box_no,
    expected_box_no = source.expected_box_no,
    status = source.status,
    remark = source.remark,
    updated_at = CURRENT_TIMESTAMP
FROM tmp_archive_box_bah_match source
WHERE source.match_count = 1
  AND target.id = source.match_id;

INSERT INTO app.mr_archive_box_record
    (bah, sjh, box_no, expected_box_no, status, remark)
SELECT
    source.bah,
    NULL,
    source.box_no,
    source.expected_box_no,
    source.status,
    source.remark
FROM tmp_archive_box_bah_match source
WHERE source.match_count = 0;

SELECT metric AS "导入项目", value AS "数量"
FROM tmp_archive_box_import_stats
ORDER BY CASE metric
    WHEN 'CSV 有效行数' THEN 1
    WHEN '按上架号新增' THEN 2
    WHEN '按上架号更新' THEN 3
    WHEN '仅病案号新增' THEN 4
    WHEN '仅病案号更新' THEN 5
    ELSE 99
END;

SELECT
    COUNT(*) AS "装箱记录总数",
    COUNT(*) FILTER (WHERE archive_id IS NULL) AS "未关联病案主表数量"
FROM app.mr_archive_box_record;

COMMIT;
\echo 'mr_archive_box_record.csv 导入完成。'
