package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.service.ArchiveBoxDataExchangeService;
import com.zjcxph.imgapi.service.DataExchangeExportService;
import com.zjcxph.imgapi.service.ScanDataExchangeService;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Import({
        TabularImportFileReader.class,
        StatisticsDataExchangeService.class,
        ArchiveBoxDataExchangeService.class,
        ScanDataExchangeService.class,
        DataExchangeExportService.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "mybatis.mapper-locations=classpath*:mapper/*.xml",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("核心数据交换 PostgreSQL 16 + Flyway 集成测试")
class CoreDataExchangePostgresqlIT {

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
        registry.add("spring.flyway.postgresql.transactional-lock", () -> false);
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StatisticsDataExchangeService statisticsDataExchangeService;

    @Autowired
    private ArchiveBoxDataExchangeService archiveBoxDataExchangeService;

    @Autowired
    private ScanDataExchangeService scanDataExchangeService;

    @Autowired
    private DataExchangeExportService exportService;

    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("""
                truncate table app.mr_archive_box_record, app.mr_scan,
                               app.mr_statistics, app.mr_archive
                restart identity cascade
                """);
    }

    @Test
    @DisplayName("统计文件先预校验再写入，并在重复执行时更新或跳过")
    void importsStatisticsIdempotently() throws Exception {
        MockMultipartFile file = csv("statistics.csv", """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U01,2026-01-02,住院病案,12,100,张三,内科,P001,2026-01-05
                2,C02,U02,2026-02-03,,8,,李四,外科,P002,2026-02-07
                """);

        DataExchangeImportResult validation = statisticsDataExchangeService.importStatistics(file, true);
        DataExchangeImportResult imported = statisticsDataExchangeService.importStatistics(file, false);
        DataExchangeImportResult repeated = statisticsDataExchangeService.importStatistics(file, false);

        assertThat(validation.canImport()).isTrue();
        assertThat(validation.insertedRows()).isEqualTo(2);
        assertThat(imported.insertedRows()).isEqualTo(2);
        assertThat(repeated.insertedRows()).isZero();
        assertThat(repeated.updatedRows()).isEqualTo(1);
        assertThat(repeated.duplicateRows()).isEqualTo(1);
        assertThat(count("select count(*) from app.mr_statistics")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_statistics where archive_id is not null")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_archive")).isEqualTo(2);
    }

    @Test
    @DisplayName("相同上架号导入新内容时更新原统计记录")
    void updatesStatisticsBySjh() throws Exception {
        statisticsDataExchangeService.importStatistics(csv("initial.csv", """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U01,2026-01-02,住院病案,12,100,张三,内科,P001,2026-01-05
                """), false);

        DataExchangeImportResult result = statisticsDataExchangeService.importStatistics(csv("update.csv", """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U02,2026-01-02,住院病案,20,100,张三,内科,P001,2026-01-05
                """), false);

        assertThat(result.updatedRows()).isEqualTo(1);
        assertThat(result.insertedRows()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select pages from app.mr_statistics where sjh = '100'",
                Integer.class
        )).isEqualTo(20);
    }

    @Test
    @DisplayName("装箱数据解析 archive_id 后可重复更新而不产生重复记录")
    void importsArchiveBoxesByStableArchiveId() throws Exception {
        statisticsDataExchangeService.importStatistics(csv("statistics.csv", """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U01,2026-01-02,住院病案,12,100,张三,内科,P001,2026-01-05
                2,C02,U02,2026-02-03,住院病案,8,,李四,外科,P002,2026-02-07
                """), false);

        MockMultipartFile boxFile = csv("boxes.csv", """
                bah,sjh,box_no,expected_box_no,status,remark
                1,100,A-01,A-01,NORMAL,
                2,,A-02,A-02,MISSING,待补档
                """);

        DataExchangeImportResult validation = archiveBoxDataExchangeService.importArchiveBoxes(boxFile, true);
        DataExchangeImportResult imported = archiveBoxDataExchangeService.importArchiveBoxes(boxFile, false);
        DataExchangeImportResult repeated = archiveBoxDataExchangeService.importArchiveBoxes(boxFile, false);

        assertThat(validation.canImport()).isTrue();
        assertThat(validation.insertedRows()).isEqualTo(2);
        assertThat(imported.insertedRows()).isEqualTo(2);
        assertThat(repeated.updatedRows()).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_archive_box_record")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_archive_box_record where archive_id is not null")).isEqualTo(2);
    }

    @Test
    @DisplayName("无法唯一关联的装箱记录在预校验阶段拒绝")
    void rejectsUnresolvableArchiveBox() throws Exception {
        DataExchangeImportResult result = archiveBoxDataExchangeService.importArchiveBoxes(csv("boxes.csv", """
                bah,sjh,box_no,expected_box_no,status,remark
                1234567,,A-01,A-01,NORMAL,无法关联
                """), true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(result.errors().getFirst().message()).contains("无法唯一关联");
        assertThat(count("select count(*) from app.mr_archive_box_record")).isZero();
    }

    @Test
    @DisplayName("扫描记录支持预校验、幂等导入、受控更新和可重新导入导出")
    void importsScansIdempotently() throws Exception {
        String indexDefinition = jdbcTemplate.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'app'
                  and tablename = 'mr_scan'
                  and indexname = 'idx_mr_scan_folder_filename'
                """, String.class);
        assertThat(indexDefinition)
                .isNotBlank()
                .contains("USING btree (folder, filename)");

        statisticsDataExchangeService.importStatistics(csv("statistics.csv", """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U01,2026-01-02,住院病案,12,100,张三,内科,P001,2026-01-05
                """), false);

        MockMultipartFile file = csv("scans.csv", """
                sjh,bah,brxh,folder,filename,btype,filesize
                100,1,BR001,24.04/24.04.07/100-1,0001.jpg,3,1234
                200,2,BR002,24.04/24.04.07/200-2,0001.jpg,4,2345
                """);

        DataExchangeImportResult validation = scanDataExchangeService.importScans(file, true);
        DataExchangeImportResult imported = scanDataExchangeService.importScans(file, false);
        DataExchangeImportResult repeated = scanDataExchangeService.importScans(file, false);

        assertThat(validation.canImport()).isTrue();
        assertThat(validation.insertedRows()).isEqualTo(2);
        assertThat(imported.insertedRows()).isEqualTo(2);
        assertThat(repeated.insertedRows()).isZero();
        assertThat(repeated.updatedRows()).isZero();
        assertThat(repeated.duplicateRows()).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_scan")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_scan where archive_id is not null")).isEqualTo(2);
        assertThat(count("select count(*) from app.mr_archive")).isEqualTo(2);

        DataExchangeImportResult updated = scanDataExchangeService.importScans(csv("scan-update.csv", """
                sjh,bah,brxh,folder,filename,btype,filesize
                100,1,BR001,24.04/24.04.07/100-1,0001.jpg,5,3456
                """), false);

        assertThat(updated.updatedRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select btype from app.mr_scan where sjh = '100'",
                Integer.class
        )).isEqualTo(5);

        StringWriter scanWriter = new StringWriter();
        exportService.exportScan(null, "100", null, null, null, null, null, scanWriter);
        assertThat(scanWriter.toString()).startsWith(
                "\uFEFFsjh,bah,brxh,folder,filename,btype,filesize\n"
        );
        assertThat(scanWriter.toString()).contains("100,1,BR001,24.04/24.04.07/100-1,0001.jpg,5,3456");
    }

    @Test
    @DisplayName("GB18030 文件可识别，导出 CSV 保持英文可导入表头")
    void supportsGb18030AndReimportableExports() throws Exception {
        String content = """
                bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                1,C01,U01,2026-01-02,住院病案,12,100,张三,内科,P001,2026-01-05
                """;
        MockMultipartFile gbFile = new MockMultipartFile(
                "file",
                "statistics.csv",
                "text/csv",
                content.getBytes(Charset.forName("GB18030"))
        );
        DataExchangeImportResult result = statisticsDataExchangeService.importStatistics(gbFile, false);

        StringWriter statisticsWriter = new StringWriter();
        exportService.exportStatistics(null, null, null, null, null, null, statisticsWriter);

        assertThat(result.encoding()).isEqualTo("GB18030");
        assertThat(statisticsWriter.toString()).startsWith(
                "\uFEFFbah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate\n"
        );
        assertThat(statisticsWriter.toString()).contains("张三,内科,P001");
    }

    private MockMultipartFile csv(String fileName, String content) {
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
