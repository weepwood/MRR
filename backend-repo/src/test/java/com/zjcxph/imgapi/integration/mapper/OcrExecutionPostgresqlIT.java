package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.OcrMapper;
import com.zjcxph.imgapi.ocr.OcrModels.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml", "mybatis.configuration.map-underscore-to-camel-case=true"})
class OcrExecutionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired OcrMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void currentFactRequiresMatchingArchiveAndValidImage(){
        Job current=job(), historical=job();int scan=scan(current.getArchiveId());
        Fact original=fact(scan,current.getArchiveId());mapper.saveFact(original);
        assertThat(mapper.factForCurrentArchive(scan,current.getArchiveId()).getOcrText()).isEqualTo(original.getOcrText());
        jdbc.update("UPDATE app.mr_scan_ocr SET archive_id=? WHERE scan_id=?",historical.getArchiveId(),scan);
        assertThat(mapper.factForCurrentArchive(scan,current.getArchiveId())).isNull();
        assertThat(mapper.factForCurrentArchive(scan,historical.getArchiveId())).isNull();
        assertThat(mapper.fact(scan).getOcrText()).isEqualTo(original.getOcrText());
        jdbc.update("UPDATE app.mr_scan_ocr SET archive_id=? WHERE scan_id=?",current.getArchiveId(),scan);
        jdbc.update("UPDATE app.mr_scan SET uploadflag=0 WHERE id=?",scan);
        assertThat(mapper.factForCurrentArchive(scan,current.getArchiveId())).isNull();
    }

    @Autowired com.zjcxph.imgapi.mapper.ScanMapper scans;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void workerReusesFactOnlyForCurrentArchive(boolean sameArchive,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)throws Exception{
        Job current=job(), historical=job();
        int scan=scan(current.getArchiveId());
        String hash="a".repeat(64),fingerprint="b".repeat(64);
        Fact old=fact(scan,sameArchive?current.getArchiveId():historical.getArchiveId());
        old.setModelChecksum(fingerprint);old.setPreprocessingVersion("rgb-exif-v1");
        mapper.saveFact(old);
        current.setConfigSnapshot(com.zjcxph.imgapi.ocr.OcrProtocol.JSON.writeValueAsString(
                new Config("synthetic",fingerprint,30,65536,0.7)));
        mapper.insert(current);mapper.insertItem(current.getId(),scan);mapper.planned(current.getId(),1);
        var access=org.mockito.Mockito.mock(com.zjcxph.imgapi.security.MedicalAccess.class);
        var engine=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrEngine.class);
        var input=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrInput.class);
        var settings=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.SystemSettingService.class);
        var classification=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ClassificationService.class);
        var guard=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrLoadGuard.class);
        var path=java.nio.file.Files.createFile(temp.resolve("synthetic.ocr"));
        org.mockito.Mockito.when(settings.getSetting("ocrEnabled")).thenReturn("true");
        org.mockito.Mockito.when(engine.fingerprint("synthetic")).thenReturn(fingerprint);
        org.mockito.Mockito.when(input.prepare(org.mockito.ArgumentMatchers.eq(scan),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.zjcxph.imgapi.ocr.OcrInput.Prepared(path,hash));
        org.mockito.Mockito.when(engine.recognize(org.mockito.ArgumentMatchers.eq("synthetic"),org.mockito.ArgumentMatchers.eq(path),
                org.mockito.ArgumentMatchers.eq(30),org.mockito.ArgumentMatchers.eq(65536),org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.zjcxph.imgapi.ocr.OcrProtocol.Result("synthetic",1,1,java.util.List.of(),"synthetic"));
        var service=new com.zjcxph.imgapi.service.OcrJobService(mapper,scans,access,engine,input,settings,
                new org.springframework.transaction.support.TransactionTemplate(transactionManager),classification,guard);
        service.runOne();
        Fact actual=mapper.fact(scan);
        assertThat(actual.getArchiveId()).isEqualTo(current.getArchiveId());
        if(sameArchive){
            assertThat(actual.getResultVersion()).isEqualTo(old.getResultVersion());
            org.mockito.Mockito.verify(engine,org.mockito.Mockito.never()).recognize(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any());
        }else{
            assertThat(actual.getResultVersion()).isNotEqualTo(old.getResultVersion());
            assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan_ocr_history WHERE result_version=?",Long.class,old.getResultVersion()))
                    .isEqualTo(historical.getArchiveId());
            org.mockito.Mockito.verify(engine).recognize(org.mockito.ArgumentMatchers.eq("synthetic"),
                    org.mockito.ArgumentMatchers.eq(path),org.mockito.ArgumentMatchers.eq(30),org.mockito.ArgumentMatchers.eq(65536),org.mockito.ArgumentMatchers.any());
        }
        var item=mapper.items(current.getId(),0,50).getFirst();
        assertThat(item.getOcrResultVersion()).isEqualTo(actual.getResultVersion());
        org.mockito.Mockito.verify(classification).classify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(f->current.getArchiveId().equals(f.getArchiveId())));
        assertThat(mapper.job(current.getId()).getStatus()).isEqualTo("SUCCESS");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"revoke", "source", "version", "cancel", "fallback-revoke", "fallback-version", "fallback-cancel"})
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void committedOcrFactDoesNotAuthorizeStaleClassification(String change,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        Job current=job(); int scan=scan(current.getArchiveId());
        long actor=jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,password_hash,role_code,status) VALUES (?,'synthetic','ADMIN','active') RETURNING id",Long.class,"synthetic-ocr-actor-"+UUID.randomUUID());
        current.setOwnerUserId(actor);
        String hash="a".repeat(64),fingerprint="b".repeat(64);
        current.setConfigSnapshot(com.zjcxph.imgapi.ocr.OcrProtocol.JSON.writeValueAsString(new Config("synthetic",fingerprint,30,65536,.7)));
        String replacement=UUID.randomUUID().toString();
        try {
            mapper.insert(current);mapper.insertItem(current.getId(),scan);mapper.planned(current.getId(),1);
            var engine=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrEngine.class);
            var input=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrInput.class);
            var settings=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.SystemSettingService.class);
            var classification=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ClassificationService.class);
            var guard=org.mockito.Mockito.mock(com.zjcxph.imgapi.ocr.OcrLoadGuard.class);
            var path=java.nio.file.Files.createFile(temp.resolve("synthetic.ocr"));
            org.mockito.Mockito.when(settings.getSetting("ocrEnabled")).thenReturn("true");
            org.mockito.Mockito.when(engine.fingerprint("synthetic")).thenReturn(fingerprint);
            org.mockito.Mockito.when(input.prepare(org.mockito.ArgumentMatchers.eq(scan),org.mockito.ArgumentMatchers.any())).thenReturn(new com.zjcxph.imgapi.ocr.OcrInput.Prepared(path,hash));
            org.mockito.Mockito.when(engine.recognize(org.mockito.ArgumentMatchers.eq("synthetic"),org.mockito.ArgumentMatchers.eq(path),org.mockito.ArgumentMatchers.eq(30),org.mockito.ArgumentMatchers.eq(65536),org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new com.zjcxph.imgapi.ocr.OcrProtocol.Result("synthetic",1,1,java.util.List.of(),"synthetic"));
            boolean fallback=change.startsWith("fallback-");
            String mutation=fallback?change.substring("fallback-".length()):change;
            if(fallback) org.mockito.Mockito.doThrow(new com.zjcxph.imgapi.ocr.OcrFailure("CLASSIFICATION_FAILED"))
                    .when(classification).classify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager) {
                private boolean changed;
                @Override public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> callback) {
                    T result;
                    try { result=super.execute(callback); }
                    catch(com.zjcxph.imgapi.ocr.OcrFailure failure) {
                        if(fallback && !changed && "CLASSIFICATION_FAILED".equals(failure.code())) mutate();
                        throw failure;
                    }
                    if (!fallback && !changed && Boolean.TRUE.equals(result)) mutate();
                    return result;
                }
                private void mutate() {
                        changed=true;
                        assertThat(mapper.fact(scan)).isNotNull();
                        // Actual independent committed database mutation between the two service transactions.
                        switch(mutation) {
                            case "revoke" -> jdbc.update("UPDATE app.mr_auth_user SET status='disabled' WHERE id=?",actor);
                            case "source" -> jdbc.update("UPDATE app.mr_scan SET uploadflag=0 WHERE id=?",scan);
                            case "version" -> jdbc.update("UPDATE app.mr_scan_ocr SET result_version=? WHERE scan_id=?",replacement,scan);
                            case "cancel" -> mapper.cancel(current.getId());
                            default -> throw new AssertionError(change);
                        }
                }
            };
            var service=new com.zjcxph.imgapi.service.OcrJobService(mapper,scans,new com.zjcxph.imgapi.security.MedicalAccess(users,roles),engine,input,settings,tx,classification,guard);
            service.runOne();
            if(fallback) org.mockito.Mockito.verify(classification).classify(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            else org.mockito.Mockito.verifyNoInteractions(classification);
            var item=mapper.items(current.getId(),0,50).getFirst();
            assertThat(item.getOcrResultVersion()).isNull();
            if("cancel".equals(mutation)) {
                assertThat(item.getStatus()).isEqualTo("CANCELLED");
                assertThat(mapper.job(current.getId()).getStatus()).isEqualTo("CANCELLED");
            } else {
                assertThat(item.getStatus()).isEqualTo("FAILED");
                assertThat(item.getFailureCode()).isEqualTo("revoke".equals(mutation)?"AUTH_REVOKED":"SOURCE_CHANGED");
                assertThat(mapper.job(current.getId()).getStatus()).isEqualTo("FAILED");
            }
            assertThat(mapper.job(current.getId()).getLeaseToken()).isNull();
            assertThat(mapper.fact(scan)).isNotNull();
            if ("version".equals(mutation)) assertThat(mapper.fact(scan).getResultVersion()).isEqualTo(replacement);
        } finally {
            com.zjcxph.imgapi.integration.OcrTestDataCleanup.purge(jdbc,transactionManager,current.getArchiveId(),null);
            jdbc.update("DELETE FROM app.mr_auth_user WHERE id=?",actor);
        }
    }

    @Autowired com.zjcxph.imgapi.mapper.AuthUserMapper users;
    @Autowired com.zjcxph.imgapi.mapper.AuthRoleMapper roles;

    @Test void claimsWithFencingAndRecoversExpiredPageWithoutLosingProgress() {
        Job job=job(); int scan=scan(job.getArchiveId()); mapper.insert(job); mapper.insertItem(job.getId(),scan); mapper.planned(job.getId(),1);
        Job claimed=mapper.claim("token-one"); assertThat(claimed.getId()).isEqualTo(job.getId());
        assertThat(mapper.claim("token-two")).isNull();
        assertThat(mapper.processing(job.getId(),scan,"token-one")).isEqualTo(1);
        jdbc.update("UPDATE app.mr_smart_classification_job SET lease_until=NOW()-INTERVAL '1 second' WHERE id=?",job.getId());
        assertThat(mapper.claim("token-two").getLeaseToken()).isEqualTo("token-two");
        assertThat(mapper.alive(job.getId(),"token-one")).isFalse();
        assertThat(mapper.next(job.getId()).getScanId()).isEqualTo(scan);
        mapper.complete(job.getId(),scan,"NO_MATCH","SUCCEEDED","result",null);
        mapper.release(job.getId(),"token-one"); assertThat(mapper.job(job.getId()).getStatus()).isEqualTo("RUNNING");
        mapper.release(job.getId(),"token-two"); assertThat(mapper.job(job.getId()).getStatus()).isEqualTo("SUCCESS");
        assertThat(mapper.job(job.getId()).getProcessedCount()).isEqualTo(1);
    }

    @Test void preservesPreviousOcrTextWhenReplacingCurrentVersion() {
        Job job=job(); int scan=scan(job.getArchiveId());
        Fact first=fact(scan,job.getArchiveId()); mapper.saveFact(first);
        mapper.archiveFact(scan,UUID.randomUUID().toString());
        Fact second=fact(scan,job.getArchiveId()); second.setOcrText("合成第二版本"); mapper.saveFact(second);
        assertThat(mapper.fact(scan).getOcrText()).isEqualTo("合成第二版本");
        assertThat(jdbc.queryForObject("SELECT ocr_text FROM app.mr_scan_ocr_history WHERE result_version=?",String.class,first.getResultVersion())).isEqualTo("合成测试文字");
    }
    @Test void lateWorkerCannotReopenCompletedPageAfterLeaseTakeover() {
        Job job=job();int scan=scan(job.getArchiveId());mapper.insert(job);mapper.insertItem(job.getId(),scan);mapper.planned(job.getId(),1);
        mapper.claim("old-worker");assertThat(mapper.next(job.getId()).getScanId()).isEqualTo(scan);
        jdbc.update("UPDATE app.mr_smart_classification_job SET lease_until=NOW()-INTERVAL '1 second' WHERE id=?",job.getId());
        mapper.claim("new-worker");mapper.complete(job.getId(),scan,"NO_MATCH","SUCCEEDED","result",null);mapper.release(job.getId(),"new-worker");
        assertThat(mapper.processing(job.getId(),scan,"old-worker")).isZero();
        assertThat(mapper.items(job.getId(),0,50).getFirst().getStatus()).isEqualTo("NO_MATCH");
        assertThat(mapper.job(job.getId()).getStatus()).isEqualTo("SUCCESS");
        assertThat(mapper.job(job.getId()).getProcessedCount()).isEqualTo(1);
    }
    @Test void pageStartRequiresCurrentUnexpiredUncancelledLease() {
        Job job=job();int scan=scan(job.getArchiveId());mapper.insert(job);mapper.insertItem(job.getId(),scan);mapper.planned(job.getId(),1);
        mapper.claim("old-worker");
        jdbc.update("UPDATE app.mr_smart_classification_job SET lease_until=NOW()-INTERVAL '1 second' WHERE id=?",job.getId());
        assertThat(mapper.processing(job.getId(),scan,"old-worker")).isZero();
        mapper.claim("new-worker");
        assertThat(mapper.processing(job.getId(),scan,"old-worker")).isZero();
        assertThat(mapper.items(job.getId(),0,50).getFirst().getAttempts()).isZero();
        assertThat(mapper.processing(job.getId(),scan,"new-worker")).isEqualTo(1);
        mapper.cancel(job.getId());
        assertThat(mapper.processing(job.getId(),scan,"new-worker")).isZero();
        assertThat(mapper.items(job.getId(),0,50).getFirst().getAttempts()).isEqualTo(1);
    }

    @Test void rejectsConcurrentActiveJobsForSameArchiveAtDatabaseBoundary() {
        Job first=job(); mapper.insert(first);
        Job second=job(); second.setArchiveId(first.getArchiveId());
        assertThatThrownBy(()->mapper.insert(second)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test void candidatesExcludeDeletedClassifiedAndWrongArchiveLinks() {
        Job job=job(); int valid=scan(job.getArchiveId()); int classified=scan(job.getArchiveId()); int deleted=scan(job.getArchiveId());
        jdbc.update("UPDATE app.mr_scan SET btype=1 WHERE id=?",classified);
        jdbc.update("UPDATE app.mr_scan SET uploadflag=0 WHERE id=?",deleted);
        assertThat(mapper.candidates(job.getArchiveId(),null)).containsExactly(valid);
        assertThat(mapper.belongs(valid,job.getArchiveId()+9999)).isFalse();
    }

    private Job job() {
        String code=UUID.randomUUID().toString();
        Long archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES(?,?) RETURNING id",Long.class,code,code);
        Job job=new Job();job.setId(UUID.randomUUID().toString());job.setArchiveId(archive);job.setOwnerUserId(999L);
        job.setCreatedBy("synthetic-operator");job.setConfigSnapshot("{}");job.setRuleSetVersion("none");job.setLexiconVersion("none");return job;
    }
    private int scan(Long archive) {
        return jdbc.queryForObject("INSERT INTO app.mr_scan(archive_id,bah,sjh,filename,folder,btype,uploadflag) SELECT id,bah,sjh,'synthetic.jpg','synthetic',0,1 FROM app.mr_archive WHERE id=? RETURNING id",Integer.class,archive);
    }
    private Fact fact(int scan,Long archive) {
        Fact fact=new Fact();fact.setScanId(scan);fact.setArchiveId(archive);fact.setStatus("SUCCEEDED");
        fact.setResultVersion(UUID.randomUUID().toString());fact.setOcrText("合成测试文字");fact.setLinesJson("[]");
        fact.setContentHash("a".repeat(64));fact.setConfidence(0.99);return fact;
    }
}
