package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.StatisticsDataExchangeService;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
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
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Import({
        TabularImportFileReader.class,
        StatisticsDataExchangeService.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("病案号与上架号导入边界 PostgreSQL 16 集成测试")
class MedicalRecordImportBoundaryPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StatisticsDataExchangeService statisticsDataExchangeService;

    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("""
                truncate table app.mr_archive_box_record, app.mr_scan,
                               app.mr_statistics, app.mr_archive
                restart identity cascade
                """);
    }

    @Test
    @DisplayName("高位病案号必须提供上架号且校验失败时不写入数据库")
    void requiresSjhForHighMedicalRecordNumberWithoutPartialWrites() throws Exception {
        DataExchangeImportResult lowBah = statisticsDataExchangeService.importStatistics(
                statisticsCsv("low-bah.csv", "9999999", ""),
                false
        );

        assertThat(lowBah.canImport()).isTrue();
        assertThat(lowBah.insertedRows()).isEqualTo(1);
        assertThat(count("select count(*) from app.mr_statistics")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select bah from app.mr_statistics",
                String.class
        )).isEqualTo("9999999");

        DataExchangeImportResult rejectedHighBah = statisticsDataExchangeService.importStatistics(
                statisticsCsv("high-bah-missing-sjh.csv", "10000000", ""),
                false
        );

        assertThat(rejectedHighBah.canImport()).isFalse();
        assertThat(rejectedHighBah.errorRows()).isEqualTo(1);
        assertThat(rejectedHighBah.errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("sjh");
            assertThat(error.message()).contains("必须同时提供上架号");
            assertThat(error.value()).isEqualTo("10000000");
        });
        assertThat(count("select count(*) from app.mr_statistics")).isEqualTo(1);
        assertThat(count("select count(*) from app.mr_archive")).isEqualTo(1);

        DataExchangeImportResult acceptedHighBah = statisticsDataExchangeService.importStatistics(
                statisticsCsv("high-bah-with-sjh.csv", "10000000", "20000001"),
                false
        );

        assertThat(acceptedHighBah.canImport()).isTrue();
        assertThat(acceptedHighBah.insertedRows()).isEqualTo(1);
        assertThat(count("select count(*) from app.mr_statistics")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_archive")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app.mr_statistics where bah = '10000000' and sjh = '20000001'",
                Long.class
        )).isEqualTo(1L);
    }

    private MockMultipartFile statisticsCsv(String fileName, String bah, String sjh) {
        String content = """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                %s,C01,U01,2026-01-02,住院病案,12,%s,测试患者,测试科室,P001,2026-01-05
                """.formatted(bah, sjh);
        return new MockMultipartFile(
                "file",
                fileName,
                "text/csv",
                content.stripIndent().getBytes(StandardCharsets.UTF_8)
        );
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
