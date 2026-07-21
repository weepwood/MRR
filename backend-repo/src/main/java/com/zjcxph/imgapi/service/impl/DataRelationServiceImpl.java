package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DataRelationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataRelationServiceImpl implements DataRelationService {

    private static final int MAX_SEARCH_RESULTS = 50;
    private static final int DETAIL_SAMPLE_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public DataRelationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> getOverview() {
        List<Map<String, Object>> relations = new ArrayList<>();
        relations.add(exactArchiveCoverage(
                "mr_statistics",
                "统计记录",
                "mr_statistics.archive_id → mr_archive.id"
        ));
        relations.add(estimatedScanCoverage());
        relations.add(exactArchiveCoverage(
                "mr_archive_box_record",
                "装箱记录",
                "mr_archive_box_record.archive_id → mr_archive.id"
        ));
        relations.add(legacyPatientCoverage());

        long linkedTotal = 0;
        long relationTotal = 0;
        for (Map<String, Object> relation : relations) {
            if (!Boolean.TRUE.equals(relation.get("coverageIncluded"))) {
                continue;
            }
            linkedTotal += number(relation.get("linkedCount"));
            relationTotal += number(relation.get("totalCount"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("archiveCount", queryCount("SELECT COUNT(*) FROM app.mr_archive"));
        result.put("healthScore", percentage(linkedTotal, relationTotal));
        result.put("relations", relations);
        result.put("latestQualityRun", latestQualityRun());
        result.put("relationChecks", latestRelationChecks());
        result.put("notes", List.of(
                "mr_scan 覆盖率来自 PostgreSQL 统计信息，页面打开时不会全表扫描三千万级数据",
                "mr_patient 当前没有 archive_id，只按 BAH 进行遗留兼容匹配，不把 patient_id 当身份证号猜测关联",
                "精确关联覆盖率以最近一次数据质量检查结果和外键验收为准"
        ));
        return result;
    }

    @Override
    public List<Map<String, Object>> searchArchives(String type, String value, int limit) {
        String searchType = DataRelationComparisonUtils.normalizeSearchType(type);
        String searchValue = DataRelationComparisonUtils.normalizeText(value);
        if (searchValue == null) {
            throw new BusinessException(400, "查询值不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_RESULTS));

        return switch (searchType) {
            case "ARCHIVE_ID" -> searchByArchiveId(searchValue);
            case "BAH" -> searchByCode("bah", searchValue, safeLimit);
            case "SJH" -> searchByCode("sjh", searchValue, safeLimit);
            default -> throw new BusinessException(400, "查询类型仅支持 ARCHIVE_ID、BAH 或 SJH");
        };
    }

    @Override
    public Map<String, Object> getArchiveRelation(long archiveId) {
        Map<String, Object> archive = findArchive(archiveId);
        String bah = DataRelationComparisonUtils.normalizeText(archive.get("bah"));
        String sjh = DataRelationComparisonUtils.normalizeText(archive.get("sjh"));

        List<Map<String, Object>> statistics = relatedStatistics(archiveId, bah, sjh);
        List<Map<String, Object>> patients = relatedPatients(bah);
        List<Map<String, Object>> boxes = relatedBoxes(archiveId, bah, sjh);
        Map<String, Object> scanSummary = scanSummary(archiveId, bah, sjh);
        List<Map<String, Object>> scanSamples = scanSamples(archiveId);
        Map<String, Object> migrationSummary = migrationSummary(archiveId);
        List<Map<String, Object>> comparisons = buildComparisons(
                archive,
                statistics,
                patients,
                boxes,
                scanSamples,
                scanSummary
        );

        List<String> warnings = new ArrayList<>();
        warnings.add("mr_patient 尚无 archive_id，患者记录仅通过 BAH 兼容匹配，结果不能视为数据库外键关系");
        if (number(scanSummary.get("unlinkedCandidateCount")) > 0) {
            warnings.add("发现编号相同但 archive_id 为空的扫描记录，需要进入异常中心确认关联");
        }
        if (number(scanSummary.get("duplicatePageCount")) > 0) {
            warnings.add("当前病案存在重复页码，请核对是否为重复扫描或合法多文件页");
        }
        if (hasLegacyRows(statistics)) {
            warnings.add("存在尚未写入 archive_id、仅通过编号兼容匹配的统计记录");
        }
        if (hasLegacyRows(boxes)) {
            warnings.add("存在尚未写入 archive_id、仅通过编号兼容匹配的装箱记录");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("archive", archive);
        result.put("statistics", statistics);
        result.put("patients", patients);
        result.put("boxes", boxes);
        result.put("scanSummary", scanSummary);
        result.put("scanSamples", scanSamples);
        result.put("migrationSummary", migrationSummary);
        result.put("comparisons", comparisons);
        result.put("warnings", warnings);
        result.put("readOnly", true);
        return result;
    }

    private Map<String, Object> exactArchiveCoverage(String tableName, String label, String relation) {
        Map<String, Object> counts = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total_count, COUNT(archive_id) AS linked_count FROM app." + tableName
        );
        long total = number(counts.get("total_count"));
        long linked = number(counts.get("linked_count"));
        return relationOverview(tableName, label, relation, total, linked, false, "EXACT", true);
    }

    private Map<String, Object> estimatedScanCoverage() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT GREATEST(c.reltuples::bigint, 0) AS estimated_total,
                       COALESCE(s.null_frac, 0)::double precision AS null_fraction,
                       pg_total_relation_size(c.oid) AS total_bytes
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stats s
                  ON s.schemaname = n.nspname
                 AND s.tablename = c.relname
                 AND s.attname = 'archive_id'
                WHERE n.nspname = 'app'
                  AND c.relname = 'mr_scan'
                LIMIT 1
                """);

        long total = rows.isEmpty() ? 0 : number(rows.getFirst().get("estimated_total"));
        double nullFraction = rows.isEmpty() ? 0 : decimal(rows.getFirst().get("null_fraction"));
        long missing = Math.max(0, Math.round(total * nullFraction));
        long linked = Math.max(0, total - missing);
        Map<String, Object> overview = relationOverview(
                "mr_scan",
                "扫描记录",
                "mr_scan.archive_id → mr_archive.id",
                total,
                linked,
                true,
                "POSTGRES_STATISTICS",
                true
        );
        overview.put("estimatedMissingCount", missing);
        overview.put("totalBytes", rows.isEmpty() ? 0 : number(rows.getFirst().get("total_bytes")));
        return overview;
    }

    private Map<String, Object> legacyPatientCoverage() {
        long total = queryCount("SELECT COUNT(*) FROM app.mr_patient");
        Map<String, Object> result = relationOverview(
                "mr_patient",
                "患者记录",
                "mr_patient.bah ⇢ mr_archive（遗留关联）",
                total,
                0,
                false,
                "LEGACY_BAH_ONLY",
                false
        );
        result.put("status", "LEGACY");
        return result;
    }

    private Map<String, Object> relationOverview(
            String tableName,
            String label,
            String relation,
            long total,
            long linked,
            boolean estimated,
            String source,
            boolean coverageIncluded
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("label", label);
        result.put("relation", relation);
        result.put("totalCount", total);
        result.put("linkedCount", linked);
        result.put("missingCount", Math.max(0, total - linked));
        result.put("coverage", percentage(linked, total));
        result.put("estimated", estimated);
        result.put("source", source);
        result.put("coverageIncluded", coverageIncluded);
        result.put("status", coverageStatus(linked, total));
        return result;
    }

    private List<Map<String, Object>> searchByArchiveId(String value) {
        long archiveId;
        try {
            archiveId = Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "archive_id 必须是整数");
        }
        return jdbcTemplate.queryForList("""
                SELECT id, bah, sjh, patient_id AS "patientId", patient_name AS "patientName",
                       inpatient_department AS "department", archive_date AS "archiveDate",
                       discharge_date AS "dischargeDate", archive_type AS "archiveType",
                       page_count AS "pageCount", scan_count AS "scanCount",
                       'EXACT' AS "matchType"
                FROM app.v_archive_summary
                WHERE id = ?
                """, archiveId);
    }

    private List<Map<String, Object>> searchByCode(String column, String value, int limit) {
        String sql = """
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS code
                )
                SELECT a.id, a.bah, a.sjh, a.patient_id AS "patientId",
                       a.patient_name AS "patientName",
                       a.inpatient_department AS "department", a.archive_date AS "archiveDate",
                       a.discharge_date AS "dischargeDate", a.archive_type AS "archiveType",
                       a.page_count AS "pageCount", a.scan_count AS "scanCount",
                       CASE WHEN BTRIM(COALESCE(a.%1$s, '')) = i.code
                            THEN 'EXACT' ELSE 'FORMAT_ONLY' END AS "matchType"
                FROM app.v_archive_summary a
                CROSS JOIN input i
                WHERE BTRIM(COALESCE(a.%1$s, '')) = i.code
                   OR (
                        BTRIM(COALESCE(a.%1$s, '')) ~ '^[0-9]+$'
                        AND i.code ~ '^[0-9]+$'
                        AND COALESCE(NULLIF(LTRIM(BTRIM(a.%1$s), '0'), ''), '0') =
                            COALESCE(NULLIF(LTRIM(i.code, '0'), ''), '0')
                   )
                ORDER BY CASE WHEN BTRIM(COALESCE(a.%1$s, '')) = i.code THEN 0 ELSE 1 END,
                         a.id DESC
                LIMIT ?
                """.formatted(column);
        return jdbcTemplate.queryForList(sql, value, limit);
    }

    private Map<String, Object> findArchive(long archiveId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, bah, sjh, patient_id AS "patientId", patient_name AS "patientName",
                       inpatient_department AS "department", device_id AS "deviceId",
                       operator_no AS "operatorNo", archive_date AS "archiveDate",
                       discharge_date AS "dischargeDate", archive_type AS "archiveType",
                       page_count AS "pageCount", source_statistics_id AS "sourceStatisticsId",
                       created_at AS "createdAt", updated_at AS "updatedAt"
                FROM app.mr_archive
                WHERE id = ?
                """, archiveId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "病案主档不存在");
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> relatedStatistics(long archiveId, String bah, String sjh) {
        return jdbcTemplate.queryForList("""
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS bah,
                           NULLIF(BTRIM(CAST(? AS text)), '') AS sjh
                )
                SELECT s.id, s.archive_id AS "archiveId", s.bah, s.sjh, s.cid, s.openerno,
                       s.date, s.type, s.pages, s.patientname AS "patientName",
                       s.inpatientdepartment AS "department", s.patientid AS "patientId",
                       s.dischargedate AS "dischargeDate",
                       CASE WHEN s.archive_id = ? THEN 'ARCHIVE_ID' ELSE 'LEGACY_CODE' END AS "relationMode"
                FROM app.mr_statistics s
                CROSS JOIN input i
                WHERE s.archive_id = ?
                   OR (
                        s.archive_id IS NULL
                        AND (
                            (i.sjh IS NOT NULL AND (
                                BTRIM(COALESCE(s.sjh, '')) = i.sjh
                                OR numeric_code_key(s.sjh) = numeric_code_key(i.sjh)
                            ))
                            OR (i.sjh IS NULL AND i.bah IS NOT NULL AND (
                                BTRIM(COALESCE(s.bah, '')) = i.bah
                                OR numeric_code_key(s.bah) = numeric_code_key(i.bah)
                            ))
                        )
                   )
                ORDER BY CASE WHEN s.archive_id = ? THEN 0 ELSE 1 END, s.id DESC
                LIMIT ?
                """, bah, sjh, archiveId, archiveId, archiveId, DETAIL_SAMPLE_LIMIT);
    }

    private List<Map<String, Object>> relatedPatients(String bah) {
        if (bah == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS bah
                )
                SELECT p.id, p.idcard, p.bah, p.admissiontime, p.department, p.name,
                       p.ruyuan, p.bingqu, p.chuangwei,
                       CASE WHEN BTRIM(COALESCE(p.bah, '')) = i.bah
                            THEN 'LEGACY_BAH_EXACT' ELSE 'LEGACY_BAH_FORMAT' END AS "relationMode"
                FROM app.mr_patient p
                CROSS JOIN input i
                WHERE BTRIM(COALESCE(p.bah, '')) = i.bah
                   OR numeric_code_key(p.bah) = numeric_code_key(i.bah)
                ORDER BY p.id DESC
                LIMIT ?
                """, bah, DETAIL_SAMPLE_LIMIT);
    }

    private List<Map<String, Object>> relatedBoxes(long archiveId, String bah, String sjh) {
        return jdbcTemplate.queryForList("""
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS bah,
                           NULLIF(BTRIM(CAST(? AS text)), '') AS sjh
                )
                SELECT b.id, b.archive_id AS "archiveId", b.bah, b.sjh, b.box_no AS "boxNo",
                       b.expected_box_no AS "expectedBoxNo", b.status, b.remark,
                       b.created_at AS "createdAt", b.updated_at AS "updatedAt",
                       CASE WHEN b.archive_id = ? THEN 'ARCHIVE_ID' ELSE 'LEGACY_CODE' END AS "relationMode"
                FROM app.mr_archive_box_record b
                CROSS JOIN input i
                WHERE b.archive_id = ?
                   OR (
                        b.archive_id IS NULL
                        AND (
                            (i.sjh IS NOT NULL AND (
                                BTRIM(COALESCE(b.sjh, '')) = i.sjh
                                OR numeric_code_key(b.sjh) = numeric_code_key(i.sjh)
                            ))
                            OR (i.sjh IS NULL AND i.bah IS NOT NULL AND (
                                BTRIM(COALESCE(b.bah, '')) = i.bah
                                OR numeric_code_key(b.bah) = numeric_code_key(i.bah)
                            ))
                        )
                   )
                ORDER BY CASE WHEN b.archive_id = ? THEN 0 ELSE 1 END,
                         b.updated_at DESC, b.id DESC
                LIMIT ?
                """, bah, sjh, archiveId, archiveId, archiveId, DETAIL_SAMPLE_LIMIT);
    }

    private Map<String, Object> scanSummary(long archiveId, String bah, String sjh) {
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE uploadflag <> 0) AS active_count,
                       COUNT(*) FILTER (WHERE uploadflag = 0) AS deleted_count,
                       COUNT(DISTINCT pages) FILTER (WHERE uploadflag <> 0) AS distinct_page_count,
                       MIN(pages) FILTER (WHERE uploadflag <> 0) AS min_page,
                       MAX(pages) FILTER (WHERE uploadflag <> 0) AS max_page,
                       COUNT(*) FILTER (WHERE uploadflag <> 0) -
                           COUNT(DISTINCT pages) FILTER (WHERE uploadflag <> 0) AS duplicate_page_count,
                       COUNT(*) FILTER (
                           WHERE uploadflag <> 0 AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NOT NULL
                       ) AS oss_count
                FROM app.mr_scan
                WHERE archive_id = ?
                """, archiveId);

        long unlinkedCandidates = queryCount("""
                WITH input AS (
                    SELECT NULLIF(BTRIM(CAST(? AS text)), '') AS bah,
                           NULLIF(BTRIM(CAST(? AS text)), '') AS sjh
                )
                SELECT COUNT(*)
                FROM app.mr_scan s
                CROSS JOIN input i
                WHERE s.archive_id IS NULL
                  AND s.uploadflag <> 0
                  AND (
                        (i.sjh IS NOT NULL AND (
                            BTRIM(COALESCE(s.sjh, '')) = i.sjh
                            OR numeric_code_key(s.sjh) = numeric_code_key(i.sjh)
                        ))
                        OR (i.sjh IS NULL AND i.bah IS NOT NULL AND (
                            BTRIM(COALESCE(s.bah, '')) = i.bah
                            OR numeric_code_key(s.bah) = numeric_code_key(i.bah)
                        ))
                  )
                """, bah, sjh);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeCount", number(summary.get("active_count")));
        result.put("deletedCount", number(summary.get("deleted_count")));
        result.put("distinctPageCount", number(summary.get("distinct_page_count")));
        result.put("minPage", summary.get("min_page"));
        result.put("maxPage", summary.get("max_page"));
        result.put("duplicatePageCount", Math.max(0, number(summary.get("duplicate_page_count"))));
        result.put("ossCount", number(summary.get("oss_count")));
        result.put("unlinkedCandidateCount", unlinkedCandidates);
        return result;
    }

    private List<Map<String, Object>> scanSamples(long archiveId) {
        return jdbcTemplate.queryForList("""
                SELECT id, archive_id AS "archiveId", bah, sjh, filename, btype, pages,
                       uploadflag AS "uploadFlag", folder, migration_status AS "migrationStatus",
                       CASE WHEN NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NULL
                            THEN false ELSE true END AS "hasOss"
                FROM app.mr_scan
                WHERE archive_id = ?
                ORDER BY pages, id
                LIMIT ?
                """, archiveId, DETAIL_SAMPLE_LIMIT);
    }

    private Map<String, Object> migrationSummary(long archiveId) {
        return jdbcTemplate.queryForMap("""
                SELECT COUNT(l.id) AS log_count,
                       COUNT(l.id) FILTER (
                           WHERE LOWER(COALESCE(l.migration_status, ''))
                               IN ('success', 'migrated', 'completed', 'verified')
                       ) AS success_count,
                       COUNT(l.id) FILTER (
                           WHERE LOWER(COALESCE(l.migration_status, '')) IN ('failed', 'error')
                       ) AS failed_count,
                       MAX(l.updated_at) AS last_updated_at
                FROM app.mr_scan s
                LEFT JOIN app.image_migration_log l ON l.scan_id = s.id
                WHERE s.archive_id = ?
                """, archiveId);
    }

    private List<Map<String, Object>> buildComparisons(
            Map<String, Object> archive,
            List<Map<String, Object>> statistics,
            List<Map<String, Object>> patients,
            List<Map<String, Object>> boxes,
            List<Map<String, Object>> scans,
            Map<String, Object> scanSummary
    ) {
        List<Map<String, Object>> comparisons = new ArrayList<>();
        Map<String, Object> statisticsRow = firstOrEmpty(statistics);
        Map<String, Object> patientRow = firstOrEmpty(patients);
        Map<String, Object> boxRow = firstOrEmpty(boxes);
        Map<String, Object> scanRow = firstOrEmpty(scans);

        comparisons.add(DataRelationComparisonUtils.compareCode("BAH", archive.get("bah"), "mr_statistics", statisticsRow.get("bah")));
        comparisons.add(DataRelationComparisonUtils.compareCode("BAH", archive.get("bah"), "mr_patient", patientRow.get("bah")));
        comparisons.add(DataRelationComparisonUtils.compareCode("BAH", archive.get("bah"), "mr_archive_box_record", boxRow.get("bah")));
        comparisons.add(DataRelationComparisonUtils.compareCode("BAH", archive.get("bah"), "mr_scan", scanRow.get("bah")));
        comparisons.add(DataRelationComparisonUtils.compareCode("SJH", archive.get("sjh"), "mr_statistics", statisticsRow.get("sjh")));
        comparisons.add(DataRelationComparisonUtils.compareCode("SJH", archive.get("sjh"), "mr_archive_box_record", boxRow.get("sjh")));
        comparisons.add(DataRelationComparisonUtils.compareCode("SJH", archive.get("sjh"), "mr_scan", scanRow.get("sjh")));
        comparisons.add(DataRelationComparisonUtils.compareText("患者姓名", archive.get("patientName"), "mr_statistics", statisticsRow.get("patientName")));
        comparisons.add(DataRelationComparisonUtils.compareText("患者姓名", archive.get("patientName"), "mr_patient", patientRow.get("name")));
        comparisons.add(DataRelationComparisonUtils.compareText("科室", archive.get("department"), "mr_statistics", statisticsRow.get("department")));
        comparisons.add(DataRelationComparisonUtils.compareText("科室", archive.get("department"), "mr_patient", patientRow.get("department")));
        comparisons.add(DataRelationComparisonUtils.compareNumber("页数", archive.get("pageCount"), "mr_scan.activeCount", scanSummary.get("activeCount")));
        return comparisons;
    }

    private Map<String, Object> latestQualityRun() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, status, triggered_by AS "triggeredBy", check_count AS "checkCount",
                       total_issues AS "totalIssues", critical_count AS "criticalCount",
                       warning_count AS "warningCount", started_at AS "startedAt",
                       completed_at AS "completedAt", error_message AS "errorMessage"
                FROM app.mrr_data_quality_run
                ORDER BY id DESC
                LIMIT 1
                """);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private List<Map<String, Object>> latestRelationChecks() {
        return jdbcTemplate.queryForList("""
                SELECT r.check_code AS "checkCode", r.check_name AS "checkName",
                       r.severity, r.issue_count AS "issueCount",
                       r.sampled_count AS "sampledCount", r.checked_at AS "checkedAt"
                FROM app.mrr_data_quality_check_result r
                WHERE r.run_id = (
                    SELECT id FROM app.mrr_data_quality_run ORDER BY id DESC LIMIT 1
                )
                  AND (
                    r.check_code LIKE '%ARCHIVE%'
                    OR r.check_code LIKE '%ORPHAN%'
                    OR r.check_code LIKE '%HIGH_BAH%'
                    OR r.check_code LIKE '%DUPLICATE_SJH%'
                  )
                ORDER BY CASE r.severity WHEN 'CRITICAL' THEN 1 ELSE 2 END,
                         r.issue_count DESC, r.check_code
                """);
    }

    private boolean hasLegacyRows(List<Map<String, Object>> rows) {
        return rows.stream().anyMatch(row -> "LEGACY_CODE".equals(row.get("relationMode")));
    }

    private long queryCount(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> firstOrEmpty(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 100.0;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String coverageStatus(long linked, long total) {
        double coverage = percentage(linked, total);
        if (coverage >= 99.9) {
            return "HEALTHY";
        }
        if (coverage >= 95) {
            return "WARNING";
        }
        return "CRITICAL";
    }

    private static String numeric_code_key(String ignored) {
        throw new UnsupportedOperationException("SQL marker only");
    }
}
