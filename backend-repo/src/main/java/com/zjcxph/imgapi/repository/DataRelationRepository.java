package com.zjcxph.imgapi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DataRelationRepository {

    private static final int DETAIL_SAMPLE_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public DataRelationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> archiveCoverage(String tableName) {
        if (!List.of("mr_statistics", "mr_archive_box_record").contains(tableName)) {
            throw new IllegalArgumentException("unsupported relation table: " + tableName);
        }
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS total_count, COUNT(archive_id) AS linked_count FROM app." + tableName
        );
    }

    public List<Map<String, Object>> estimatedScanCoverage() {
        return jdbcTemplate.queryForList("""
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
    }

    public long countTable(String tableName) {
        if (!List.of("mr_archive", "mr_patient").contains(tableName)) {
            throw new IllegalArgumentException("unsupported count table: " + tableName);
        }
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app." + tableName, Long.class);
        return value == null ? 0 : value;
    }

    public Map<String, Object> latestQualityRun() {
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

    public List<Map<String, Object>> latestRelationChecks() {
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

    public List<Map<String, Object>> searchByArchiveId(long archiveId) {
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

    public List<Map<String, Object>> searchByCode(String column, String value, int limit) {
        if (!List.of("bah", "sjh").contains(column)) {
            throw new IllegalArgumentException("unsupported code column: " + column);
        }
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
                   OR app.numeric_code_key(a.%1$s) = app.numeric_code_key(i.code)
                ORDER BY CASE WHEN BTRIM(COALESCE(a.%1$s, '')) = i.code THEN 0 ELSE 1 END,
                         a.id DESC
                LIMIT ?
                """.formatted(column);
        return jdbcTemplate.queryForList(sql, value, limit);
    }

    public Map<String, Object> findArchive(long archiveId) {
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
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> relatedStatistics(long archiveId, String bah, String sjh) {
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
                            (i.sjh IS NOT NULL AND app.numeric_code_key(s.sjh) = app.numeric_code_key(i.sjh))
                            OR (i.sjh IS NULL AND i.bah IS NOT NULL
                                AND app.numeric_code_key(s.bah) = app.numeric_code_key(i.bah))
                        )
                   )
                ORDER BY CASE WHEN s.archive_id = ? THEN 0 ELSE 1 END, s.id DESC
                LIMIT ?
                """, bah, sjh, archiveId, archiveId, archiveId, DETAIL_SAMPLE_LIMIT);
    }

    public List<Map<String, Object>> relatedPatients(String bah) {
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
                WHERE app.numeric_code_key(p.bah) = app.numeric_code_key(i.bah)
                ORDER BY p.id DESC
                LIMIT ?
                """, bah, DETAIL_SAMPLE_LIMIT);
    }

    public List<Map<String, Object>> relatedBoxes(long archiveId, String bah, String sjh) {
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
                            (i.sjh IS NOT NULL AND app.numeric_code_key(b.sjh) = app.numeric_code_key(i.sjh))
                            OR (i.sjh IS NULL AND i.bah IS NOT NULL
                                AND app.numeric_code_key(b.bah) = app.numeric_code_key(i.bah))
                        )
                   )
                ORDER BY CASE WHEN b.archive_id = ? THEN 0 ELSE 1 END,
                         b.updated_at DESC, b.id DESC
                LIMIT ?
                """, bah, sjh, archiveId, archiveId, archiveId, DETAIL_SAMPLE_LIMIT);
    }

    public Map<String, Object> scanSummary(long archiveId, String bah, String sjh) {
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE uploadflag <> 0) AS active_count,
                       COUNT(*) FILTER (WHERE uploadflag = 0) AS deleted_count,
                       COUNT(DISTINCT pages) FILTER (WHERE uploadflag <> 0) AS distinct_page_count,
                       MIN(pages) FILTER (WHERE uploadflag <> 0) AS min_page,
                       MAX(pages) FILTER (WHERE uploadflag <> 0) AS max_page,
                       COUNT(*) FILTER (WHERE uploadflag <> 0) -
                           COUNT(DISTINCT pages) FILTER (WHERE uploadflag <> 0) AS duplicate_page_count,
                       COUNT(*) FILTER (
                           WHERE uploadflag <> 0
                             AND NULLIF(BTRIM(COALESCE(oss_url, '')), '') IS NOT NULL
                       ) AS oss_count
                FROM app.mr_scan
                WHERE archive_id = ?
                """, archiveId);

        Long unlinkedCandidates = jdbcTemplate.queryForObject("""
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
                        (i.sjh IS NOT NULL AND BTRIM(COALESCE(s.sjh, '')) = i.sjh)
                        OR (i.sjh IS NULL AND i.bah IS NOT NULL
                            AND BTRIM(COALESCE(s.bah, '')) = i.bah)
                  )
                """, Long.class, bah, sjh);
        summary.put("unlinked_candidate_count", unlinkedCandidates == null ? 0L : unlinkedCandidates);
        return summary;
    }

    public List<Map<String, Object>> scanSamples(long archiveId) {
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

    public Map<String, Object> migrationSummary(long archiveId) {
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
}
