package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.security.*;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class MedicalImportAuthorizationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthUserMapper users;
    @Autowired AuthRoleMapper roles;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest
    @ValueSource(strings={"patient","statistics","scan","box"})
    void committedRevocationDuringParsingPreventsPersistentImport(String dataset) throws Exception {
        String role="IMPORT_TEST_"+UUID.randomUUID();
        String jti="synthetic-"+UUID.randomUUID(),hash=TokenBlacklist.tokenHash(jti);
        String bah="7654317",sjh="987654317";
        jdbc.update("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES (?,?,'record:edit')",role,role);
        long actor=jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,password_hash,role_code,status,password_version) VALUES (?,'synthetic',?,'active',1) RETURNING id",Long.class,"synthetic-"+UUID.randomUUID(),role);
        var tx=new TransactionTemplate(manager);
        var independent=new TransactionTemplate(manager);
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var session=new AuthSession();session.setId(actor);session.setRoleCode(role);session.setPasswordVersion(1);
        AuthContext.setCurrentUser(session,hash);
        var reader=spy(new TabularImportFileReader());
        var revoked=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            Object parsed=call.callRealMethod();
            if(revoked.compareAndSet(false,true)) independent.executeWithoutResult(status->{
                // Revoke after file parsing, before persistent writes, using a separate committed transaction.
                if("patient".equals(dataset)||"scan".equals(dataset))
                    new TokenBlacklist(jdbc).revoke(jti,System.currentTimeMillis()+60000);
                else jdbc.update("UPDATE app.mr_auth_role SET permissions='record:read' WHERE code=?",role);
            });
            return parsed;
        }).when(reader).read(any(),anyLong(),anyInt());
        var access=new MedicalAccess(users,roles);
        var patients=new PatientImportService(jdbc,reader,access);
        var statistics=new StatisticsDataExchangeService(jdbc,reader,access);
        var scans=new ScanDataExchangeService(jdbc,reader,mock(org.springframework.cache.CacheManager.class),access);
        var boxes=new ArchiveBoxDataExchangeService(jdbc,reader,access);
        String csv=switch(dataset) {
            case "patient" -> "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\n"+bah+",synthetic,,,,,,\n";
            case "statistics" -> "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate\n"+bah+",C01,U01,2026-01-02,synthetic,12,"+sjh+",synthetic,,,\n";
            case "scan" -> "sjh,bah,brxh,folder,filename,btype,filesize\n"+sjh+","+bah+",,synthetic-import,one.jpg,0,10\n";
            case "box" -> "bah,sjh,box_no,expected_box_no,status,remark\n"+bah+","+sjh+",A,A,NORMAL,\n";
            default -> throw new AssertionError(dataset);
        };
        var file=new MockMultipartFile("file","synthetic.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        java.util.function.Function<Boolean,Boolean> run=dryRun->tx.execute(status->{
            try {
                return switch(dataset) {
                    case "patient" -> patients.importPatients(file,dryRun).canImport();
                    case "statistics" -> statistics.importStatistics(file,dryRun).canImport();
                    case "scan" -> scans.importScans(file,dryRun).canImport();
                    case "box" -> boxes.importArchiveBoxes(file,dryRun).canImport();
                    default -> throw new AssertionError(dataset);
                };
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        try {
            assertThatThrownBy(()->run.apply(false)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo(403));
            assertThat(revoked).isTrue();
            for(String table:new String[]{"mr_patient","mr_statistics","mr_scan","mr_archive_box_record","mr_archive"})
                assertThat(jdbc.queryForObject("SELECT count(*) FROM app."+table+" WHERE bah=?",Long.class,bah)).as(table).isZero();
            // Preview remains admission-only; valid authorized import still persists.
            assertThat(run.apply(true)).isTrue();
            jdbc.update("DELETE FROM app.token_revocation WHERE token_hash=?",hash);
            jdbc.update("UPDATE app.mr_auth_role SET permissions='record:edit' WHERE code=?",role);
            assertThat(run.apply(false)).isTrue();
            String table=switch(dataset){case "patient"->"mr_patient";case "statistics"->"mr_statistics";case "scan"->"mr_scan";default->"mr_archive_box_record";};
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app."+table+" WHERE bah=?",Long.class,bah)).isEqualTo(1L);
        } finally {
            AuthContext.clear();
            for(String table:new String[]{"mr_patient","mr_statistics","mr_scan","mr_archive_box_record","mr_archive"})
                jdbc.update("DELETE FROM app."+table+" WHERE bah=?",bah);
            jdbc.update("DELETE FROM app.token_revocation WHERE token_hash=?",hash);
            jdbc.update("DELETE FROM app.mr_auth_user WHERE id=?",actor);
            jdbc.update("DELETE FROM app.mr_auth_role WHERE code=?",role);
        }
    }
}
