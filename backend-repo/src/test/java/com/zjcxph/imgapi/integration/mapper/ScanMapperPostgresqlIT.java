package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.Scan;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "mybatis.configuration.map-underscore-to-camel-case=true",
        "mybatis.mapper-locations=classpath*:mapper/*.xml"
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ScanMapper PostgreSQL 16 + Flyway 集成测试")
class ScanMapperPostgresqlIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imgapi")
                    .withUsername("imgapi")
                    .withPassword("imgapi");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> appendJdbcParameter(
                POSTGRES.getJdbcUrl(), "currentSchema=app"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    private static String appendJdbcParameter(String jdbcUrl, String parameter) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + parameter;
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScanMapper scanMapper;

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
}
