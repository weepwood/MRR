package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.integration.OcrTestDataCleanup;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.ocr.*;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import com.zjcxph.imgapi.service.*;
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
import static org.mockito.AdditionalAnswers.delegatesTo;

@MybatisTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml","mybatis.configuration.map-underscore-to-camel-case=true"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class OcrContextPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired OcrMapper queue;@Autowired ClassificationMapper classifications;@Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;@TempDir Path temporary;
    OcrJobService service;ImageTypeService types;AuthSession actor;Job job;Long archive;
    OcrInput input;
    final List<Integer> scans=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        scans.clear();String code=UUID.randomUUID().toString();
        archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        actor=new AuthSession();actor.setId(888L);actor.setUsername("synthetic-context-reviewer");actor.setRoleCode("ADMIN");
        OcrAccess access=mock(OcrAccess.class);when(access.require(anyLong(),any(String[].class))).thenReturn(actor);
        input=mock(OcrInput.class);when(input.prepare(anyInt(),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic-"+i.getArgument(0)),"a".repeat(64)));
        var catalog=mock(ClassificationMapper.class,delegatesTo(classifications));
        doReturn(List.of(new ClassificationEngine.Rule(1L,3,"手术记录","TITLE","EXACT",1,false,false,100,true))).when(catalog).rules(anyString());
        doReturn(List.of()).when(catalog).lexicon(anyString());
        var classifier=new ClassificationService(catalog,queue);
        var settings=mock(SystemSettingService.class);when(settings.getSetting("ocrEnabled")).thenReturn("true");
        service=new OcrJobService(queue,mock(ScanMapper.class),access,mock(OcrEngine.class),input,settings,new TransactionTemplate(manager),classifier,mock(OcrLoadGuard.class));
        types=new ImageTypeService(classifications,queue,access,input,new TransactionTemplate(manager),new ConcurrentMapCacheManager());
        job=new Job();job.setId(UUID.randomUUID().toString());job.setArchiveId(archive);job.setOwnerUserId(actor.getId());job.setCreatedBy(actor.getUsername());
        job.setConfigSnapshot(OcrProtocol.JSON.writeValueAsString(new Config("synthetic","b".repeat(64),30,4194304,.7)));
        job.setRuleSetVersion("synthetic-context");job.setLexiconVersion("none");job.setContextEnabled(true);job.setContextDone(false);queue.insert(job);
        for(int page=1;page<=4;page++) {
            int scan=jdbc.queryForObject("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT id,bah,sjh,'synthetic-context.jpg','synthetic',0,1,? FROM app.mr_archive WHERE id=? RETURNING id",Integer.class,page,archive);
            scans.add(scan);queue.insertItem(job.getId(),scan);
            Fact fact=new Fact();fact.setScanId(scan);fact.setArchiveId(archive);fact.setStatus("SUCCEEDED");fact.setResultVersion(UUID.randomUUID().toString());
            fact.setContentHash("a".repeat(64));fact.setModelChecksum("b".repeat(64));fact.setImageWidth(1000);fact.setImageHeight(1400);fact.setEngineVersion("synthetic");fact.setConfidence(.99);
            String title=page==1?"手术记录":page==2?"续页（背面）":"续页";fact.setOcrText(title);
            fact.setLinesJson(OcrProtocol.JSON.writeValueAsString(List.of(new OcrProtocol.Line(title,.99,List.of(0.,10.,500.,60.)))));queue.saveFact(fact);
            queue.complete(job.getId(),scan,"NO_MATCH","SUCCEEDED",fact.getResultVersion(),null);
            classifier.classify(job,queue.item(job.getId(),scan),fact);
        }
        queue.snapshotOrder(job.getId());queue.planned(job.getId(),4);
    }
    @AfterEach void clear() {OcrTestDataCleanup.purge(jdbc,manager,archive,null);}
    private ClassificationMapper.Suggestion contextual() {
        return classifications.suggestions(job.getId(),0).stream().filter(s->s.getScanId().equals(scans.get(1))).findFirst().orElseThrow();
    }
    @Test void secondPassResumesAndNeverPropagatesBeyondTheIndependentAnchorWindow() {
        service.runOne();service.runOne();assertThat(queue.job(job.getId()).getContextCursor()).isEqualTo(2);
        assertThat(queue.job(job.getId()).getStatus()).isEqualTo("RUNNING");
        assertThat(contextual().isAutoEligible()).isFalse();assertThat(contextual().getReferenceScanId()).isEqualTo(scans.getFirst());
        service.runOne();service.runOne();service.runOne();
        assertThat(queue.job(job.getId()).getStatus()).isEqualTo("SUCCESS");assertThat(queue.job(job.getId()).isContextDone()).isTrue();
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(3);
        assertThat(queue.item(job.getId(),scans.get(3)).getSuggestedType()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.mr_scan WHERE archive_id=? AND btype<>0",Integer.class,archive)).isZero();
    }
    @Test void relationCanBeConfirmedThenRejectedWithRealActorAudit() {
        service.runOne();service.runOne();var suggestion=contextual();
        types.review(actor,suggestion.getId(),"CONFIRM_PAIR",null);
        assertThat(queue.item(job.getId(),scans.getFirst()).getRelationStatus()).isEqualTo("CONFIRMED");
        types.review(actor,suggestion.getId(),"REJECT_PAIR",null);
        assertThat(queue.item(job.getId(),scans.get(1)).getRelationStatus()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForList("SELECT operator_user_id FROM app.mr_ocr_page_relation_audit WHERE job_id=? ORDER BY id",Long.class,job.getId())).containsExactly(actor.getId(),actor.getId());
        assertThat(classifications.scan(scans.get(1)).getBtype()).isZero();
    }
    @Test void changedReferenceOcrRejectsBothPairConfirmationAndClassification() {
        service.runOne();service.runOne();var suggestion=contextual();
        jdbc.update("UPDATE app.mr_scan_ocr SET result_version=? WHERE scan_id=?",UUID.randomUUID().toString(),scans.getFirst());
        assertThatThrownBy(()->types.review(actor,suggestion.getId(),"CONFIRM_PAIR",null)).hasMessageContaining("参考页");
        assertThatThrownBy(()->types.review(actor,suggestion.getId(),"ACCEPT",null)).hasMessageContaining("参考页");
        assertThat(queue.item(job.getId(),scans.get(1)).getRelationStatus()).isEqualTo("SUGGESTED");
    }
    @Test void contextWritesAndCheckpointAreAtomicOnFailure() {
        service.runOne();
        jdbc.execute("ALTER TABLE app.mr_smart_classification_job ADD CONSTRAINT ck_test_context_cursor CHECK(context_cursor<2)");
        try {service.runOne();assertThat(queue.job(job.getId()).getContextCursor()).isEqualTo(1);
            assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
            assertThat(queue.item(job.getId(),scans.getFirst()).getRelatedScanId()).isNull();
        } finally {jdbc.execute("ALTER TABLE app.mr_smart_classification_job DROP CONSTRAINT ck_test_context_cursor");}
        service.runOne();assertThat(contextual().getReasonCode()).isEqualTo("CONTEXT_REVERSE");
    }
    @Test void referenceSourceHashIsRecheckedOutsideTheAuditTransaction() {
        service.runOne();service.runOne();var suggestion=contextual();
        when(input.prepare(eq(scans.getFirst()),any())).thenAnswer(i->new OcrInput.Prepared(temporary.resolve("synthetic-changed"),"c".repeat(64)));
        assertThatThrownBy(()->types.review(actor,suggestion.getId(),"ACCEPT",null)).hasMessageContaining("参考页");
        assertThat(classifications.scan(scans.get(1)).getBtype()).isZero();
    }
    @Test void aNewDuplicateReferencePageInvalidatesTheSuggestion() {
        service.runOne();service.runOne();var suggestion=contextual();
        jdbc.update("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag,pages) SELECT id,bah,sjh,'synthetic-duplicate.jpg','synthetic',0,1,1 FROM app.mr_archive WHERE id=?",archive);
        assertThatThrownBy(()->types.review(actor,suggestion.getId(),"ACCEPT",null)).hasMessageContaining("参考页");
    }
    @Test void duplicatePageNumbersDoNotCreateFalseAdjacency() {
        jdbc.update("UPDATE app.mr_scan SET pages=2 WHERE id=?",scans.get(3));
        for(int i=0;i<5;i++) service.runOne();
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
    }
    @Test void anAlreadyClassifiedPageIsNeverChangedByContext() {
        jdbc.update("UPDATE app.mr_scan SET btype=5,type_version=type_version+1 WHERE id=?",scans.get(1));
        for(int i=0;i<5;i++) service.runOne();
        assertThat(classifications.scan(scans.get(1)).getBtype()).isEqualTo(5);
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
    }
    @Test void aFailedPrecedingItemCannotReuseAnOlderSuccessfulOcrFact() {
        jdbc.update("UPDATE app.mr_smart_classification_job_item SET status='FAILED',ocr_status='FAILED',failure_code='TIMEOUT' WHERE job_id=? AND scan_id=?",job.getId(),scans.get(1));
        for(int i=0;i<5;i++) service.runOne();
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
    }
    @Test void changedPrecedingPageOrderMustNotInventNewAdjacency() {
        jdbc.update("UPDATE app.mr_scan SET pages=2 WHERE id=?",scans.getFirst());
        jdbc.update("UPDATE app.mr_scan SET pages=10 WHERE id=?",scans.get(1));
        for(int i=0;i<5;i++) service.runOne();
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
    }
    @Test void aReferenceOcrVersionOutsideTheFrozenItemIsNotUsed() {
        jdbc.update("UPDATE app.mr_smart_classification_job_item SET ocr_result_version=? WHERE job_id=? AND scan_id=?",UUID.randomUUID().toString(),job.getId(),scans.getFirst());
        for(int i=0;i<5;i++) service.runOne();
        assertThat(classifications.suggestions(job.getId(),0)).hasSize(1);
    }
    @Test void relationConfirmationDoesNotDependOnAcceptingTheTypeFirst() {
        service.runOne();service.runOne();var suggestion=contextual();
        types.review(actor,suggestion.getId(),"ACCEPT",null);
        types.review(actor,suggestion.getId(),"CONFIRM_PAIR",null);
        assertThat(queue.item(job.getId(),scans.get(1)).getRelationStatus()).isEqualTo("CONFIRMED");
        assertThat(classifications.scan(scans.get(1)).getBtype()).isEqualTo(3);
    }
    @Test void anUnrelatedLaterTypeChangeStillPreventsPairConfirmation() {
        service.runOne();service.runOne();var suggestion=contextual();types.review(actor,suggestion.getId(),"ACCEPT",null);
        com.zjcxph.imgapi.utils.AuthContext.setCurrentUser(actor);
        try {types.manual(scans.get(1),5);} finally {com.zjcxph.imgapi.utils.AuthContext.clear();}
        assertThatThrownBy(()->types.review(actor,suggestion.getId(),"CONFIRM_PAIR",null)).hasMessageContaining("已变化");
        assertThat(classifications.scan(scans.get(1)).getBtype()).isEqualTo(5);
    }
}
