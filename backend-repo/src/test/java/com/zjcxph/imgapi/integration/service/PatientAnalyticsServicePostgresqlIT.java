package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.resp.PatientAnalyticsSummary;
import com.zjcxph.imgapi.dto.resp.PatientMultiRecordGroup;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.PatientAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
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
@Import(PatientAnalyticsService.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("患者统计 PostgreSQL 16 + Flyway 集成测试")
class PatientAnalyticsServicePostgresqlIT {

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
    private PatientAnalyticsService patientAnalyticsService;

    @BeforeEach
    void resetPatients() {
        jdbcTemplate.execute("truncate table app.mr_patient restart identity");
        jdbcTemplate.update("""
                insert into app.mr_patient
                    (bah, name, idcard, ruyuan, admissiontime, department, bingqu, chuangwei)
                values
                    ('00000001', '张三', '330101199001010011', date '2026-01-01', null, '内科', '一病区', '01'),
                    ('00000002', '张三', '330101199001010011', date '2026-01-02', null, '外科', '二病区', '02'),
                    ('00000003', '李四', null, null, '2026-02-03 10:00:00', '内科', '一病区', '03'),
                    ('00000004', '李四', '   ', null, '2026-02-04 11:00:00', '内科', '一病区', '04'),
                    ('00000005', '王五', '330101199002020022', date '2025-12-31', null, '儿科', '三病区', '05')
                """);
    }

    @Test
    @DisplayName("按年度统计身份证缺失、日期趋势和科室分布")
    void summarizesPatientDataQualityAndYearDistribution() {
        PatientAnalyticsSummary summary = patientAnalyticsService.getSummary(2026);

        assertThat(summary.totalRecords()).isEqualTo(5);
        assertThat(summary.totalArchives()).isEqualTo(5);
        assertThat(summary.yearArchives()).isEqualTo(4);
        assertThat(summary.missingIdCardRecords()).isEqualTo(2);
        assertThat(summary.confirmedMultiRecordGroups()).isEqualTo(1);
        assertThat(summary.suspectedMultiRecordGroups()).isEqualTo(1);
        assertThat(summary.dateCounts())
                .filteredOn(item -> item.count() > 0)
                .extracting(PatientAnalyticsSummary.DateCount::date, PatientAnalyticsSummary.DateCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("2026-01-01", 1L),
                        org.assertj.core.groups.Tuple.tuple("2026-01-02", 1L),
                        org.assertj.core.groups.Tuple.tuple("2026-02-03", 1L),
                        org.assertj.core.groups.Tuple.tuple("2026-02-04", 1L)
                );
        assertThat(summary.departmentCounts())
                .extracting(PatientAnalyticsSummary.DepartmentCount::department, PatientAnalyticsSummary.DepartmentCount::count)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("内科", 3L),
                        org.assertj.core.groups.Tuple.tuple("外科", 1L)
                );
    }

    @Test
    @DisplayName("返回身份证缺失明细和同一患者多病案分组")
    void listsMissingIdCardsAndMultiRecordGroups() {
        List<Patient> missingRecords = patientAnalyticsService.findMissingIdCardRecords(1, 20);
        List<PatientMultiRecordGroup> groups = patientAnalyticsService.findMultiRecordGroups(1, 20, true);

        assertThat(missingRecords).extracting(Patient::getBah)
                .containsExactly("00000004", "00000003");
        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst()).satisfies(group -> {
            assertThat(group.matchType()).isEqualTo("IDCARD");
            assertThat(group.maskedIdCard()).isEqualTo("330***********0011");
            assertThat(group.archiveNumbers()).containsExactly("00000001", "00000002");
        });
        assertThat(groups.getLast()).satisfies(group -> {
            assertThat(group.matchType()).isEqualTo("NAME_ONLY");
            assertThat(group.patientName()).isEqualTo("李四");
            assertThat(group.archiveNumbers()).containsExactly("00000003", "00000004");
        });
    }
}
