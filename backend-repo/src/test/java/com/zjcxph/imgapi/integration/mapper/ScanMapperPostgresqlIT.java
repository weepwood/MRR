package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ScanMapper PostgreSQL 16 + Flyway 集成测试")
class ScanMapperPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScanMapper scanMapper;

    @Autowired
    private MigrationJobMapper migrationJobMapper;

    @Test
    @DisplayName("在 PostgreSQL 16 上应用全部 Flyway 迁移")
    void appliesAllFlywayMigrationsOnPostgresql16() {
        String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(serverVersion).startsWith("16.");
        assertThat(applied).isNotEmpty().allSatisfy(migration ->
                assertThat(migration.getState().isApplied()).isTrue());
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("通过真实 PostgreSQL 执行有限 ScanMapper 模糊查询")
    void queriesScanWithPostgresqlConcatenationOperator() {
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, btype, pages, openerno, uploaddate, uploadflag, folder)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "605746", "00789508", "SJH001", "test.jpg", 1, 2,
                "OP001", "2026-07-13", 1, "25.03.15");

        ScanRequest request = new ScanRequest();
        request.setBah("7895");

        List<Scan> results = scanMapper.findByCondition(request, 100);

        assertThat(results).singleElement().satisfies(scan -> {
            assertThat(scan.getBah()).isEqualTo("00789508");
            assertThat(scan.getFilename()).isEqualTo("test.jpg");
        });
    }

    @Test
    @DisplayName("游标查询仅返回 afterId 之后的记录")
    void queriesScanAfterId() {
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (brxh, bah, filename, pages, uploadflag, folder)
                VALUES
                    ('1', '00000001', '1.jpg', 1, 1, '25.03.15'),
                    ('2', '00000002', '2.jpg', 2, 1, '25.03.15')
                """);
        Integer firstId = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM app.mr_scan WHERE bah IN ('00000001', '00000002')",
                Integer.class
        );

        List<Scan> results = scanMapper.findAfterId(firstId, 10);

        assertThat(results).extracting(Scan::getBah).contains("00000002").doesNotContain("00000001");
    }

    @Test
    @DisplayName("通过 mr_archive 解析 archive_id 并只读取有效影像")
    void queriesActiveScansByResolvedArchiveId() {
        Long archiveId = jdbcTemplate.queryForObject("""
                INSERT INTO app.mr_archive (bah, sjh)
                VALUES ('00789508', '00000123')
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (archive_id, brxh, bah, sjh, filename, pages, uploadflag, folder)
                VALUES
                    (?, '605746', '00789508', '00000123', 'active.jpg', 2, 1, '25.03.15'),
                    (?, '605746', '00789508', '00000123', 'deleted.jpg', 1, 0, '25.03.15')
                """, archiveId, archiveId);

        Long resolvedArchiveId = scanMapper.resolveArchiveId("00789508", "");
        List<Scan> results = scanMapper.findActiveByArchiveId(resolvedArchiveId);

        assertThat(resolvedArchiveId).isEqualTo(archiveId);
        assertThat(results).singleElement().satisfies(scan -> {
            assertThat(scan.getArchiveId()).isEqualTo(archiveId);
            assertThat(scan.getFilename()).isEqualTo("active.jpg");
            assertThat(scan.getUploadFlag()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("主档保留短编号时可通过兼容搜索词进入快速路径")
    void resolvesArchiveIdAcrossPaddingFormats() {
        Long archiveId = jdbcTemplate.queryForObject("""
                INSERT INTO app.mr_archive (bah)
                VALUES ('54321')
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (archive_id, brxh, bah, filename, pages, uploadflag, folder)
                VALUES (?, '605746', '54321', 'mixed-format.jpg', 1, 1, '25.03.15')
                """, archiveId);

        Long exactArchiveId = scanMapper.resolveArchiveId("00054321", "");
        Long compatibleArchiveId = scanMapper.resolveArchiveIdBySearchCode("54321", "");
        List<Scan> results = scanMapper.findActiveByArchiveId(compatibleArchiveId);

        assertThat(exactArchiveId).isNull();
        assertThat(compatibleArchiveId).isEqualTo(archiveId);
        assertThat(results).extracting(Scan::getFilename)
                .containsExactly("mixed-format.jpg");
    }

    @Test
    @DisplayName("兼容查询排除已经软删除的影像")
    void legacyLookupExcludesSoftDeletedScans() {
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (brxh, bah, filename, pages, uploadflag, folder)
                VALUES
                    ('605746', '00990001', 'active-legacy.jpg', 2, 1, '25.03.15'),
                    ('605746', '00990001', 'deleted-legacy.jpg', 1, 0, '25.03.15')
                """);

        List<Scan> results = scanMapper.findByCode("00990001", "990001", "", "");

        assertThat(results).extracting(Scan::getFilename)
                .containsExactly("active-legacy.jpg");
    }

    @Test
    @DisplayName("迁移统计区分待迁移、等待上架号和失败重试")
    void migrationStatsUseOssKeyAndSjhAsSourceOfTruth() {
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder, oss_url, migration_status)
                VALUES
                    ('1', '00991001', '11110001', 'migrated.jpg', 1, 1, '25.03.15',
                     'medical-records/1111/11110001-00991001/migrated.jpg', NULL),
                    ('2', '00991002', '22220002', 'retry.jpg', 1, 1, '25.03.15', NULL, 'retry_wait'),
                    ('3', '00991003', '33330003', 'failed.jpg', 1, 1, '25.03.15', NULL, 'failed'),
                    ('4', '00991004', '44440004', 'pending.jpg', 1, 1, '25.03.15', NULL, NULL),
                    ('5', '00991005', NULL, 'waiting.jpg', 1, 1, '25.03.15', NULL, NULL)
                """);

        Map<String, Object> stats = scanMapper.countMigrationStats();

        assertThat(((Number) stats.get("total")).longValue()).isEqualTo(5);
        assertThat(((Number) stats.get("migrated")).longValue()).isEqualTo(1);
        assertThat(((Number) stats.get("failed")).longValue()).isEqualTo(1);
        assertThat(((Number) stats.get("retry_wait")).longValue()).isEqualTo(1);
        assertThat(((Number) stats.get("pending")).longValue()).isEqualTo(2);
        assertThat(((Number) stats.get("waiting_sjh")).longValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("原子领取跳过已迁移、未到期重试和缺少上架号记录")
    void atomicClaimOnlyUpdatesEligibleRows() {
        Integer migratedId = jdbcTemplate.queryForObject("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder, oss_url, migration_status)
                VALUES
                    ('1', '00992001', '11110001', 'migrated.jpg', 1, 1, '25.03.15',
                     'medical-records/existing.jpg', 'not_migrated')
                RETURNING id
                """, Integer.class);
        Integer futureRetryId = jdbcTemplate.queryForObject("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder, migration_status, migration_next_retry_at)
                VALUES
                    ('2', '00992002', '22220002', 'retry.jpg', 1, 1, '25.03.15',
                     'retry_wait', NOW() + INTERVAL '10 minutes')
                RETURNING id
                """, Integer.class);
        Integer waitingSjhId = jdbcTemplate.queryForObject("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder, migration_status)
                VALUES
                    ('3', '00992003', NULL, 'waiting.jpg', 1, 1, '25.03.15', 'waiting_sjh')
                RETURNING id
                """, Integer.class);

        assertThat(scanMapper.markMigrationStarted(migratedId)).isZero();
        assertThat(scanMapper.markMigrationStarted(futureRetryId)).isZero();
        assertThat(scanMapper.markMigrationStarted(waitingSjhId)).isZero();

        jdbcTemplate.update("UPDATE app.mr_scan SET sjh = '33330003' WHERE id = ?", waitingSjhId);
        assertThat(scanMapper.markMigrationStarted(waitingSjhId)).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT migration_status FROM app.mr_scan WHERE id = ?",
                String.class,
                migratedId
        )).isEqualTo("not_migrated");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT migration_status FROM app.mr_scan WHERE id = ?",
                String.class,
                futureRetryId
        )).isEqualTo("retry_wait");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT migration_status FROM app.mr_scan WHERE id = ?",
                String.class,
                waitingSjhId
        )).isEqualTo("migrating");
    }

    @Test
    @DisplayName("进度更新不会覆盖并发写入的取消状态")
    void progressUpdatePreservesCancellationState() {
        MigrationJob job = new MigrationJob();
        job.setStatus("pending");
        job.setMode("pilot");
        job.setRequestedCount(10L);
        job.setMaxScanId(100);
        job.setCancelRequested(false);
        job.setTotalCount(10L);
        job.setProcessedCount(0L);
        job.setFailedCount(0L);
        job.setRate(BigDecimal.ZERO);
        job.setCreatedBy("test");
        migrationJobMapper.insert(job);

        assertThat(migrationJobMapper.markRunning(job.getId(), new Date())).isEqualTo(1);
        assertThat(migrationJobMapper.requestCancel(job.getId())).isEqualTo(1);
        migrationJobMapper.updateProgress(job.getId(), 3, 1, new BigDecimal("30.00"));

        MigrationJob updated = migrationJobMapper.findById(job.getId());
        assertThat(updated.getStatus()).isEqualTo("cancelling");
        assertThat(updated.getCancelRequested()).isTrue();
        assertThat(updated.getProcessedCount()).isEqualTo(3);
        assertThat(updated.getFailedCount()).isEqualTo(1);

        migrationJobMapper.complete(
                job.getId(),
                "completed",
                10,
                3,
                1,
                new BigDecimal("30.00"),
                null,
                new Date()
        );
        MigrationJob completed = migrationJobMapper.findById(job.getId());
        assertThat(completed.getStatus()).isEqualTo("cancelled");
        assertThat(completed.getCancelRequested()).isTrue();
    }
}
