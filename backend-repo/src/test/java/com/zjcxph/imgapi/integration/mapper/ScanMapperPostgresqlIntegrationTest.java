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

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ScanMapper PostgreSQL 16 + Flyway 集成测试")
class ScanMapperPostgresqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imgapi")
                    .withUsername("imgapi")
                    .withPassword("imgapi");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?currentSchema=app");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("spring.sql.init.mode", () -> "never");
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
    @DisplayName("通过真实 PostgreSQL 执行 ScanMapper 模糊查询")
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

        List<Scan> results = scanMapper.findByCondition(request);

        assertThat(results).singleElement().satisfies(scan -> {
            assertThat(scan.getBah()).isEqualTo("00789508");
            assertThat(scan.getFilename()).isEqualTo("test.jpg");
        });
    }
}
