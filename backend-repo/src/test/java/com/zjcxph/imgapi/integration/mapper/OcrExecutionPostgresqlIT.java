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
