package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DataQualityService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DataQualityServiceImpl implements DataQualityService {

    private static final Logger logger = LoggerFactory.getLogger(DataQualityServiceImpl.class);
    private static final Set<String> LINKABLE_ENTITY_TYPES = Set.of(
            "mr_scan",
            "mr_statistics",
            "mr_archive_box_record"
    );

    private final JdbcTemplate jdbcTemplate;
    private final MultiGauge issuesGauge;
    private final AtomicLong totalIssuesGauge = new AtomicLong();
    private final AtomicLong lastSuccessTimestampGauge = new AtomicLong();
    private final AtomicLong runningGauge = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.data-quality.enabled:true}")
    private boolean enabled;

    @Value("${app.data-quality.sample-limit:200}")
    private int sampleLimit;

    @Value("${app.data-quality.retention-days:90}")
    private int retentionDays;

    private final List<CheckDefinition> checks = List.of(
            new CheckDefinition(
                    "SCAN_CODE_BLANK", "扫描记录病案号和上架号同时为空", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_scan
                    WHERE NULLIF(BTRIM(COALESCE(bah, '')), '') IS NULL
                      AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           '病案号和上架号同时为空，无法识别所属病案' AS detail
                    FROM app.mr_scan
                    WHERE NULLIF(BTRIM(COALESCE(bah, '')), '') IS NULL
                      AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "HIGH_BAH_WITHOUT_SJH", "高位病案号缺少唯一上架号", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_archive
                    WHERE BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                      AND BTRIM(bah)::numeric >= 10000000
                      AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_archive' AS entity_type, id::text AS entity_id, bah, sjh,
                           '数值病案号大于等于 10000000，但缺少用于唯一定位的上架号' AS detail
                    FROM app.mr_archive
                    WHERE BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                      AND BTRIM(bah)::numeric >= 10000000
                      AND NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_PAGES_INVALID", "扫描页码为空或小于等于零", "CRITICAL",
                    "SELECT COUNT(*) FROM app.mr_scan WHERE pages IS NULL OR pages <= 0",
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('无效页码: ', COALESCE(pages::text, 'NULL')) AS detail
                    FROM app.mr_scan
                    WHERE pages IS NULL OR pages <= 0
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_FILE_METADATA_MISSING", "扫描文件元数据缺失", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_scan
                    WHERE NULLIF(BTRIM(COALESCE(filename, '')), '') IS NULL
                       OR NULLIF(BTRIM(COALESCE(folder, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('文件名=', COALESCE(filename, 'NULL'), ', 目录=', COALESCE(folder, 'NULL')) AS detail
                    FROM app.mr_scan
                    WHERE NULLIF(BTRIM(COALESCE(filename, '')), '') IS NULL
                       OR NULLIF(BTRIM(COALESCE(folder, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
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
                        WHERE id > GREATEST((SELECT COALESCE(MAX(id), 0) - 500000 FROM app.mr_scan), 0)
                          AND uploadflag <> 0
                          AND archive_id IS NULL
                    )
                    SELECT GREATEST(
                        COALESCE((SELECT issue_count FROM estimated), 0),
                        COALESCE((SELECT issue_count FROM recent), 0)
                    )
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           '有效扫描记录 archive_id 为空；总量为统计估算，样本为最近记录' AS detail
                    FROM app.mr_scan
                    WHERE archive_id IS NULL
                      AND uploadflag <> 0
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "SCAN_ARCHIVE_LINK_MISMATCH_RECENT", "最近扫描记录与主档编号不一致", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_scan s
                    JOIN app.mr_archive a ON a.id = s.archive_id
                    WHERE s.id > GREATEST((SELECT COALESCE(MAX(id), 0) - 500000 FROM app.mr_scan), 0)
                      AND s.uploadflag <> 0
                      AND (
                        (
                          NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                          AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                          AND BTRIM(s.sjh) <> BTRIM(a.sjh)
                          AND NOT (
                            BTRIM(s.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                            AND COALESCE(NULLIF(LTRIM(BTRIM(s.sjh), '0'), ''), '0') =
                                COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0')
                          )
                        )
                        OR (
                          NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                          AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                          AND BTRIM(s.bah) <> BTRIM(a.bah)
                          AND NOT (
                            BTRIM(s.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                            AND COALESCE(NULLIF(LTRIM(BTRIM(s.bah), '0'), ''), '0') =
                                COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0')
                          )
                        )
                      )
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, s.id::text AS entity_id, s.bah, s.sjh,
                           CONCAT('archive_id=', s.archive_id, '，扫描编号与 mr_archive 编号不一致') AS detail
                    FROM app.mr_scan s
                    JOIN app.mr_archive a ON a.id = s.archive_id
                    WHERE s.id > GREATEST((SELECT COALESCE(MAX(id), 0) - 500000 FROM app.mr_scan), 0)
                      AND s.uploadflag <> 0
                      AND (
                        (NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                         AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                         AND BTRIM(s.sjh) <> BTRIM(a.sjh)
                         AND NOT (BTRIM(s.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                           AND COALESCE(NULLIF(LTRIM(BTRIM(s.sjh), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0')))
                        OR
                        (NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                         AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                         AND BTRIM(s.bah) <> BTRIM(a.bah)
                         AND NOT (BTRIM(s.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                           AND COALESCE(NULLIF(LTRIM(BTRIM(s.bah), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0')))
                      )
                    ORDER BY s.id DESC
                    """
            ),
            new CheckDefinition(
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
            new CheckDefinition(
                    "STATISTICS_ARCHIVE_LINK_MISMATCH", "统计记录与主档编号不一致", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_statistics s
                    JOIN app.mr_archive a ON a.id = s.archive_id
                    WHERE (
                        NULLIF(BTRIM(COALESCE(s.sjh, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                        AND BTRIM(s.sjh) <> BTRIM(a.sjh)
                        AND NOT (BTRIM(s.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(s.sjh), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0'))
                    ) OR (
                        NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                        AND BTRIM(s.bah) <> BTRIM(a.bah)
                        AND NOT (BTRIM(s.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(s.bah), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0'))
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
                        AND BTRIM(s.sjh) <> BTRIM(a.sjh)
                        AND NOT (BTRIM(s.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(s.sjh), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0'))
                    ) OR (
                        NULLIF(BTRIM(COALESCE(s.bah, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                        AND BTRIM(s.bah) <> BTRIM(a.bah)
                        AND NOT (BTRIM(s.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(s.bah), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0'))
                    )
                    ORDER BY s.id DESC
                    """
            ),
            new CheckDefinition(
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
            new CheckDefinition(
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
            new CheckDefinition(
                    "BOX_ARCHIVE_LINK_MISMATCH", "装箱记录与主档编号不一致", "CRITICAL",
                    """
                    SELECT COUNT(*)
                    FROM app.mr_archive_box_record b
                    JOIN app.mr_archive a ON a.id = b.archive_id
                    WHERE (
                        NULLIF(BTRIM(COALESCE(b.sjh, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.sjh, '')), '') IS NOT NULL
                        AND BTRIM(b.sjh) <> BTRIM(a.sjh)
                        AND NOT (BTRIM(b.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(b.sjh), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0'))
                    ) OR (
                        NULLIF(BTRIM(COALESCE(b.bah, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                        AND BTRIM(b.bah) <> BTRIM(a.bah)
                        AND NOT (BTRIM(b.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(b.bah), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0'))
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
                        AND BTRIM(b.sjh) <> BTRIM(a.sjh)
                        AND NOT (BTRIM(b.sjh) ~ '^[0-9]+$' AND BTRIM(a.sjh) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(b.sjh), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.sjh), '0'), ''), '0'))
                    ) OR (
                        NULLIF(BTRIM(COALESCE(b.bah, '')), '') IS NOT NULL
                        AND NULLIF(BTRIM(COALESCE(a.bah, '')), '') IS NOT NULL
                        AND BTRIM(b.bah) <> BTRIM(a.bah)
                        AND NOT (BTRIM(b.bah) ~ '^[0-9]+$' AND BTRIM(a.bah) ~ '^[0-9]+$'
                          AND COALESCE(NULLIF(LTRIM(BTRIM(b.bah), '0'), ''), '0') = COALESCE(NULLIF(LTRIM(BTRIM(a.bah), '0'), ''), '0'))
                    )
                    ORDER BY b.updated_at DESC, b.id DESC
                    """
            ),
            new CheckDefinition(
                    "DUPLICATE_SJH", "主档上架号规范化后重复", "CRITICAL",
                    """
                    SELECT COALESCE(SUM(duplicate_count - 1), 0)
                    FROM (
                        SELECT COUNT(*) AS duplicate_count
                        FROM app.mr_archive
                        WHERE NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NOT NULL
                        GROUP BY BTRIM(sjh)
                        HAVING COUNT(*) > 1
                    ) duplicated
                    """,
                    """
                    SELECT 'mr_archive' AS entity_type, MIN(id)::text AS entity_id,
                           MIN(bah) AS bah, BTRIM(sjh) AS sjh,
                           CONCAT('规范化上架号对应 ', COUNT(*), ' 条病案主档') AS detail
                    FROM app.mr_archive
                    WHERE NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NOT NULL
                    GROUP BY BTRIM(sjh)
                    HAVING COUNT(*) > 1
                    ORDER BY COUNT(*) DESC, MIN(id) DESC
                    """
            ),
            new CheckDefinition(
                    "PATIENT_ARCHIVE_AMBIGUOUS", "患者记录病案号对应多个主档", "WARNING",
                    """
                    WITH archive_codes AS (
                        SELECT COALESCE(NULLIF(LTRIM(BTRIM(bah), '0'), ''), '0') AS code_key,
                               COUNT(*) AS archive_count
                        FROM app.mr_archive
                        WHERE BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                        GROUP BY COALESCE(NULLIF(LTRIM(BTRIM(bah), '0'), ''), '0')
                        HAVING COUNT(*) > 1
                    )
                    SELECT COUNT(*)
                    FROM app.mr_patient p
                    JOIN archive_codes c
                      ON BTRIM(COALESCE(p.bah, '')) ~ '^[0-9]+$'
                     AND COALESCE(NULLIF(LTRIM(BTRIM(p.bah), '0'), ''), '0') = c.code_key
                    """,
                    """
                    WITH archive_codes AS (
                        SELECT COALESCE(NULLIF(LTRIM(BTRIM(bah), '0'), ''), '0') AS code_key,
                               COUNT(*) AS archive_count
                        FROM app.mr_archive
                        WHERE BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$'
                        GROUP BY COALESCE(NULLIF(LTRIM(BTRIM(bah), '0'), ''), '0')
                        HAVING COUNT(*) > 1
                    )
                    SELECT 'mr_patient' AS entity_type, p.id::text AS entity_id, p.bah,
                           NULL::text AS sjh,
                           CONCAT('患者病案号可匹配 ', c.archive_count, ' 条 mr_archive，不能自动选择') AS detail
                    FROM app.mr_patient p
                    JOIN archive_codes c
                      ON BTRIM(COALESCE(p.bah, '')) ~ '^[0-9]+$'
                     AND COALESCE(NULLIF(LTRIM(BTRIM(p.bah), '0'), ''), '0') = c.code_key
                    ORDER BY p.id DESC
                    """
            ),
            new CheckDefinition(
                    "MIGRATED_WITHOUT_OSS_URL", "已迁移扫描记录缺少 OSS 地址", "CRITICAL",
                    """
                    SELECT COUNT(*) FROM app.mr_scan
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed', 'verified')
                      AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NULL
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, id::text AS entity_id, bah, sjh,
                           CONCAT('迁移状态=', COALESCE(migration_status, 'NULL'), '，但 OSS 地址为空') AS detail
                    FROM app.mr_scan
                    WHERE LOWER(COALESCE(migration_status, '')) IN ('migrated', 'success', 'completed', 'verified')
                      AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NULL
                    ORDER BY id DESC
                    """
            ),
            new CheckDefinition(
                    "MIGRATION_SUCCESS_UNVERIFIED", "迁移成功但未完成校验", "WARNING",
                    """
                    SELECT COUNT(*) FROM app.image_migration_log
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
            new CheckDefinition(
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
            new CheckDefinition(
                    "DUPLICATE_SCAN_PAGE", "同一病案扫描页疑似重复", "WARNING",
                    """
                    SELECT COALESCE(SUM(duplicate_count - 1), 0)
                    FROM (
                        SELECT COUNT(*) AS duplicate_count
                        FROM app.mr_scan
                        WHERE uploadflag <> 0
                          AND archive_id IS NOT NULL
                        GROUP BY archive_id, filename, pages
                        HAVING COUNT(*) > 1
                    ) duplicate_rows
                    """,
                    """
                    SELECT 'mr_scan' AS entity_type, MIN(id)::text AS entity_id,
                           MIN(bah) AS bah, MIN(sjh) AS sjh,
                           CONCAT('archive_id=', archive_id, '，相同文件名和页码重复 ', COUNT(*), ' 条') AS detail
                    FROM app.mr_scan
                    WHERE uploadflag <> 0
                      AND archive_id IS NOT NULL
                    GROUP BY archive_id, filename, pages
                    HAVING COUNT(*) > 1
                    ORDER BY COUNT(*) DESC, MIN(id) DESC
                    """
            ),
            new CheckDefinition(
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

    public DataQualityServiceImpl(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.issuesGauge = MultiGauge.builder("mrr.data.quality.issues")
                .description("MRR data quality issue count grouped by check and severity")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.total.issues", totalIssuesGauge, AtomicLong::get)
                .description("Total issues from the latest completed data quality run")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.last.success.timestamp", lastSuccessTimestampGauge, AtomicLong::get)
                .description("Epoch seconds of the latest successful data quality run")
                .register(meterRegistry);
        Gauge.builder("mrr.data.quality.running", runningGauge, AtomicLong::get)
                .description("Whether a data quality run is currently executing")
                .register(meterRegistry);
    }

    @PostConstruct
    public void initializeMetrics() {
        try {
            refreshMetricsFromLatestRun();
        } catch (Exception e) {
            logger.warn("Unable to initialize data quality metrics: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${app.data-quality.cron:0 15 2 * * ?}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        try {
            runChecks("scheduled");
        } catch (Exception e) {
            logger.error("Scheduled data quality run failed", e);
        }
    }

    @Override
    public Map<String, Object> getSummary() {
        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT id, status, triggered_by, check_count, total_issues, critical_count,
                       warning_count, started_at, completed_at, error_message
                FROM app.mrr_data_quality_run
                ORDER BY id DESC
                LIMIT 1
                """);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running.get());
        result.put("enabled", enabled);
        if (runs.isEmpty()) {
            result.put("latestRun", null);
            result.put("checks", List.of());
            return result;
        }
        Map<String, Object> latestRun = runs.getFirst();
        result.put("latestRun", latestRun);
        result.put("checks", jdbcTemplate.queryForList("""
                SELECT check_code, check_name, severity, issue_count, sampled_count, checked_at
                FROM app.mrr_data_quality_check_result
                WHERE run_id = ?
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, issue_count DESC, check_code
                """, latestRun.get("id")));
        return result;
    }

    @Override
    public List<Map<String, Object>> getIssues(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Long> runIds = jdbcTemplate.query(
                "SELECT id FROM app.mrr_data_quality_run ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1)
        );
        if (runIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT id, check_code, check_name, severity, entity_type, entity_id,
                       bah, sjh, detail, detected_at
                FROM app.mrr_data_quality_issue
                WHERE run_id = ?
                ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 ELSE 2 END, id DESC
                LIMIT ?
                """, runIds.getFirst(), safeLimit);
    }

    @Override
    public Map<String, Object> getIssue(long issueId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT i.id, i.run_id AS "runId", i.check_code AS "checkCode",
                       i.check_name AS "checkName", i.severity,
                       i.entity_type AS "entityType", i.entity_id AS "entityId",
                       i.bah, i.sjh, i.detail, i.detected_at AS "detectedAt",
                       r.status AS "runStatus", r.triggered_by AS "triggeredBy",
                       r.started_at AS "runStartedAt", r.completed_at AS "runCompletedAt"
                FROM app.mrr_data_quality_issue i
                JOIN app.mrr_data_quality_run r ON r.id = i.run_id
                WHERE i.id = ?
                """, issueId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "数据质量异常不存在");
        }
        return rows.getFirst();
    }

    @Override
    public Map<String, Object> previewRepair(long issueId) {
        Map<String, Object> issue = getIssue(issueId);
        String checkCode = String.valueOf(issue.get("checkCode"));
        String entityType = normalize(issue.get("entityType"));
        String entityId = normalize(issue.get("entityId"));
        String bah = normalize(issue.get("bah"));
        String sjh = normalize(issue.get("sjh"));

        List<Map<String, Object>> candidates = findArchiveCandidates(bah, sjh);
        Map<String, Object> currentEntity = loadCurrentEntity(entityType, entityId);
        String suggestedAction = suggestedAction(checkCode);
        boolean deterministic = suggestedAction.equals("LINK_ARCHIVE_ID")
                && LINKABLE_ENTITY_TYPES.contains(entityType)
                && candidates.size() == 1
                && currentEntity.get("archiveId") == null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issue", issue);
        result.put("currentEntity", currentEntity);
        result.put("candidateArchives", candidates);
        result.put("suggestedAction", suggestedAction);
        result.put("deterministic", deterministic);
        result.put("readOnly", true);
        result.put("canApply", false);
        result.put("reason", deterministic
                ? "存在唯一候选主档，但第一阶段仅提供预览，尚未开放写入"
                : "候选关系不唯一或异常类型需要人工判断，禁止自动修改");
        return result;
    }

    @Override
    public Map<String, Object> runChecks(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("数据质量检查正在运行，请稍后重试");
        }
        runningGauge.set(1);
        Long runId = null;
        try {
            String safeTriggeredBy = triggeredBy == null || triggeredBy.isBlank()
                    ? "manual"
                    : triggeredBy.substring(0, Math.min(32, triggeredBy.length()));
            runId = jdbcTemplate.queryForObject("""
                    INSERT INTO app.mrr_data_quality_run(status, triggered_by, started_at)
                    VALUES ('RUNNING', ?, CURRENT_TIMESTAMP)
                    RETURNING id
                    """, Long.class, safeTriggeredBy);

            long totalIssues = 0;
            long criticalCount = 0;
            long warningCount = 0;
            int safeSampleLimit = Math.max(1, Math.min(sampleLimit, 1000));

            for (CheckDefinition check : checks) {
                Long countValue = jdbcTemplate.queryForObject(check.countSql(), Long.class);
                long issueCount = countValue == null ? 0 : countValue;
                List<Map<String, Object>> samples = issueCount == 0
                        ? List.of()
                        : jdbcTemplate.queryForList(check.sampleSql() + " LIMIT ?", safeSampleLimit);

                jdbcTemplate.update("""
                        INSERT INTO app.mrr_data_quality_check_result
                            (run_id, check_code, check_name, severity, issue_count, sampled_count)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, runId, check.code(), check.name(), check.severity(), issueCount, samples.size());

                for (Map<String, Object> sample : samples) {
                    jdbcTemplate.update("""
                            INSERT INTO app.mrr_data_quality_issue
                                (run_id, check_code, check_name, severity, entity_type, entity_id, bah, sjh, detail)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                            runId, check.code(), check.name(), check.severity(),
                            sample.get("entity_type"), sample.get("entity_id"), sample.get("bah"),
                            sample.get("sjh"), sample.get("detail"));
                }

                totalIssues += issueCount;
                if ("CRITICAL".equals(check.severity())) {
                    criticalCount += issueCount;
                } else {
                    warningCount += issueCount;
                }
            }

            jdbcTemplate.update("""
                    UPDATE app.mrr_data_quality_run
                    SET status = 'SUCCESS', check_count = ?, total_issues = ?, critical_count = ?,
                        warning_count = ?, completed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, checks.size(), totalIssues, criticalCount, warningCount, runId);

            cleanupExpiredRuns();
            refreshMetrics(runId);
            logger.info("Data quality run {} completed: total={}, critical={}, warning={}",
                    runId, totalIssues, criticalCount, warningCount);
            return getSummary();
        } catch (Exception e) {
            if (runId != null) {
                jdbcTemplate.update("""
                        UPDATE app.mrr_data_quality_run
                        SET status = 'FAILED', completed_at = CURRENT_TIMESTAMP, error_message = ?
                        WHERE id = ?
                        """, abbreviate(e.getMessage(), 2000), runId);
            }
            throw new IllegalStateException("数据质量检查失败: " + e.getMessage(), e);
        } finally {
            running.set(false);
            runningGauge.set(0);
        }
    }

    private List<Map<String, Object>> findArchiveCandidates(String bah, String sjh) {
        if (bah == null && sjh == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT id, bah, sjh, patient_name AS "patientName",
                       inpatient_department AS "department", page_count AS "pageCount",
                       CASE
                           WHEN ? IS NOT NULL AND BTRIM(COALESCE(sjh, '')) = ? THEN 'SJH_EXACT'
                           WHEN ? IS NOT NULL AND BTRIM(COALESCE(bah, '')) = ? THEN 'BAH_EXACT'
                           ELSE 'FORMAT_ONLY'
                       END AS "matchType"
                FROM app.mr_archive
                WHERE (? IS NOT NULL AND (
                        BTRIM(COALESCE(sjh, '')) = ?
                        OR (
                            BTRIM(COALESCE(sjh, '')) ~ '^[0-9]+$' AND ? ~ '^[0-9]+$'
                            AND COALESCE(NULLIF(LTRIM(BTRIM(sjh), '0'), ''), '0') =
                                COALESCE(NULLIF(LTRIM(?, '0'), ''), '0')
                        )
                      ))
                   OR (? IS NULL AND ? IS NOT NULL AND (
                        BTRIM(COALESCE(bah, '')) = ?
                        OR (
                            BTRIM(COALESCE(bah, '')) ~ '^[0-9]+$' AND ? ~ '^[0-9]+$'
                            AND COALESCE(NULLIF(LTRIM(BTRIM(bah), '0'), ''), '0') =
                                COALESCE(NULLIF(LTRIM(?, '0'), ''), '0')
                        )
                      ))
                ORDER BY CASE
                           WHEN ? IS NOT NULL AND BTRIM(COALESCE(sjh, '')) = ? THEN 0
                           WHEN ? IS NOT NULL AND BTRIM(COALESCE(bah, '')) = ? THEN 1
                           ELSE 2
                         END,
                         id DESC
                LIMIT 5
                """,
                sjh, sjh, bah, bah,
                sjh, sjh, sjh, sjh,
                sjh, bah, bah, bah, bah,
                sjh, sjh, bah, bah);
    }

    private Map<String, Object> loadCurrentEntity(String entityType, String entityId) {
        if (entityType == null || entityId == null) {
            return Map.of();
        }
        if (!LINKABLE_ENTITY_TYPES.contains(entityType)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityType", entityType);
            result.put("entityId", entityId);
            result.put("archiveId", null);
            result.put("readOnly", true);
            return result;
        }

        long id;
        try {
            id = Long.parseLong(entityId);
        } catch (NumberFormatException exception) {
            return Map.of();
        }

        String sql = switch (entityType) {
            case "mr_scan" -> """
                    SELECT 'mr_scan' AS "entityType", id AS "entityId", archive_id AS "archiveId",
                           bah, sjh, filename, pages, uploadflag AS "uploadFlag"
                    FROM app.mr_scan WHERE id = ?
                    """;
            case "mr_statistics" -> """
                    SELECT 'mr_statistics' AS "entityType", id AS "entityId", archive_id AS "archiveId",
                           bah, sjh, patientname AS "patientName", pages
                    FROM app.mr_statistics WHERE id = ?
                    """;
            case "mr_archive_box_record" -> """
                    SELECT 'mr_archive_box_record' AS "entityType", id AS "entityId", archive_id AS "archiveId",
                           bah, sjh, box_no AS "boxNo", status
                    FROM app.mr_archive_box_record WHERE id = ?
                    """;
            default -> throw new IllegalStateException("unsupported entity type");
        };
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private String suggestedAction(String checkCode) {
        String normalized = checkCode == null ? "" : checkCode.toUpperCase(Locale.ROOT);
        if (normalized.contains("LINK_MISSING")) {
            return "LINK_ARCHIVE_ID";
        }
        if (normalized.contains("LINK_MISMATCH")) {
            return "REVIEW_ARCHIVE_LINK";
        }
        if (normalized.contains("DUPLICATE")) {
            return "REVIEW_DUPLICATE";
        }
        if (normalized.contains("AMBIGUOUS")) {
            return "MANUAL_DISAMBIGUATION";
        }
        return "MANUAL_REVIEW";
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void cleanupExpiredRuns() {
        int safeRetentionDays = Math.max(7, retentionDays);
        jdbcTemplate.update("""
                DELETE FROM app.mrr_data_quality_run
                WHERE started_at < CURRENT_TIMESTAMP - make_interval(days => ?)
                """, safeRetentionDays);
    }

    private void refreshMetricsFromLatestRun() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM app.mrr_data_quality_run WHERE status = 'SUCCESS' ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1)
        );
        if (!ids.isEmpty()) {
            refreshMetrics(ids.getFirst());
        }
    }

    private void refreshMetrics(long runId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT check_code, severity, issue_count
                FROM app.mrr_data_quality_check_result
                WHERE run_id = ?
                """, runId);
        List<MultiGauge.Row<?>> gaugeRows = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("issue_count")).longValue();
            total += count;
            gaugeRows.add(MultiGauge.Row.of(
                    Tags.of(
                            "check_code", String.valueOf(row.get("check_code")),
                            "severity", String.valueOf(row.get("severity"))
                    ),
                    count
            ));
        }
        issuesGauge.register(gaugeRows, true);
        totalIssuesGauge.set(total);
        lastSuccessTimestampGauge.set(Instant.now().getEpochSecond());
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record CheckDefinition(
            String code,
            String name,
            String severity,
            String countSql,
            String sampleSql
    ) {
    }
}
