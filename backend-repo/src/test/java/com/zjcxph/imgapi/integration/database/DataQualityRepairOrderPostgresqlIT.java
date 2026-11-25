package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.DataQualityRepairOrderService;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import com.zjcxph.imgapi.service.impl.DataQualityServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataQualityRepairOrderPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void approvalRequiresSeparateActorAndRechecksCurrentCandidate() {
        long issue = fixture();
        var service = service(true);
        long order = service.propose(issue, 11L).id();
        assertThatThrownBy(() -> service.approve(order, 11L)).hasMessageContaining("不同");
        assertThat(service.approve(order, 12L).status()).isEqualTo("APPROVED");
        assertThat(service.get(order).approvedBy()).isEqualTo(12L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_repair_event WHERE order_id=?", Long.class, order)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mr_scan WHERE bah='7654321' AND archive_id IS NULL", Long.class)).isEqualTo(1L);
    }

    @Test
    void staleSourceCannotBeApprovedAndDisabledCannotPropose() {
        long issue = fixture();
        assertThatThrownBy(() -> service(false).propose(issue, 11L)).hasMessageContaining("关闭");
        var service = service(true);
        long order = service.propose(issue, 11L).id();
        jdbc.update("UPDATE app.mr_scan SET filename='changed.synthetic' WHERE bah='7654321'");
        assertThatThrownBy(() -> service.approve(order, 12L)).hasMessageContaining("变化");
        assertThat(service.get(order).status()).isEqualTo("PROPOSED");
    }

    @Test
    void applyVerifyAndCompensationRespectExistingTrigger() {
        var service = service(true);
        long order = service.propose(fixture(), 11L).id();
        assertThatThrownBy(() -> service.apply(order, 11L)).hasMessageContaining("状态");
        var approved = service.approve(order, 12L);
        assertThatThrownBy(() -> service.apply(order, 11L)).hasMessageContaining("审批人");
        assertThat(service.apply(order, 12L).status()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, approved.scanId())).isEqualTo(approved.targetArchiveId());
        assertThat(service.verify(order, 13L).status()).isEqualTo("VERIFIED");
        assertThatThrownBy(() -> service.compensate(order, 12L)).hasMessageContaining("重新关联");
        jdbc.update("INSERT INTO app.mr_archive(bah) VALUES ('7654321')");
        assertThat(service.compensate(order, 12L).status()).isEqualTo("COMPENSATED");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?", Long.class, approved.scanId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_repair_event WHERE order_id=?", Long.class, order)).isEqualTo(5L);
    }

    @Test
    void failedAuditRollsBackMedicalLinkAndOrderTogether() {
        var service = service(true);
        var order = service.approve(service.propose(fixture(), 11L).id(),12L);
        var failingJdbc = new JdbcTemplate(java.util.Objects.requireNonNull(jdbc.getDataSource())) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("INSERT INTO app.mrr_data_quality_repair_event") && "APPLIED".equals(args[2]))
                    throw new IllegalStateException("synthetic audit failure");
                return super.update(sql,args);
            }
        };
        var failing = new DataQualityRepairOrderService(failingJdbc,
                new DataQualityServiceImpl(jdbc,new SimpleMeterRegistry()),
                mock(DeploymentReadinessService.class),mock(MaintenanceModeService.class),true);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
        assertThatThrownBy(() -> tx.execute(status -> failing.apply(order.id(),12L)))
                .hasMessageContaining("synthetic audit failure");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,order.scanId())).isNull();
        assertThat(service.get(order.id()).status()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mrr_data_quality_repair_event WHERE order_id=?",Long.class,order.id())).isEqualTo(2L);
    }

    @Test
    void approvedOrderRejectsNewAmbiguityAndExpiry() {
        var service = service(true);
        var order = service.approve(service.propose(fixture(),11L).id(),12L);
        jdbc.update("INSERT INTO app.mr_archive(bah) VALUES ('7654321')");
        assertThatThrownBy(() -> service.apply(order.id(),12L)).hasMessageContaining("不唯一");
        jdbc.update("UPDATE app.mrr_data_quality_repair_order SET approved_at=clock_timestamp()-interval '25 hours' WHERE id=?",order.id());
        assertThatThrownBy(() -> service.apply(order.id(),12L)).hasMessageContaining("过期");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,order.scanId())).isNull();
    }

    @Test
    void verifiedRecurrenceNeedsNewSuccessfulRunAndNewApproval() {
        var service = service(true);
        long oldIssue = fixture();
        var old = service.approve(service.propose(oldIssue,11L).id(),12L);
        service.apply(old.id(),12L);
        service.verify(old.id(),13L);
        // Construct a synthetic recurrence using the existing trigger (ambiguity then resolution).
        long duplicate = jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES ('7654321') RETURNING id",Long.class);
        jdbc.update("UPDATE app.mr_scan SET archive_id=NULL WHERE id=?",old.scanId());
        jdbc.update("DELETE FROM app.mr_archive WHERE id=?",duplicate);
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,old.scanId())).isNull();
        assertThatThrownBy(() -> service.propose(oldIssue,14L)).hasMessageContaining("重新成功检查");
        assertThat(service.get(old.id()).status()).isEqualTo("VERIFIED");
        long run = jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_run(status,triggered_by,started_at,completed_at)
                VALUES ('SUCCESS','synthetic',clock_timestamp(),clock_timestamp()) RETURNING id
                """,Long.class);
        long issue = jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,bah,detail,detected_at)
                VALUES (?,'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED','synthetic','WARNING','mr_scan',?,'7654321','synthetic',clock_timestamp()) RETURNING id
                """,Long.class,run,Integer.toString(old.scanId()));
        var next = service.propose(issue,14L);
        assertThat(next.reopenedFrom()).isEqualTo(old.id());
        assertThat(next.approvedBy()).isNull();
        assertThat(next.status()).isEqualTo("PROPOSED");
        assertThat(service.get(old.id()).status()).isEqualTo("SUPERSEDED");
        assertThat(service.events(old.id(),0)).extracting(DataQualityRepairOrderService.Event::action)
                .containsExactly("PROPOSED","APPROVED","APPLIED","VERIFIED","SUPERSEDED");
        assertThat(service.list(null,issue,null,14L).orders()).extracting(DataQualityRepairOrderService.Order::id)
                .containsExactly(next.id(),old.id());
        assertThatThrownBy(() -> service.approve(next.id(),14L)).hasMessageContaining("不同");
        assertThatThrownBy(() -> service.propose(issue,15L)).hasMessageContaining("有效修复工单");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,old.scanId())).isNull();
        assertThat(service.approve(next.id(),15L).status()).isEqualTo("APPROVED");
    }

    @Test
    void normalBoxAssociationUsesSameApprovalWithoutTouchingScanOrBoxFacts() {
        var service=service(true);
        long scanIssue=fixture();
        var scanOrder=service.propose(scanIssue,11L);
        long box=jdbc.queryForObject("INSERT INTO app.mr_archive_box_record(id,bah,box_no,status) VALUES (?,'654321','synthetic-box','NORMAL') RETURNING id",Long.class,scanOrder.scanId());
        long target=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES ('654321') RETURNING id",Long.class);
        long issue=boxIssue(box,"BOX_ARCHIVE_LINK_MISSING","mr_archive_box_record","654321");
        var order=service.propose(issue,11L);
        assertThat(order.scanId()).isNull();
        assertThat(order.entityType()).isEqualTo("mr_archive_box_record");
        assertThat(order.entityId()).isEqualTo(Long.toString(box));
        assertThat(order.targetArchiveId()).isEqualTo(target);
        assertThat(service.list(null,issue,null,11L).orders()).extracting(DataQualityRepairOrderService.Order::id).containsExactly(order.id());
        assertThat(service.list(null,scanIssue,null,11L).orders()).extracting(DataQualityRepairOrderService.Order::id).containsExactly(scanOrder.id());
        assertThatThrownBy(()->service.approve(order.id(),11L)).hasMessageContaining("不同");
        service.approve(order.id(),12L);
        assertThat(service.apply(order.id(),12L).status()).isEqualTo("APPLIED");
        assertThat(service.verify(order.id(),13L).status()).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_archive_box_record WHERE id=?",Long.class,box)).isEqualTo(target);
        assertThat(jdbc.queryForObject("SELECT box_no FROM app.mr_archive_box_record WHERE id=?",String.class,box)).isEqualTo("synthetic-box");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,scanOrder.scanId())).isNull();
        assertThatThrownBy(()->service.compensate(order.id(),12L)).hasMessageContaining("重新关联");
        jdbc.update("INSERT INTO app.mr_archive(bah) VALUES ('654321')");
        assertThat(service.compensate(order.id(),12L).status()).isEqualTo("COMPENSATED");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_archive_box_record WHERE id=?",Long.class,box)).isNull();
    }

    @Test
    void nonNormalBoxAndUnsupportedStatisticsCannotBeRepaired() {
        var service=service(true);
        long box=jdbc.queryForObject("INSERT INTO app.mr_archive_box_record(bah,box_no,status) VALUES ('654321','synthetic-box','MISPLACED') RETURNING id",Long.class);
        jdbc.update("INSERT INTO app.mr_archive(bah) VALUES ('654321')");
        long issue=boxIssue(box,"BOX_ARCHIVE_LINK_MISSING","mr_archive_box_record","654321");
        assertThatThrownBy(()->service.propose(issue,11L)).hasMessageContaining("确定性");
        long unsupported=boxIssue(box,"STATISTICS_ARCHIVE_LINK_MISSING","mr_statistics","654321");
        assertThatThrownBy(()->service.propose(unsupported,11L)).hasMessageContaining("仅支持");
        jdbc.update("UPDATE app.mr_archive_box_record SET status='NORMAL' WHERE id=?",box);
        var order=service.propose(issue,11L);
        service.approve(order.id(),12L);
        jdbc.update("UPDATE app.mr_archive_box_record SET box_no='changed' WHERE id=?",box);
        assertThatThrownBy(()->service.apply(order.id(),12L)).hasMessageContaining("变化");
        assertThat(service.get(order.id()).status()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_archive_box_record WHERE id=?",Long.class,box)).isNull();
    }

    private long boxIssue(long entity,String check,String type,String bah) {
        long run=jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','test') RETURNING id",Long.class);
        return jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,bah,detail)
                VALUES (?,?,'synthetic','WARNING',?,?,?,'synthetic') RETURNING id
                """,Long.class,run,check,type,Long.toString(entity),bah);
    }

    private DataQualityRepairOrderService service(boolean enabled) {
        return new DataQualityRepairOrderService(jdbc, new DataQualityServiceImpl(jdbc, new SimpleMeterRegistry()),
                mock(DeploymentReadinessService.class), mock(MaintenanceModeService.class), enabled);
    }

    private long fixture() {
        int scan = jdbc.queryForObject("INSERT INTO app.mr_scan(bah,filename,uploadflag) VALUES ('7654321','synthetic.png',1) RETURNING id", Integer.class);
        jdbc.update("INSERT INTO app.mr_archive(bah) VALUES ('7654321')");
        long run = jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','test') RETURNING id", Long.class);
        return jdbc.queryForObject("""
            INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,bah,detail)
            VALUES (?,'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED','synthetic','WARNING','mr_scan',?,'7654321','synthetic') RETURNING id
            """, Long.class, run, Integer.toString(scan));
    }
}
