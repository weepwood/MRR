package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.OssMigrationManagementMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("OSS 迁移管理 Mapper PostgreSQL 16 集成测试")
class OssMigrationManagementMapperPostgresqlIT {

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
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO app, public");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OssMigrationManagementMapper mapper;

    @Test
    @DisplayName("待迁移和等待上架号列表支持精确条件筛选")
    void filtersPendingAndWaitingRecords() {
        jdbcTemplate.update("""
                INSERT INTO app.mr_scan
                    (brxh, bah, sjh, filename, pages, uploadflag, folder, migration_status)
                VALUES
                    ('1', '00993001', '55550001', 'pending-management.jpg', 1, 1, '25.03.15', NULL),
                    ('2', '00993002', NULL, 'waiting-management.jpg', 1, 1, '25.03.15', 'waiting_sjh'),
                    ('3', '00993003', '66660003', 'other-folder.jpg', 1, 1, '25.03.16', NULL),
                    ('4', '00993004', '77770004', 'future-retry.jpg', 1, 1, '25.03.15', 'retry_wait')
                """);
        jdbcTemplate.update("""
                UPDATE app.mr_scan
                SET migration_next_retry_at = NOW() + INTERVAL '10 minutes'
                WHERE filename = 'future-retry.jpg'
                """);

        List<Scan> pending = mapper.findPending(
                "25.03.15",
                "00993001",
                "55550001",
                10
        );
        List<Scan> waiting = mapper.findWaitingSjh(
                "25.03.15",
                "00993002",
                null,
                10
        );

        assertThat(pending).extracting(Scan::getFilename)
                .containsExactly("pending-management.jpg");
        assertThat(waiting).extracting(Scan::getFilename)
                .containsExactly("waiting-management.jpg");
    }

    @Test
    @DisplayName("迁移日志支持状态和 Scan ID 联合筛选")
    void filtersLogsByStatusAndScanId() {
        jdbcTemplate.update("""
                INSERT INTO app.image_migration_log
                    (scan_id, local_path, migration_status, error_message)
                VALUES
                    (88, 'NGINX:scan:88', 'failed', 'missing'),
                    (88, 'NGINX:scan:88', 'retry_wait', 'timeout'),
                    (99, 'NGINX:scan:99', 'failed', 'conflict')
                """);

        List<ImageMigrationLog> logs = mapper.findLogs("failed", 88, 0, 20);
        long total = mapper.countLogs("failed", 88);

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getScanId()).isEqualTo(88);
        assertThat(logs.getFirst().getMigrationStatus()).isEqualTo("failed");
        assertThat(total).isEqualTo(1L);
    }
}
