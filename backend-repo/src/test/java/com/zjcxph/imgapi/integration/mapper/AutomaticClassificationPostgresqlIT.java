package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml","mybatis.configuration.map-underscore-to-camel-case=true"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AutomaticClassificationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired AutomaticClassificationMapper mapper;@Autowired ClassificationMapper classifications;
    @Autowired OcrBatchMapper batches;@Autowired OcrMapper ocr;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager manager;
    @TempDir Path temporary;
    AutomaticClassificationService service;ImageTypeService types;OcrQualityGate quality;OcrAccess access;OcrInput input;
    AuthSession actor;String batchId;String jobId;Long archive;String sourceHash;List<Integer> scans;
    @BeforeEach void setup() throws Exception {
        actor=new AuthSession();actor.setId(876L);actor.setUsername("synthetic-authorizer");actor.setRoleCode("ADMIN");AuthContext.setCurrentUser(actor);
        access=mock(OcrAccess.class);when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        sourceHash="a".repeat(64);input=mock(OcrInput.class);when(input.prepare(anyInt(),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic.ocr"),sourceHash));
        quality=mock(OcrQualityGate.class);when(quality.enabled()).thenReturn(true);when(quality.checksum()).thenReturn("c".repeat(64));when(quality.permits(anyString(),anyString(),anyInt())).thenReturn(true);
        var guard=mock(OcrLoadGuard.class);when(guard.allowed()).thenReturn(true);var jobs=mock(OcrJobService.class);when(jobs.enabled()).thenReturn(true);
        var batchService=mock(OcrBatchService.class);when(batchService.owned(any(),anyString())).thenAnswer(i->batches.batch(i.getArgument(1)));
        var tx=new TransactionTemplate(manager);types=new ImageTypeService(classifications,ocr,access,input,tx,new ConcurrentMapCacheManager());
        service=new AutomaticClassificationService(mapper,classifications,batches,ocr,input,access,types,quality,guard,jobs,batchService,tx);
        String code=UUID.randomUUID().toString();archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        scans=jdbc.queryForList("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag) SELECT a.id,a.bah,a.sjh,'synthetic-'||g||'.jpg','synthetic',0,1 FROM app.mr_archive a CROSS JOIN generate_series(1,2) g WHERE a.id=? RETURNING id",Integer.class,archive);
        var batch=new OcrBatchMapper.Batch();batchId=UUID.randomUUID().toString();batch.setId(batchId);batch.setOwnerUserId(actor.getId());batch.setCreatedBy(actor.getUsername());batch.setIdempotencyKey(batchId);batch.setAfterId(0);batch.setUpperId(scans.getLast());batch.setMaxPages(10);batch.setAutoApply(true);batch.setPolicyChecksum("c".repeat(64));batch.setRuleSetVersion("rules");batch.setLexiconVersion("lexicon");
        batch.setConfigSnapshot(OcrProtocol.JSON.writeValueAsString(new OcrModels.Config("test","b".repeat(64),30,4194304,.7)));batches.insert(batch);
        var job=new OcrModels.Job();jobId=UUID.randomUUID().toString();job.setId(jobId);job.setArchiveId(archive);job.setBatchId(batchId);job.setCreatedBy(actor.getUsername());job.setOwnerUserId(actor.getId());job.setConfigSnapshot(batch.getConfigSnapshot());job.setRuleSetVersion("rules");job.setLexiconVersion("lexicon");ocr.insert(job);
        for(Integer scan:scans) {
            var fact=new OcrModels.Fact();fact.setScanId(scan);fact.setArchiveId(archive);fact.setStatus("SUCCEEDED");fact.setOcrText("合成手术记录");fact.setLinesJson("[]");fact.setContentHash(sourceHash);fact.setResultVersion(UUID.randomUUID().toString());fact.setModelChecksum("b".repeat(64));fact.setConfidence(.99);ocr.saveFact(fact);
            var s=new ClassificationMapper.Suggestion();s.setJobId(jobId);s.setScanId(scan);s.setOriginalType(0);s.setOriginalTypeVersion(0);s.setSuggestedType(3);s.setRuleScore(1.);s.setOcrConfidence(.99);s.setRuleSetVersion("rules");s.setLexiconVersion("lexicon");s.setOcrContentHash(sourceHash);s.setOcrResultVersion(fact.getResultVersion());s.setMatchedRules("[]");s.setExcludedRules("[]");s.setAutoEligible(true);s.setReasonCode("EXACT_TITLE");classifications.suggest(s);
        }
    }
    @AfterEach void cleanup() {service.close();jdbc.update("UPDATE app.mr_smart_classification_job SET status='CANCELLED' WHERE batch_id=?",batchId);batches.status(batchId,"CANCELLED",null);AuthContext.clear();}
    @Test void automaticApplyIsAuditedAndReplayDoesNotWriteTwice() {
        service.runOne();service.runOne();service.runOne();
        assertThat(classifications.scan(scans.getFirst()).getBtype()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.mr_image_type_audit WHERE batch_id=?",Integer.class,batchId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT execution_mode FROM app.mr_image_type_audit WHERE batch_id=?",String.class,batchId)).containsOnly("BACKGROUND");
        assertThat(jdbc.queryForList("SELECT operator_user_id FROM app.mr_image_type_audit WHERE batch_id=?",Long.class,batchId)).containsOnly(actor.getId());
    }
    @Test void rollbackSkipsLaterHumanConfirmationEvenIfTypeIsUnchanged() {
        service.runOne();service.runOne();types.manual(scans.getLast(),3);
        batches.status(batchId,"PAUSED","USER_PAUSED");when(quality.enabled()).thenReturn(false);
        var first=service.rollback(actor,batchId,new AutomaticClassificationService.RollbackRequest(0,100));
        assertThat(first.reverted()).isEqualTo(1);assertThat(first.skipped()).isEqualTo(1);
        assertThat(classifications.scan(scans.getFirst()).getBtype()).isZero();
        assertThat(classifications.scan(scans.getLast()).getBtype()).isEqualTo(3);
        assertThat(service.rollback(actor,batchId,new AutomaticClassificationService.RollbackRequest(0,100)).reverted()).isZero();
    }
    @Test void changedSourceAndChangedTypeCannotBeAutomaticallyOverwritten() {
        types.manual(scans.getFirst(),15);sourceHash="d".repeat(64);service.runOne();service.runOne();
        assertThat(classifications.scan(scans.getFirst()).getBtype()).isEqualTo(15);
        assertThat(classifications.scan(scans.getLast()).getBtype()).isZero();
        assertThat(ocr.fact(scans.getLast()).getStatus()).isEqualTo("STALE");
    }
    @Test void failedAuditRollsBackAutomaticTypeAndFallsBackToReview() {
        jdbc.execute("ALTER TABLE app.mr_image_type_audit ADD CONSTRAINT ck_test_auto_failure CHECK(operation<>'AUTO_APPLY_UNCLASSIFIED') NOT VALID");
        try {
            service.runOne();assertThat(classifications.scan(scans.getFirst()).getBtype()).isZero();
            assertThat(jdbc.queryForObject("SELECT auto_status FROM app.mr_image_classification_suggestion WHERE scan_id=?",String.class,scans.getFirst())).isEqualTo("REVIEW_REQUIRED");
        } finally {jdbc.execute("ALTER TABLE app.mr_image_type_audit DROP CONSTRAINT ck_test_auto_failure");}
    }
    @Test void revokedAuthorizationPausesWithoutReadingOrChangingImage() {
        when(access.require(anyLong(),any(String[].class))).thenThrow(new BusinessException(403,"revoked"));service.runOne();
        assertThat(batches.batch(batchId).getStatus()).isEqualTo("PAUSED");verify(input,never()).prepare(anyInt(),any());
        assertThat(classifications.scan(scans.getFirst()).getBtype()).isZero();
    }
}
