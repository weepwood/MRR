package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("病案编号比较键 PostgreSQL 16 集成测试")
class MedicalRecordCodeMatchKeyPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("短编号和前导零编号生成相同比较键")
    void normalizesOnlyForComparison() {
        String shortCode = jdbcTemplate.queryForObject(
                "SELECT app.numeric_code_key('123')",
                String.class
        );
        String paddedCode = jdbcTemplate.queryForObject(
                "SELECT app.numeric_code_key('00000123')",
                String.class
        );
        String nonNumericCode = jdbcTemplate.queryForObject(
                "SELECT app.numeric_code_key(' SJH-A01 ')",
                String.class
        );

        assertThat(shortCode).isEqualTo("123");
        assertThat(paddedCode).isEqualTo("123");
        assertThat(nonNumericCode).isEqualTo("SJH-A01");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    @DisplayName("可控规模业务表建立编号比较键索引")
    void createsMatchKeyIndexesOutsideLargeScanTable() {
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'app'
                  AND indexname IN (
                    'idx_mr_archive_bah_match_key',
                    'idx_mr_archive_sjh_match_key',
                    'idx_mr_statistics_bah_match_key',
                    'idx_mr_statistics_sjh_match_key',
                    'idx_mr_patient_bah_match_key',
                    'idx_archive_box_bah_match_key',
                    'idx_archive_box_sjh_match_key'
                  )
                """, Integer.class);
        Integer scanIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'app'
                  AND indexname = 'idx_mr_scan_code_match_key'
                """, Integer.class);

        assertThat(indexCount).isEqualTo(7);
        assertThat(scanIndexCount).isZero();
    }
}
