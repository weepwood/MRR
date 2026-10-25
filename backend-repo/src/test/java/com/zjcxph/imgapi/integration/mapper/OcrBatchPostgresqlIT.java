package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml","mybatis.configuration.map-underscore-to-camel-case=true"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OcrBatchPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired OcrBatchMapper batches;@Autowired OcrMapper queue;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager manager;
    OcrBatchService service;OcrLoadGuard guard;AuthSession actor;int first;int last;String batchId;Long archive;
    @BeforeEach void setup() {
        actor=new AuthSession();actor.setId(888L);actor.setUsername("synthetic-batch-operator");actor.setRoleCode("ADMIN");
        var access=mock(OcrAccess.class);when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        var jobs=mock(OcrJobService.class);when(jobs.enabled()).thenReturn(true);
        when(jobs.configuration()).thenReturn(new OcrModels.Config("test","b".repeat(64),30,4194304,.7));
        guard=mock(OcrLoadGuard.class);when(guard.allowed()).thenReturn(true);
        service=new OcrBatchService(batches,queue,access,jobs,guard,new TransactionTemplate(manager),mock(OcrQualityGate.class));
        String code=UUID.randomUUID().toString();archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        var ids=jdbc.queryForList("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT a.id,a.bah,a.sjh,'synthetic-'||g||'.jpg','synthetic',0,1,206-g FROM app.mr_archive a CROSS JOIN generate_series(1,205) g WHERE a.id=? RETURNING id",Integer.class,archive);
        first=ids.getFirst();last=ids.getLast();batchId=null;
    }
    @AfterEach void cleanup() {
        com.zjcxph.imgapi.integration.OcrTestDataCleanup.purge(jdbc,manager,archive,batchId);
    }
    @Test void boundedCursorAndChildrenResumeWithoutDuplicates() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();
        service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(205);
        service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(205);
        assertThat(batches.jobs(batchId,"")).hasSize(1);
        assertThat(queue.next(batches.jobs(batchId,"").getFirst()).getScanId()).isEqualTo(last);
        completeChildren();service.dispatch();assertThat(batches.batch(batchId).getStatus()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT i.scan_id) FROM app.mr_smart_classification_job_item i JOIN app.mr_smart_classification_job j ON j.id=i.job_id WHERE j.batch_id=?",Integer.class,batchId)).isEqualTo(205);
    }
    @Test void insufficientBudgetMustNotSplitAnArchive() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,201,UUID.randomUUID().toString(),false)).id();
        service.dispatch();
        assertThat(batches.batch(batchId).getDispatchedCount()).isZero();
        assertThat(batches.batch(batchId).getReasonCode()).isEqualTo("CASE_EXCEEDS_BUDGET");
        assertThat(batches.jobs(batchId,"")).isEmpty();
    }
    @Test void lowerBoundaryMustNotSilentlyTruncateAnArchive() {
        batchId=service.create(actor,new OcrBatchService.Request(first,last,205,UUID.randomUUID().toString(),false)).id();
        service.dispatch();
        assertThat(batches.batch(batchId).getDispatchedCount()).isZero();
        assertThat(batches.batch(batchId).getReasonCode()).isEqualTo("CASE_RANGE_INCOMPLETE");
    }
    @Test void nullShelfNumberFallsBackWithoutRewritingCodes() {
        jdbc.update("UPDATE app.mr_archive SET sjh=NULL WHERE id=?",archive);
        jdbc.update("UPDATE app.mr_scan SET sjh=NULL WHERE archive_id=?",archive);
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();
        service.dispatch();
        assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(205);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.mr_scan WHERE archive_id=? AND sjh IS NOT NULL",Integer.class,archive)).isZero();
    }
    @Test void upperBoundaryMustNotSilentlyTruncateAnArchive() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last-1,205,UUID.randomUUID().toString(),false)).id();
        service.dispatch();assertThat(batches.batch(batchId).getReasonCode()).isEqualTo("CASE_RANGE_INCOMPLETE");
        assertThat(batches.jobs(batchId,"")).isEmpty();
    }
    @Test void effectiveCodeCollisionIsIsolated() {
        String code=batches.caseCode(archive);
        Long conflicting=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES(?) RETURNING id",Long.class,code);
        try {
            batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();
            service.dispatch();assertThat(batches.batch(batchId).getReasonCode()).isEqualTo("CASE_CODE_CONFLICT");
            assertThat(batches.jobs(batchId,"")).isEmpty();
        } finally {com.zjcxph.imgapi.integration.OcrTestDataCleanup.purge(jdbc,manager,conflicting,null);}
    }
    @Test void interleavedScanIdsDoNotSkipTheNextArchive() {
        String code=UUID.randomUUID().toString();
        Long second=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        try {
            jdbc.update("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT id,bah,sjh,'synthetic-second.jpg','synthetic',0,1,1 FROM app.mr_archive WHERE id=?",second);
            int end=jdbc.queryForObject("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT id,bah,sjh,'synthetic-late.jpg','synthetic',0,1,206 FROM app.mr_archive WHERE id=? RETURNING id",Integer.class,archive);
            batchId=service.create(actor,new OcrBatchService.Request(first-1,end,207,UUID.randomUUID().toString(),false)).id();
            service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(206);
            completeChildren();service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(207);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT archive_id) FROM app.mr_smart_classification_job WHERE batch_id=?",Integer.class,batchId)).isEqualTo(2);
        } finally {com.zjcxph.imgapi.integration.OcrTestDataCleanup.purge(jdbc,manager,second,null);}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void pausedCaseHoldsTheGlobalHistorySlotAcrossBatches(boolean legacy) {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();
        service.dispatch();service.action(actor,batchId,"pause");
        String code=UUID.randomUUID().toString();String secondBatch=null;
        Long second=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        try {
            int scan=jdbc.queryForObject("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT id,bah,sjh,'synthetic-second.jpg','synthetic',0,1,1 FROM app.mr_archive WHERE id=? RETURNING id",Integer.class,second);
            secondBatch=service.create(actor,new OcrBatchService.Request(scan-1,scan,1,UUID.randomUUID().toString(),false)).id();
            if(legacy) jdbc.update("UPDATE app.mr_ocr_batch SET case_mode=FALSE WHERE id=?",secondBatch);
            service.dispatch();assertThat(batches.jobs(secondBatch,"")).isEmpty();
            assertThat(batches.batch(secondBatch).getReasonCode()).isEqualTo("WAITING_PREVIOUS_CASE");
        } finally {com.zjcxph.imgapi.integration.OcrTestDataCleanup.purge(jdbc,manager,second,secondBatch);}
    }
    @Test void clampedUpperBoundRemainsIdempotentForAnIdenticalRequest() {
        var request=new OcrBatchService.Request(first-1,last+100,205,UUID.randomUUID().toString(),false);
        batchId=service.create(actor,request).id();
        assertThat(service.create(actor,request).id()).isEqualTo(batchId);
    }
    @Test void retryBackoffDoesNotSkipAheadOrCompleteTheCase() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();service.dispatch();
        String job=batches.jobs(batchId,"").getFirst();
        var tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(t->{queue.claim("synthetic-retry");queue.retry(job,last,"TIMEOUT");queue.release(job,"synthetic-retry");});
        assertThat(queue.next(job)).isNull();assertThat(queue.job(job).getStatus()).isEqualTo("RUNNING");
        assertThat(queue.job(job).getContextCursor()).isZero();
        assertThat(queue.items(job,0,1).getFirst().getScanId()).isEqualTo(last);
        assertThat(queue.items(job,last,1).getFirst().getScanId()).isEqualTo(last-1);
    }
    @Test void pausedHistoryCannotBeClaimedAndCancellationBypassesResourceGate() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();service.dispatch();
        service.action(actor,batchId,"pause");assertThat(queue.claimEligible("paused",true)).isNull();
        service.action(actor,batchId,"cancel");var cancelled=queue.claimEligible("cancel",false);
        assertThat(cancelled).isNotNull();assertThat(cancelled.isCancelRequested()).isTrue();
    }
    @Test void failedChildRegistrationRollsBackDiscoveryCursor() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,205,UUID.randomUUID().toString(),false)).id();
        jdbc.execute("ALTER TABLE app.mr_smart_classification_job_item ADD CONSTRAINT ck_test_batch_registration CHECK(scan_id<>"+first+")");
        try {
            assertThatThrownBy(service::dispatch).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(batches.batch(batchId).getCursorId()).isEqualTo(first-1);
            assertThat(batches.jobs(batchId,"")).isEmpty();
        } finally {jdbc.execute("ALTER TABLE app.mr_smart_classification_job_item DROP CONSTRAINT ck_test_batch_registration");}
    }
    private void completeChildren() {jdbc.update("UPDATE app.mr_smart_classification_job SET status='SUCCESS',processed_count=planned_count,ocr_success_count=planned_count WHERE batch_id=?",batchId);}
}
