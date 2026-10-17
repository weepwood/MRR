package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.service.*;
import org.junit.jupiter.api.*;
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
    OcrBatchService service;OcrLoadGuard guard;AuthSession actor;int first;int last;String batchId;
    @BeforeEach void setup() {
        actor=new AuthSession();actor.setId(888L);actor.setUsername("synthetic-batch-operator");actor.setRoleCode("ADMIN");
        var access=mock(OcrAccess.class);when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        var jobs=mock(OcrJobService.class);when(jobs.enabled()).thenReturn(true);
        when(jobs.configuration()).thenReturn(new OcrModels.Config("test","b".repeat(64),30,4194304,.7));
        guard=mock(OcrLoadGuard.class);when(guard.allowed()).thenReturn(true);
        service=new OcrBatchService(batches,queue,access,jobs,guard,new TransactionTemplate(manager),mock(OcrQualityGate.class));
        String code=UUID.randomUUID().toString();Long archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        var ids=jdbc.queryForList("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag) SELECT a.id,a.bah,a.sjh,'synthetic-'||g||'.jpg','synthetic',0,1 FROM app.mr_archive a CROSS JOIN generate_series(1,205) g WHERE a.id=? RETURNING id",Integer.class,archive);
        first=ids.getFirst();last=ids.getLast();batchId=null;
    }
    @AfterEach void cleanup() {
        if(batchId!=null) {jdbc.update("UPDATE app.mr_smart_classification_job SET status='CANCELLED' WHERE batch_id=?",batchId);batches.status(batchId,"CANCELLED",null);}
    }
    @Test void boundedCursorAndChildrenResumeWithoutDuplicates() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,201,UUID.randomUUID().toString(),false)).id();
        service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(200);
        service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(200);
        completeChildren();service.dispatch();assertThat(batches.batch(batchId).getDispatchedCount()).isEqualTo(201);
        assertThat(batches.batch(batchId).getCursorId()).isLessThan(last);
        completeChildren();service.dispatch();assertThat(batches.batch(batchId).getStatus()).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT i.scan_id) FROM app.mr_smart_classification_job_item i JOIN app.mr_smart_classification_job j ON j.id=i.job_id WHERE j.batch_id=?",Integer.class,batchId)).isEqualTo(201);
    }
    @Test void pausedHistoryCannotBeClaimedAndCancellationBypassesResourceGate() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,10,UUID.randomUUID().toString(),false)).id();service.dispatch();
        service.action(actor,batchId,"pause");assertThat(queue.claimEligible("paused",true)).isNull();
        service.action(actor,batchId,"cancel");var cancelled=queue.claimEligible("cancel",false);
        assertThat(cancelled).isNotNull();assertThat(cancelled.isCancelRequested()).isTrue();
    }
    @Test void failedChildRegistrationRollsBackDiscoveryCursor() {
        batchId=service.create(actor,new OcrBatchService.Request(first-1,last,10,UUID.randomUUID().toString(),false)).id();
        jdbc.execute("ALTER TABLE app.mr_smart_classification_job_item ADD CONSTRAINT ck_test_batch_registration CHECK(scan_id<>"+first+")");
        try {
            assertThatThrownBy(service::dispatch).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(batches.batch(batchId).getCursorId()).isEqualTo(first-1);
            assertThat(batches.jobs(batchId,"")).isEmpty();
        } finally {jdbc.execute("ALTER TABLE app.mr_smart_classification_job_item DROP CONSTRAINT ck_test_batch_registration");}
    }
    private void completeChildren() {jdbc.update("UPDATE app.mr_smart_classification_job SET status='SUCCESS',processed_count=planned_count,ocr_success_count=planned_count WHERE batch_id=?",batchId);}
}
