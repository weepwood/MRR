package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 病案影像表编号索引就绪检查。
 *
 * <p>这些索引由 {@code backend-repo/scripts/build-code-key-indexes.sql} 在批准的
 * 副本窗口人工执行，不随 Flyway 自动创建。因此"缺索引"在运行时不报错，只会让
 * 按编号查询退化成全表扫描——必须由就绪检查主动暴露，否则运维无从发现。</p>
 */
class ScanCodeKeyIndexReadinessCheckTest {

    private static final long LARGE_TABLE_ROWS = 30_000_000L;

    private static DeploymentReadinessService service(JdbcTemplate jdbcTemplate) {
        return new DeploymentReadinessService(
                jdbcTemplate,
                mock(Flyway.class),
                mock(ImageProperties.class),
                mock(ArchiveExportProperties.class),
                mock(ImageUrlService.class),
                mock(OssService.class),
                mock(SystemSettingService.class),
                0L,
                48L,
                1L,
                1_000_000L
        );
    }

    private static JdbcTemplate jdbcWithIndexes(List<Map<String, Object>> indexes, long estimatedRows) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("idx_mr_scan_bah_numeric_cursor")))
                .thenReturn(indexes);
        when(jdbcTemplate.queryForList(contains("reltuples")))
                .thenReturn(List.of(Map.of("estimated_rows", estimatedRows)));
        return jdbcTemplate;
    }

    private static Map<?, ?> detailsOf(Map<String, Object> check) {
        return (Map<?, ?>) check.get("details");
    }

    @Test
    void reportsWarningWhenLargeScanTableHasNoCodeKeyIndexes() {
        Map<String, Object> check = service(jdbcWithIndexes(List.of(), LARGE_TABLE_ROWS))
                .checkScanCodeKeyIndexes();

        assertEquals("scan-code-key-indexes", check.get("code"));
        assertEquals("WARNING", check.get("severity"));
        assertFalse((Boolean) check.get("passed"), "三千万行缺失索引必须判为未通过");
        assertEquals("MISSING", detailsOf(check).get("state"));
    }

    @Test
    void detailsListEveryMissingIndexSoOperationsKnowWhatToBuild() {
        Map<String, Object> check = service(jdbcWithIndexes(List.of(), LARGE_TABLE_ROWS))
                .checkScanCodeKeyIndexes();

        Map<?, ?> details = detailsOf(check);
        assertEquals(
                List.of("idx_mr_scan_bah_numeric_cursor", "idx_mr_scan_sjh_numeric_cursor"),
                details.get("missing")
        );
        assertEquals(LARGE_TABLE_ROWS, details.get("estimatedRows"));
        assertEquals("scripts/build-code-key-indexes.sql", details.get("script"));
    }

    @Test
    void partialIndexCoverageStillFails() {
        Map<String, Object> check = service(jdbcWithIndexes(
                List.of(Map.of("index_name", "idx_mr_scan_bah_numeric_cursor", "valid", true)),
                LARGE_TABLE_ROWS
        )).checkScanCodeKeyIndexes();

        assertFalse((Boolean) check.get("passed"));
        assertEquals(List.of("idx_mr_scan_sjh_numeric_cursor"), detailsOf(check).get("missing"));
    }

    @Test
    void invalidIndexCountsAsMissing() {
        Map<String, Object> check = service(jdbcWithIndexes(
                List.of(
                        Map.of("index_name", "idx_mr_scan_bah_numeric_cursor", "valid", false),
                        Map.of("index_name", "idx_mr_scan_sjh_numeric_cursor", "valid", true)
                ),
                LARGE_TABLE_ROWS
        )).checkScanCodeKeyIndexes();

        assertFalse((Boolean) check.get("passed"), "建到一半失败的索引与缺失等价，都会让查询走全表扫描");
        assertEquals(List.of("idx_mr_scan_bah_numeric_cursor"), detailsOf(check).get("invalid"));
    }

    @Test
    void passesWhenBothIndexesArePresentAndValid() {
        Map<String, Object> check = service(jdbcWithIndexes(
                List.of(
                        Map.of("index_name", "idx_mr_scan_bah_numeric_cursor", "valid", true),
                        Map.of("index_name", "idx_mr_scan_sjh_numeric_cursor", "valid", true)
                ),
                LARGE_TABLE_ROWS
        )).checkScanCodeKeyIndexes();

        assertTrue((Boolean) check.get("passed"));
        assertEquals("READY", detailsOf(check).get("state"));
        assertEquals("WARNING", check.get("severity"), "索引缺失不应阻断启动，只做告警");
    }

    @Test
    void smallScanTableIsNotFlaggedBecauseFullScanIsCheap() {
        Map<String, Object> check = service(jdbcWithIndexes(List.of(), 5_000L))
                .checkScanCodeKeyIndexes();

        assertTrue((Boolean) check.get("passed"), "小规模表全表扫描代价可接受，不应产生噪音告警");
        assertEquals("BELOW_THRESHOLD", detailsOf(check).get("state"));
    }

    @Test
    void estimateUsesStatisticsInsteadOfCountingTheWholeTable() {
        JdbcTemplate jdbcTemplate = jdbcWithIndexes(List.of(), LARGE_TABLE_ROWS);
        service(jdbcTemplate).checkScanCodeKeyIndexes();

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
                .queryForList(sqlCaptor.capture());
        assertTrue(
                sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("reltuples")),
                "表规模必须取自统计信息"
        );
        assertTrue(
                sqlCaptor.getAllValues().stream().noneMatch(sql -> sql.toLowerCase().contains("count(")),
                "就绪检查每 30 秒执行一次，不能对三千万行表做精确计数"
        );
    }

    @Test
    void databaseErrorsDegradeToFailedCheckInsteadOfCrashingReadiness() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("idx_mr_scan_bah_numeric_cursor")))
                .thenThrow(new IllegalStateException("connection reset"));

        Map<String, Object> check = service(jdbcTemplate).checkScanCodeKeyIndexes();

        assertFalse((Boolean) check.get("passed"));
        assertEquals("WARNING", check.get("severity"));
    }
}
