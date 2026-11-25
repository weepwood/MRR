package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class QualityCaseReviewPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    private QualityCaseReviewService service(boolean enabled) {
        return new QualityCaseReviewService(jdbc,mock(DeploymentReadinessService.class),mock(MaintenanceModeService.class),enabled);
    }
    private long run() {
        return jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by,completed_at) VALUES ('SUCCESS','synthetic',clock_timestamp()) RETURNING id",Long.class);
    }
    private long issue(long run,String type,long entity,String code) {
        return jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,detail)
                VALUES (?,?,'synthetic','WARNING',?,?,'synthetic') RETURNING id
                """,Long.class,run,code,type,Long.toString(entity));
    }

    @Test void repeatedSamplesShareCaseButNotDecisionAndOldEvidenceSurvivesPruning() {
        var service=service(true);
        long firstRun=run();
        long first=issue(firstRun,"mr_scan",123,"SCAN_ARCHIVE_LINK_MISSING_ESTIMATED");
        assertThat(service.context(first).review()).isNull();
        var confirmed=service.decide(first,0,"CONFIRM",11);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(service.context(first).reviewedThisSample()).isTrue();
        assertThatThrownBy(()->service.decide(first,0,"IGNORE",12)).hasMessageContaining("变化");
        long secondRun=run();
        long second=issue(secondRun,"mr_scan",123,"SCAN_ARCHIVE_LINK_MISSING_ESTIMATED");
        assertThat(service.context(second).reviewedThisSample()).isFalse();
        assertThat(service.context(second).review().id()).isEqualTo(confirmed.id());
        assertThatThrownBy(()->service.decide(first,1,"IGNORE",12)).hasMessageContaining("最新");
        var ignored=service.decide(second,1,"IGNORE",12);
        var reopened=service.decide(second,2,"REOPEN",13);
        assertThat(reopened.status()).isEqualTo("OPEN");
        assertThat(ignored.id()).isEqualTo(reopened.id());
        assertThat(reopened.revision()).isEqualTo(3);
        jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id IN (?,?)",firstRun,secondRun);
        assertThat(service.list(null).reviews()).containsExactly(reopened);
        assertThat(service.events(Long.parseLong(reopened.id()),0)).extracting(QualityCaseReviewService.Event::status)
                .containsExactly("OPEN","IGNORED","CONFIRMED");
        assertThat(service.events(Long.parseLong(reopened.id()),3)).hasSize(2);
    }

    @Test void scopeAndDisabledWritesAreIndependentOfMedicalRecords() {
        var service=service(true);long run=run();
        long scan=issue(run,"mr_scan",123,"SCAN_ARCHIVE_LINK_MISSING_ESTIMATED");
        long box=issue(run,"mr_archive_box_record",123,"BOX_ARCHIVE_LINK_MISSING");
        long statistics=issue(run,"mr_statistics",123,"STATISTICS_ARCHIVE_LINK_MISSING");
        assertThatThrownBy(()->service(false).decide(scan,0,"CONFIRM",11)).hasMessageContaining("关闭");
        assertThatThrownBy(()->service.decide(statistics,0,"CONFIRM",11)).hasMessageContaining("尚未开放");
        var a=service.decide(scan,0,"CONFIRM",11);
        var b=service.decide(box,0,"IGNORE",11);
        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(service.list(Long.parseLong(b.id())).reviews()).containsExactly(a);
        assertThat(service(false).list(null).enabled()).isFalse();
    }

    @Test void auditFailureRollsBackDecisionInSameTransaction() {
        long issue=issue(run(),"mr_scan",123,"SCAN_ARCHIVE_LINK_MISSING_ESTIMATED");
        var bad=new JdbcTemplate(java.util.Objects.requireNonNull(jdbc.getDataSource())) {
            @Override public int update(String sql,Object...args) {
                if(sql.contains("INSERT INTO app.mrr_quality_case_review_event")) throw new IllegalStateException("synthetic audit failure");
                return super.update(sql,args);
            }
        };
        var service=new QualityCaseReviewService(bad,mock(DeploymentReadinessService.class),mock(MaintenanceModeService.class),true);
        var tx=new org.springframework.transaction.support.TransactionTemplate(txManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(()->tx.execute(status->service.decide(issue,0,"CONFIRM",11))).hasMessageContaining("synthetic audit failure");
        assertThat(service(true).context(issue).review()).isNull();
    }
}
