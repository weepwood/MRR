package com.zjcxph.imgapi.service.impl;

import java.util.List;

final class DataQualityCheckCatalog {

    private static final String RECENT_SCAN_BOUNDARY =
            "GREATEST((SELECT COALESCE(MAX(id), 0) - 500000 FROM app.mr_scan), 0)";

    private DataQualityCheckCatalog() {
    }

    static List<DataQualityCheckDefinition> standardChecks() {
        return List.of(
                new DataQualityCheckDefinition(
                        "SCAN_CODE_BLANK_RECENT", "最近扫描记录病案号和上架号同时为空", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_scan
                        WHERE id > %s
                          AND NULLIF(BTRIM(COALESCE(bah, '')), '') IS NULL
                          AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                        """.formatted(RECENT_SCAN_BOUNDARY),
                        """
                        SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                               '病案号和上架号同时为空，无法识别所属病案' AS detail
                        FROM app.mr_scan
                        WHERE id > %s
                          AND NULLIF(BTRIM(COALESCE(bah, '')), '') IS NULL
                          AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                        ORDER BY id DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "HIGH_BAH_WITHOUT_SJH", "高位病案号缺少唯一上架号", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_archive
                        WHERE CASE
                                  WHEN BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                                  THEN BTRIM(bah)::numeric
                              END >= 10000000
                          AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                        """,
                        """
                        SELECT 'mr_archive' AS entity_type, id::text AS entity_id, bah, sjh,
                               '数值病案号大于等于 10000000，但缺少用于唯一定位的上架号' AS detail
                        FROM app.mr_archive
                        WHERE CASE
                                  WHEN BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                                  THEN BTRIM(bah)::numeric
                              END >= 10000000
                          AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                        ORDER BY id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "SCAN_PAGES_INVALID_RECENT", "最近扫描记录页码为空或小于等于零", "CRITICAL",
                        "SELECT COUNT(*) FROM app.mr_scan WHERE id > " + RECENT_SCAN_BOUNDARY
                                + " AND (pages IS NULL OR pages <= 0)",
                        """
                        SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                               CONCAT('无效页码: ', COALESCE(pages::text, 'NULL')) AS detail
                        FROM app.mr_scan
                        WHERE id > %s
                          AND (pages IS NULL OR pages <= 0)
                        ORDER BY id DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "SCAN_FILE_METADATA_MISSING_RECENT", "最近扫描文件元数据缺失", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_scan
                        WHERE id > %s
                          AND (
                            NULLIF(BTRIM(COALESCE(filename, '')), '') IS NULL
                            OR NULLIF(BTRIM(COALESCE(folder, '')), '') IS NULL
                          )
                        """.formatted(RECENT_SCAN_BOUNDARY),
                        """
                        SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                               CONCAT('文件名=', COALESCE(filename, 'NULL'), ', 目录=', COALESCE(folder, 'NULL')) AS detail
                        FROM app.mr_scan
                        WHERE id > %s
                          AND (
                            NULLIF(BTRIM(COALESCE(filename, '')), '') IS NULL
                            OR NULLIF(BTRIM(COALESCE(folder, '')), '') IS NULL
                          )
                        ORDER BY id DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED", "扫描记录主档关联缺失（估算）", "WARNING",
                        """
                        WITH estimated AS (
                            SELECT GREATEST(
                                ROUND(GREATEST(c.reltuples, 0) * COALESCE(s.null_frac, 0)),
                                0
                            )::bigint AS issue_count
                            FROM pg_class c
                            JOIN pg_namespace n ON n.oid = c.relnamespace
                            LEFT JOIN pg_stats s
                              ON s.schemaname = n.nspname
                             AND s.tablename = c.relname
                             AND s.attname = 'archive_id'
                            WHERE n.nspname = 'app' AND c.relname = 'mr_scan'
                        ), recent AS (
                            SELECT COUNT(*)::bigint AS issue_count
                            FROM app.mr_scan
                            WHERE id > %s
                              AND uploadflag <> 0
                              AND archive_id IS NULL
                        )
                        SELECT GREATEST(
                            COALESCE((SELECT issue_count FROM estimated), 0),
                            COALESCE((SELECT issue_count FROM recent), 0)
                        )
                        """.formatted(RECENT_SCAN_BOUNDARY),
                        """
                        SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                               '有效扫描记录 archive_id 为空；总量来自统计估算，样本来自最近记录' AS detail
                        FROM app.mr_scan
                        WHERE id > %s
                          AND archive_id IS NULL
                          AND uploadflag <> 0
                        ORDER BY id DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "SCAN_ARCHIVE_LINK_MISMATCH_RECENT", "最近扫描记录与主档编号不一致", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_scan s
                        JOIN app.mr_archive a ON a.id = s.archive_id
                        WHERE s.id > %s
                          AND s.uploadflag <> 0
                          AND (
                            (NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                             AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                             AND app.numeric_code_key(s.sjh) <> app.numeric_code_key(a.sjh))
                            OR
                            (NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                             AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                             AND app.numeric_code_key(s.bah) <> app.numeric_code_key(a.bah))
                          )
                        """.formatted(RECENT_SCAN_BOUNDARY),
                        """
                        SELECT 'mr_scan' AS entity_type, s.id::text AS entity_id, s.bah, s.sjh,
                               CONCAT('archive_id=', s.archive_id, '，扫描编号与 mr_archive 编号不一致') AS detail
                        FROM app.mr_scan s
                        JOIN app.mr_archive a ON a.id = s.archive_id
                        WHERE s.id > %s
                          AND s.uploadflag <> 0
                          AND (
                            (NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                             AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                             AND app.numeric_code_key(s.sjh) <> app.numeric_code_key(a.sjh))
                            OR
                            (NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                             AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                             AND app.numeric_code_key(s.bah) <> app.numeric_code_key(a.bah))
                          )
                        ORDER BY s.id DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "STATISTICS_ARCHIVE_LINK_MISSING", "统计记录主档关联缺失", "WARNING",
                        "SELECT COUNT(*) FROM app.mr_statistics WHERE archive_id IS NULL",
                        """
                        SELECT 'mr_statistics' AS entity_type, id::text AS entity_id, bah, sjh,
                               '统计记录 archive_id 为空' AS detail
                        FROM app.mr_statistics
                        WHERE archive_id IS NULL
                        ORDER BY id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "STATISTICS_ARCHIVE_LINK_MISMATCH", "统计记录与主档编号不一致", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_statistics s
                        JOIN app.mr_archive a ON a.id = s.archive_id
                        WHERE (
                            NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                            AND app.numeric_code_key(s.sjh) <> app.numeric_code_key(a.sjh)
                        ) OR (
                            NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                            AND app.numeric_code_key(s.bah) <> app.numeric_code_key(a.bah)
                        )
                        """,
                        """
                        SELECT 'mr_statistics' AS entity_type, s.id::text AS entity_id, s.bah, s.sjh,
                               CONCAT('archive_id=', s.archive_id, '，统计编号与主档编号不一致') AS detail
                        FROM app.mr_statistics s
                        JOIN app.mr_archive a ON a.id = s.archive_id
                        WHERE (
                            NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                            AND app.numeric_code_key(s.sjh) <> app.numeric_code_key(a.sjh)
                        ) OR (
                            NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                            AND app.numeric_code_key(s.bah) <> app.numeric_code_key(a.bah)
                        )
                        ORDER BY s.id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "STATISTICS_ARCHIVE_WITHOUT_SCAN", "统计主档没有有效扫描记录", "WARNING",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_statistics s
                        WHERE s.archive_id IS NOT NULL
                          AND NOT EXISTS (
                            SELECT 1 FROM app.mr_scan sc
                            WHERE sc.archive_id = s.archive_id AND sc.uploadflag <> 0
                          )
                        """,
                        """
                        SELECT 'mr_statistics' AS entity_type, s.id::text AS entity_id, s.bah, s.sjh,
                               CONCAT('archive_id=', s.archive_id, '，没有已关联的有效扫描记录') AS detail
                        FROM app.mr_statistics s
                        WHERE s.archive_id IS NOT NULL
                          AND NOT EXISTS (
                            SELECT 1 FROM app.mr_scan sc
                            WHERE sc.archive_id = s.archive_id AND sc.uploadflag <> 0
                          )
                        ORDER BY s.id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "BOX_ARCHIVE_LINK_MISSING", "装箱记录主档关联缺失", "WARNING",
                        "SELECT COUNT(*) FROM app.mr_archive_box_record WHERE archive_id IS NULL",
                        """
                        SELECT 'mr_archive_box_record' AS entity_type, id::text AS entity_id, bah, sjh,
                               '装箱记录 archive_id 为空' AS detail
                        FROM app.mr_archive_box_record
                        WHERE archive_id IS NULL
                        ORDER BY updated_at DESC, id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "BOX_ARCHIVE_LINK_MISMATCH", "装箱记录与主档编号不一致", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_archive_box_record b
                        JOIN app.mr_archive a ON a.id = b.archive_id
                        WHERE (
                            NULLIF(BTRIM(COALESCE(b.sjh, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                            AND app.numeric_code_key(b.sjh) <> app.numeric_code_key(a.sjh)
                        ) OR (
                            NULLIF(BTRIM(COALESCE(b.bah, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                            AND app.numeric_code_key(b.bah) <> app.numeric_code_key(a.bah)
                        )
                        """,
                        """
                        SELECT 'mr_archive_box_record' AS entity_type, b.id::text AS entity_id, b.bah, b.sjh,
                               CONCAT('archive_id=', b.archive_id, '，装箱编号与主档编号不一致') AS detail
                        FROM app.mr_archive_box_record b
                        JOIN app.mr_archive a ON a.id = b.archive_id
                        WHERE (
                            NULLIF(BTRIM(COALESCE(b.sjh, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                            AND app.numeric_code_key(b.sjh) <> app.numeric_code_key(a.sjh)
                        ) OR (
                            NULLIF(BTRIM(COALESCE(b.bah, '')), '') IS NOT NULL
                            AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                            AND app.numeric_code_key(b.bah) <> app.numeric_code_key(a.bah)
                        )
                        ORDER BY b.updated_at DESC, b.id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "DUPLICATE_SJH", "主档上架号比较键重复", "CRITICAL",
                        """
                        SELECT COALESCE(SUM(duplicate_count - 1), 0)
                        FROM (
                            SELECT COUNT(*) AS duplicate_count
                            FROM app.mr_archive
                            WHERE app.numeric_code_key(sjh) IS NOT NULL
                            GROUP BY app.numeric_code_key(sjh)
                            HAVING COUNT(*) > 1
                        ) duplicated
                        """,
                        """
                        SELECT 'mr_archive' AS entity_type, MIN(id)::text AS entity_id,
                               MIN(bah) AS bah, MIN(sjh) AS sjh,
                               CONCAT('忽略前导零后，同一上架号对应 ', COUNT(*), ' 条病案主档') AS detail
                        FROM app.mr_archive
                        WHERE app.numeric_code_key(sjh) IS NOT NULL
                        GROUP BY app.numeric_code_key(sjh)
                        HAVING COUNT(*) > 1
                        ORDER BY COUNT(*) DESC, MIN(id) DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "PATIENT_ARCHIVE_AMBIGUOUS", "患者记录病案号对应多个主档", "WARNING",
                        """
                        WITH archive_codes AS (
                            SELECT app.numeric_code_key(bah) AS code_key,
                                   COUNT(*) AS archive_count
                            FROM app.mr_archive
                            WHERE app.numeric_code_key(bah) IS NOT NULL
                            GROUP BY app.numeric_code_key(bah)
                            HAVING COUNT(*) > 1
                        )
                        SELECT COUNT(*)
                        FROM app.mr_patient p
                        JOIN archive_codes c ON app.numeric_code_key(p.bah) = c.code_key
                        """,
                        """
                        WITH archive_codes AS (
                            SELECT app.numeric_code_key(bah) AS code_key,
                                   COUNT(*) AS archive_count
                            FROM app.mr_archive
                            WHERE app.numeric_code_key(bah) IS NOT NULL
                            GROUP BY app.numeric_code_key(bah)
                            HAVING COUNT(*) > 1
                        )
                        SELECT 'mr_patient' AS entity_type, p.id::text AS entity_id, p.bah,
                               NULL::text AS sjh,
                               CONCAT('患者病案号可匹配 ', c.archive_count, ' 条 mr_archive，不能自动选择') AS detail
                        FROM app.mr_patient p
                        JOIN archive_codes c ON app.numeric_code_key(p.bah) = c.code_key
                        ORDER BY p.id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "MIGRATED_WITHOUT_OSS_URL", "已迁移扫描记录缺少 OSS 地址", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.mr_scan
                        WHERE LOWER(COALESCE(migration_status, ''))
                                IN ('migrated', 'success', 'completed', 'verified')
                          AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NULL
                        """,
                        """
                        SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                               CONCAT('迁移状态=', COALESCE(migration_status, 'NULL'), '，但 OSS 地址为空') AS detail
                        FROM app.mr_scan
                        WHERE LOWER(COALESCE(migration_status, ''))
                                IN ('migrated', 'success', 'completed', 'verified')
                          AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NULL
                        ORDER BY id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "MIGRATION_SUCCESS_UNVERIFIED", "迁移成功但未完成校验", "WARNING",
                        """
                        SELECT COUNT(*)
                        FROM app.image_migration_log
                        WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                          AND verified_at IS NULL
                        """,
                        """
                        SELECT 'image_migration_log' AS entity_type, id::text AS entity_id,
                               NULL::text AS bah, NULL::text AS sjh,
                               CONCAT('scan_id=', scan_id, '，迁移成功但 verified_at 为空') AS detail
                        FROM app.image_migration_log
                        WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed')
                          AND verified_at IS NULL
                        ORDER BY id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "MIGRATION_SCAN_ORPHAN", "迁移日志找不到扫描记录", "CRITICAL",
                        """
                        SELECT COUNT(*)
                        FROM app.image_migration_log l
                        LEFT JOIN app.mr_scan s ON s.id = l.scan_id
                        WHERE s.id IS NULL
                        """,
                        """
                        SELECT 'image_migration_log' AS entity_type, l.id::text AS entity_id,
                               NULL::text AS bah, NULL::text AS sjh,
                               CONCAT('scan_id=', l.scan_id, ' 在 mr_scan 中不存在') AS detail
                        FROM app.image_migration_log l
                        LEFT JOIN app.mr_scan s ON s.id = l.scan_id
                        WHERE s.id IS NULL
                        ORDER BY l.id DESC
                        """
                ),
                new DataQualityCheckDefinition(
                        "DUPLICATE_SCAN_PAGE_RECENT", "最近同一病案扫描页疑似重复", "WARNING",
                        """
                        SELECT COALESCE(SUM(duplicate_count - 1), 0)
                        FROM (
                            SELECT COUNT(*) AS duplicate_count
                            FROM app.mr_scan
                            WHERE id > %s
                              AND uploadflag <> 0
                              AND archive_id IS NOT NULL
                            GROUP BY archive_id, filename, pages
                            HAVING COUNT(*) > 1
                        ) duplicate_rows
                        """.formatted(RECENT_SCAN_BOUNDARY),
                        """
                        SELECT 'mr_scan' AS entity_type, MIN(id)::text AS entity_id,
                               MIN(bah) AS bah, MIN(sjh) AS sjh,
                               CONCAT('archive_id=', archive_id, '，相同文件名和页码重复 ', COUNT(*), ' 条') AS detail
                        FROM app.mr_scan
                        WHERE id > %s
                          AND uploadflag <> 0
                          AND archive_id IS NOT NULL
                        GROUP BY archive_id, filename, pages
                        HAVING COUNT(*) > 1
                        ORDER BY COUNT(*) DESC, MIN(id) DESC
                        """.formatted(RECENT_SCAN_BOUNDARY)
                ),
                new DataQualityCheckDefinition(
                        "ARCHIVE_BOX_EXCEPTION", "档案装箱状态异常", "WARNING",
                        "SELECT COUNT(*) FROM app.mr_archive_box_record WHERE status <> 'NORMAL'",
                        """
                        SELECT 'mr_archive_box_record' AS entity_type, id::text AS entity_id, bah, sjh,
                               CONCAT('装箱状态=', status, '，实际箱号=', COALESCE(box_no, 'NULL'),
                                      '，预期箱号=', COALESCE(expected_box_no, 'NULL')) AS detail
                        FROM app.mr_archive_box_record
                        WHERE status <> 'NORMAL'
                        ORDER BY updated_at DESC, id DESC
                        """
                )
        );
    }
}
