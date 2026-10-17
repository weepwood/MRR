package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ClassificationMapper;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.service.ImageTypeService;
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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml","mybatis.configuration.map-underscore-to-camel-case=true"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ClassificationAuditPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired ClassificationMapper mapper; @Autowired OcrMapper ocr; @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @TempDir Path temporary;
    ImageTypeService service; AuthSession actor; int scan; Long archive;
    @BeforeEach void setup() {
        actor=new AuthSession();actor.setId(9999999999L);actor.setUsername("synthetic-auditor");actor.setRoleCode("ADMIN");actor.setStatus("active");
        AuthContext.setCurrentUser(actor); OcrAccess access=mock(OcrAccess.class);
        when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        OcrInput input=mock(OcrInput.class);
        when(input.prepare(anyInt(),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic.ocr"),"a".repeat(64)));
        service=new ImageTypeService(mapper,ocr,access,input,new TransactionTemplate(transactionManager),new ConcurrentMapCacheManager());
        String code=UUID.randomUUID().toString();
        archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        scan=jdbc.queryForObject("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag) SELECT id,bah,sjh,'synthetic.jpg','synthetic',0,1 FROM app.mr_archive WHERE id=? RETURNING id",Integer.class,archive);
    }
    @AfterEach void clear() {
        jdbc.update("UPDATE app.mr_smart_classification_job SET status='SUCCESS' WHERE archive_id=?",archive);
        AuthContext.clear();
    }
    @Test void classificationFailureMustNotDiscardSuccessfulOcrFact() {
        var access=mock(OcrAccess.class);when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        var input=mock(OcrInput.class);when(input.prepare(anyInt(),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic.ocr"),"a".repeat(64)));
        var engine=mock(OcrEngine.class);when(engine.fingerprint(anyString())).thenReturn("b".repeat(64));
        when(engine.recognize(anyString(),any(),anyInt(),anyInt(),any())).thenReturn(new OcrProtocol.Result("x",100,100,java.util.List.of(new OcrProtocol.Line("合成标题",.99,java.util.List.of(0.,0.,80.,20.))),"b".repeat(64)));
        var settings=mock(com.zjcxph.imgapi.service.SystemSettingService.class);when(settings.getSetting("ocrEnabled")).thenReturn("true");when(settings.getSetting("ocrProfile")).thenReturn("local");
        var classification=mock(com.zjcxph.imgapi.service.ClassificationService.class);doThrow(new OcrFailure("CLASSIFICATION_FAILED")).when(classification).classify(any(),any(),any());
        var jobs=new com.zjcxph.imgapi.service.OcrJobService(ocr,mock(com.zjcxph.imgapi.mapper.ScanMapper.class),access,engine,input,settings,new TransactionTemplate(transactionManager),classification);
        var scans=mock(com.zjcxph.imgapi.mapper.ScanMapper.class);var image=new com.zjcxph.imgapi.entity.Scan();image.setId(scan);image.setBtype(0);when(scans.findById(scan)).thenReturn(image);
        jobs=new com.zjcxph.imgapi.service.OcrJobService(ocr,scans,access,engine,input,settings,new TransactionTemplate(transactionManager),classification);
        var job=jobs.create(actor,archive,new OcrModels.Create(UUID.randomUUID().toString(),scan));jobs.runOne();
        assertThat(ocr.fact(scan)).isNotNull();
        assertThat(ocr.fact(scan).getOcrText()).isEqualTo("合成标题");
        assertThat(ocr.items(job.id(),0,50).getFirst().getFailureCode()).isEqualTo("CLASSIFICATION_FAILED");
    }
    @Test void persistsRealActorAndVersionAndAuditFailureRollsBackType() {
        service.manual(scan,15);
        assertThat(mapper.scan(scan).getTypeVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT operator_user_id FROM app.mr_image_type_audit WHERE scan_id=?",Long.class,scan)).isEqualTo(actor.getId());
        jdbc.execute("ALTER TABLE app.mr_image_type_audit ADD CONSTRAINT ck_test_classification_failure CHECK(new_type<>11)");
        try {
            assertThatThrownBy(()->service.manual(scan,11)).isInstanceOf(Exception.class);
            assertThat(mapper.scan(scan).getBtype()).isEqualTo(15);
            assertThat(mapper.scan(scan).getTypeVersion()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.mr_image_type_audit WHERE scan_id=?",Integer.class,scan)).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE app.mr_image_type_audit DROP CONSTRAINT ck_test_classification_failure");}
    }
    @Test void staleSuggestionCannotOverwriteManualChangeEvenIfTypeReturnsToZero() {
        Long suggestion=suggestion();service.manual(scan,3);service.manual(scan,0);
        assertThatThrownBy(()->service.review(actor,suggestion,"ACCEPT",null)).hasMessageContaining("已变化");
        assertThat(mapper.scan(scan).getBtype()).isZero();
        assertThat(mapper.scan(scan).getTypeVersion()).isEqualTo(2);
    }
    @Test void acceptingSuggestionAndAuditAreAtomicAndReplayIsRejected() {
        Long suggestion=suggestion();service.review(actor,suggestion,"ACCEPT",null);
        assertThat(mapper.scan(scan).getBtype()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT review_status FROM app.mr_image_classification_suggestion WHERE id=?",String.class,suggestion)).isEqualTo("ACCEPTED");
        assertThatThrownBy(()->service.review(actor,suggestion,"ACCEPT",null)).hasMessageContaining("已被处理");
    }
    @Test void ocrVersionReplacementMustWaitForReviewCommit() throws Exception {
        Long suggestion=suggestion();
        var access=mock(OcrAccess.class);
        var writer=java.util.concurrent.Executors.newSingleThreadExecutor();
        var pending=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        var started=new java.util.concurrent.CountDownLatch(1);
        when(access.require(anyLong(),any(String[].class))).thenAnswer(call->{
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                var replacement=writer.submit(()->{started.countDown();jdbc.update("UPDATE app.mr_scan_ocr SET result_version=?,content_hash=? WHERE scan_id=?",UUID.randomUUID().toString(),"c".repeat(64),scan);});
                pending.set(replacement);
                assertThat(started.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->replacement.get(500,java.util.concurrent.TimeUnit.MILLISECONDS))
                        .as("OCR version must remain stable until the audited type transaction commits")
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            }
            return actor;
        });
        var input=mock(OcrInput.class);
        when(input.prepare(anyInt(),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic.ocr"),"a".repeat(64)));
        var reviewer=new ImageTypeService(mapper,ocr,access,input,new TransactionTemplate(transactionManager),new ConcurrentMapCacheManager());
        try {reviewer.review(actor,suggestion,"ACCEPT",null);}
        finally {try {if(pending.get()!=null) pending.get().get(5,java.util.concurrent.TimeUnit.SECONDS);}finally{writer.shutdownNow();}}
        assertThat(mapper.scan(scan).getBtype()).isEqualTo(3);
    }
    @Test void replacementCommittedBeforeReviewRejectsOldSuggestion() {
        Long suggestion=suggestion();
        jdbc.update("UPDATE app.mr_scan_ocr SET result_version=?,content_hash=? WHERE scan_id=?",UUID.randomUUID().toString(),"c".repeat(64),scan);
        assertThatThrownBy(()->service.review(actor,suggestion,"ACCEPT",null)).hasMessageContaining("已变化");
        assertThat(mapper.scan(scan).getBtype()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.mr_image_type_audit WHERE scan_id=?",Integer.class,scan)).isZero();
    }
    private Long suggestion() {
        var job=new OcrModels.Job();job.setId(UUID.randomUUID().toString());job.setArchiveId(archive);job.setCreatedBy(actor.getUsername());job.setOwnerUserId(actor.getId());job.setConfigSnapshot("{}");job.setRuleSetVersion("test");job.setLexiconVersion("test");ocr.insert(job);
        var fact=new OcrModels.Fact();fact.setScanId(scan);fact.setArchiveId(archive);fact.setStatus("SUCCEEDED");fact.setOcrText("合成手术记录");fact.setLinesJson("[]");fact.setContentHash("a".repeat(64));fact.setResultVersion(UUID.randomUUID().toString());fact.setConfidence(.99);ocr.saveFact(fact);
        var s=new ClassificationMapper.Suggestion();s.setJobId(job.getId());s.setScanId(scan);s.setOriginalType(0);s.setOriginalTypeVersion(0);s.setSuggestedType(3);s.setRuleScore(1.);s.setOcrConfidence(.99);s.setRuleSetVersion("test");s.setLexiconVersion("test");s.setOcrContentHash(fact.getContentHash());s.setOcrResultVersion(fact.getResultVersion());s.setMatchedRules("[]");s.setExcludedRules("[]");mapper.suggest(s);
        return jdbc.queryForObject("SELECT id FROM app.mr_image_classification_suggestion WHERE job_id=?",Long.class,job.getId());
    }
}
