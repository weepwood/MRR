package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.ArchiveBoxDataExchangeService;
import com.zjcxph.imgapi.service.DataExchangeExportService;
import com.zjcxph.imgapi.service.PatientImportService;
import com.zjcxph.imgapi.service.ScanDataExchangeService;
import com.zjcxph.imgapi.service.StatisticsDataExchangeService;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@Import({TabularImportFileReader.class, PatientImportService.class, StatisticsDataExchangeService.class,
        ArchiveBoxDataExchangeService.class, ScanDataExchangeService.class, DataExchangeExportService.class})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataExchangeValidationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    private static final String PATIENT_HEADERS = "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\n";
    private static final String STATISTICS_HEADERS =
            "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate\n";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PatientImportService patients;

    /**
     * 这些用例只验证导入校验与回滚，不关心查询缓存；用 mock 代替真实 CacheManager，
     * 避免在导入流程中启用缓存切面。
     */
    @MockitoBean private org.springframework.cache.CacheManager cacheManager;
    @Autowired private StatisticsDataExchangeService statistics;
    @Autowired private ArchiveBoxDataExchangeService boxes;
    @Autowired private ScanDataExchangeService scans;
    @Autowired private DataExchangeExportService exports;

    @BeforeEach
    void clearData() {
        jdbc.execute("truncate app.mr_patient, app.mr_scan, app.mr_archive_box_record, "
                + "app.mr_statistics, app.mr_archive restart identity cascade");
    }

    @Test
    void patientExportCanBeReimportedWithoutChangingIdentifiersOrText() throws Exception {
        String bah = "00000123";
        String syntheticId = "990000200001010001";
        String department = "合成科室,\"甲\"\n第二行";
        jdbc.update("insert into app.mr_patient (bah, name, idcard, department) values (?, ?, ?, ?)",
                bah, "=1+2", syntheticId, department);
        StringWriter output = new StringWriter();
        exports.exportPatients(null, output);
        assertThat(output.toString()).contains("'00000123", "'990000200001010001", "'=1+2");
        jdbc.update("delete from app.mr_patient");

        var preview = patients.importPatients(csv(output.toString()), true);
        assertThat(preview.canImport()).isTrue();
        assertThat(count("mr_patient")).isZero();
        var imported = patients.importPatients(csv(output.toString()), false);
        var repeated = patients.importPatients(csv(output.toString()), false);
        assertThat(imported.insertedRows()).isEqualTo(1);
        assertThat(repeated.insertedRows()).isZero();
        assertThat(repeated.duplicateRows()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select bah, name, idcard, department from app.mr_patient"))
                .containsEntry("bah", bah).containsEntry("name", "=1+2")
                .containsEntry("idcard", syntheticId).containsEntry("department", department);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEntirePatientFileWithInvalidDate(boolean dryRun) throws Exception {
        var result = patients.importPatients(csv(PATIENT_HEADERS
                + "TEST001,合成患者,,2024-02-29,2024-02-29 12:00,,,\n"
                + "TEST002,合成患者,,2026-02-29,2026-01-01 24:00,,,\n"), dryRun);
        assertThat(result.canImport()).isFalse();
        assertThat(result.errors()).extracting("field").contains("ruyuan", "admissiontime");
        assertThat(count("mr_patient")).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsEntireStatisticsFileWithInvalidDate(boolean dryRun) throws Exception {
        var result = statistics.importStatistics(csv(STATISTICS_HEADERS
                + "101,C01,U01,2024-02-29,合成病案,1,901,合成患者,,,\n"
                + "102,C01,U01,2026-02-30,合成病案,1,902,合成患者,,,\n"), dryRun);
        assertThat(result.canImport()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.field()).isEqualTo("date");
        });
        assertThat(count("mr_statistics")).isZero();
        assertThat(count("mr_archive")).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsExtraColumnsForEveryImportWithoutWriting(boolean dryRun) {
        assertThatThrownBy(() -> patients.importPatients(csv(PATIENT_HEADERS
                + "TEST001,合成患者,,,,,,,extra\n"), dryRun)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> statistics.importStatistics(csv(STATISTICS_HEADERS
                + "101,C01,U01,2024-02-29,合成病案,1,901,合成患者,,,,extra\n"), dryRun))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> boxes.importArchiveBoxes(csv(
                "bah,sjh,box_no,expected_box_no,status,remark\n101,901,A,A,NORMAL,,extra\n"), dryRun))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scans.importScans(csv(
                "sjh,bah,brxh,folder,filename,btype,filesize\n901,101,,synthetic,1.jpg,0,10,extra\n"), dryRun))
                .isInstanceOf(IllegalArgumentException.class);
        for (String table : new String[]{"mr_patient", "mr_statistics", "mr_archive_box_record", "mr_scan", "mr_archive"}) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from app." + table, Long.class);
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "synthetic.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
