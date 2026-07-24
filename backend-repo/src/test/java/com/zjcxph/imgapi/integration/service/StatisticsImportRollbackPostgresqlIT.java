package com.zjcxph.imgapi.integration.service;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest
@Import({
        TabularImportFileReader.class,
        StatisticsDataExchangeService.class
})
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("统计导入事务回滚 PostgreSQL 16 集成测试")
class StatisticsImportRollbackPostgresqlIT extends PostgresqlIntegrationTestSupport {

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
        dropFailureTrigger();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("后续插入失败时回滚此前更新和主档同步")
    void rollsBackAllWritesWhenLaterInsertFails() throws Exception {
        jdbcTemplate.update("""
                insert into app.mr_statistics
                    (bah, cid, openerno, date, type, pages, sjh,
                     patientname, inpatientdepartment, patientid, dischargedate)
                values
                    ('1', 'C01', 'U01', '2026-01-02', '住院病案', 10, '100',
                     '原患者', '原科室', 'P001', '2026-01-05')
                """);
        installFailureTrigger();

        try {
            MockMultipartFile file = csv("rollback.csv", """
                    bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
                    1,C01,U02,2026-01-02,住院病案,20,100,更新患者,更新科室,P001,2026-01-05
                    2,C02,U03,2026-02-03,住院病案,30,200,新增患者,新增科室,P002,2026-02-07
                    """);

            assertThatThrownBy(() -> statisticsDataExchangeService.importStatistics(file, false))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("forced statistics import failure");

            assertThat(count("select count(*) from app.mr_statistics")).isEqualTo(1);
            assertThat(count("select count(*) from app.mr_archive")).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select pages from app.mr_statistics where sjh = '100'",
                    Integer.class
            )).isEqualTo(10);
            assertThat(jdbcTemplate.queryForObject(
                    "select patientname from app.mr_statistics where sjh = '100'",
                    String.class
            )).isEqualTo("原患者");
            assertThat(count("select count(*) from app.mr_statistics where sjh = '200'")).isZero();
            assertThat(count("select count(*) from app.mr_archive where sjh in ('200', '00000200')")).isZero();
        }
        finally {
            dropFailureTrigger();
        }
    }

    private void installFailureTrigger() {
        jdbcTemplate.execute("""
                create or replace function app.fail_statistics_insert_for_test()
                returns trigger
                language plpgsql
                as $$
                begin
                    raise exception 'forced statistics import failure';
                end
                $$
                """);
        jdbcTemplate.execute("""
                create trigger trg_fail_statistics_insert_for_test
                before insert on app.mr_statistics
                for each row
                execute function app.fail_statistics_insert_for_test()
                """);
    }

    private void dropFailureTrigger() {
        jdbcTemplate.execute("drop trigger if exists trg_fail_statistics_insert_for_test on app.mr_statistics");
        jdbcTemplate.execute("drop function if exists app.fail_statistics_insert_for_test()");
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
