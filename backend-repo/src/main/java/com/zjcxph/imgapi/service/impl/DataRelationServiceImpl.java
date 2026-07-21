package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DataRelationRepository;
import com.zjcxph.imgapi.service.DataRelationService;
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

    private final DataRelationRepository repository;

    public DataRelationServiceImpl(DataRelationRepository repository) {
        this.repository = repository;
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
        result.put("archiveCount", repository.countTable("mr_archive"));
        result.put("healthScore", percentage(linkedTotal, relationTotal));
        result.put("relations", relations);
        result.put("latestQualityRun", repository.latestQualityRun());
        result.put("relationChecks", repository.latestRelationChecks());
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
            case "ARCHIVE_ID" -> repository.searchByArchiveId(parseArchiveId(searchValue));
            case "BAH" -> repository.searchByCode("bah", searchValue, safeLimit);
            case "SJH" -> repository.searchByCode("sjh", searchValue, safeLimit);
            default -> throw new BusinessException(400, "查询类型仅支持 ARCHIVE_ID、BAH 或 SJH");
        };
    }

    @Override
    public Map<String, Object> getArchiveRelation(long archiveId) {
        Map<String, Object> archive = repository.findArchive(archiveId);
        if (archive == null) {
            throw new BusinessException(404, "病案主档不存在");
        }

        String bah = DataRelationComparisonUtils.normalizeText(archive.get("bah"));
        String sjh = DataRelationComparisonUtils.normalizeText(archive.get("sjh"));
        List<Map<String, Object>> statistics = repository.relatedStatistics(archiveId, bah, sjh);
        List<Map<String, Object>> patients = repository.relatedPatients(bah);
        List<Map<String, Object>> boxes = repository.relatedBoxes(archiveId, bah, sjh);
        Map<String, Object> rawScanSummary = repository.scanSummary(archiveId, bah, sjh);
        Map<String, Object> scanSummary = normalizeScanSummary(rawScanSummary);
        List<Map<String, Object>> scanSamples = repository.scanSamples(archiveId);
        Map<String, Object> migrationSummary = repository.migrationSummary(archiveId);
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
            warnings.add("发现编号完全相同但 archive_id 为空的扫描记录，需要进入异常中心确认关联");
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
        Map<String, Object> counts = repository.archiveCoverage(tableName);
        long total = number(counts.get("total_count"));
        long linked = number(counts.get("linked_count"));
        return relationOverview(tableName, label, relation, total, linked, false, "EXACT", true);
    }

    private Map<String, Object> estimatedScanCoverage() {
        List<Map<String, Object>> rows = repository.estimatedScanCoverage();
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
        long total = repository.countTable("mr_patient");
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

    private Map<String, Object> normalizeScanSummary(Map<String, Object> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeCount", number(raw.get("active_count")));
        result.put("deletedCount", number(raw.get("deleted_count")));
        result.put("distinctPageCount", number(raw.get("distinct_page_count")));
        result.put("minPage", raw.get("min_page"));
        result.put("maxPage", raw.get("max_page"));
        result.put("duplicatePageCount", Math.max(0, number(raw.get("duplicate_page_count"))));
        result.put("ossCount", number(raw.get("oss_count")));
        result.put("unlinkedCandidateCount", number(raw.get("unlinked_candidate_count")));
        return result;
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

    private long parseArchiveId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "archive_id 必须是整数");
        }
    }

    private boolean hasLegacyRows(List<Map<String, Object>> rows) {
        return rows.stream().anyMatch(row -> "LEGACY_CODE".equals(row.get("relationMode")));
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
}
