package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.OssMigrationManagementMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@DisplayName("OSS 迁移管理 Mapper PostgreSQL 16 集成测试")
class OssMigrationManagementMapperPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OssMigrationManagementMapper mapper;

    @Autowired
    private ScanMapper scanMapper;

    @Test
    @DisplayName("失败重置只改变当前永久失败且无OSS对象的记录，重复请求不再更新")
    void resetsOnlyCurrentPermanentFailures() {
        String[] statuses = {"failed", "retry_wait", "migrating", "waiting_sjh",
                "migrated", "not_migrated", null, "failed", "failed"};
        for (int index = 0; index < statuses.length; index++) {
            jdbcTemplate.update("""
                    INSERT INTO app.mr_scan
                        (id, brxh, bah, sjh, filename, pages, uploadflag, folder,
                         migration_status, migration_attempts, migration_error_code,
                         migration_next_retry_at, oss_url)
                    VALUES (?, ?, '00995001', '55550001', ?, 1, 1, '25.03.15', ?, 7,
                            'SYNTHETIC_ERROR', TIMESTAMP '2026-01-01 00:00:00', ?)
                    """, 9501 + index, String.valueOf(9501 + index), "retry-synthetic-" + index + ".jpg",
                    statuses[index], index == 7 ? "oss://synthetic-existing-object" : index == 8 ? "" : null);
        }
        List<Integer> ids = java.util.stream.IntStream.rangeClosed(9501, 9509).boxed().toList();

        assertThat(scanMapper.resetMigrationFailures(ids)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                SELECT id FROM app.mr_scan
                WHERE id BETWEEN 9501 AND 9509 AND migration_status = 'not_migrated'
                  AND migration_attempts = 0 AND migration_error_code IS NULL
                  AND migration_next_retry_at IS NULL
                ORDER BY id
                """, Integer.class)).containsExactly(9501, 9509);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM app.mr_scan
                WHERE id BETWEEN 9502 AND 9508 AND migration_attempts = 7
                  AND migration_error_code = 'SYNTHETIC_ERROR'
                  AND migration_next_retry_at = TIMESTAMP '2026-01-01 00:00:00'
                """, Integer.class)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForList("""
                SELECT migration_status FROM app.mr_scan
                WHERE id BETWEEN 9502 AND 9508 ORDER BY id
                """, String.class)).containsExactly("retry_wait", "migrating", "waiting_sjh",
                "migrated", "not_migrated", null, "failed");
        assertThat(scanMapper.resetMigrationFailures(ids)).isZero();
    }

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
                INSERT INTO app.mr_scan
                    (id, brxh, bah, sjh, filename, pages, uploadflag, folder)
                VALUES
                    (88, '88', '00994088', '88880088', 'log-88.jpg', 1, 1, '25.03.15'),
                    (99, '99', '00994099', '99990099', 'log-99.jpg', 1, 1, '25.03.15')
                """);
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
