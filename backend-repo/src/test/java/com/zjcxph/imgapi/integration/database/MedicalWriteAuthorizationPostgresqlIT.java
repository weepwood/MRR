package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.req.PatientUpdateRequest;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.*;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.service.impl.*;
import com.zjcxph.imgapi.utils.AuthContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.Mockito.mock;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties={"mybatis.mapper-locations=classpath*:mapper/*.xml","mybatis.configuration.map-underscore-to-camel-case=true"})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class MedicalWriteAuthorizationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchMapper patients;
    @Autowired ScanMapper scans;
    @Autowired ArchiveBoxRecordMapper boxes;
    @Autowired AuthUserMapper users;
    @Autowired AuthRoleMapper roles;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest
    @ValueSource(strings={"patient","scan-update","scan-delete","box-delete","repair-apply"})
    void committedRevocationPreventsAlreadyAdmittedMedicalWrite(String operation) {
        String role="MED_WRITE_"+UUID.randomUUID();
        String jti="synthetic-"+UUID.randomUUID();
        String hash=TokenBlacklist.tokenHash(jti);
        jdbc.update("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES (?,?,'record:manage,system:manage')",role,role);
        long actor=jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,password_hash,role_code,status,password_version) VALUES (?,'synthetic',?,'active',1) RETURNING id",Long.class,"synthetic-"+UUID.randomUUID(),role);
        long proposer=jdbc.queryForObject("INSERT INTO app.mr_auth_user(username,password_hash,role_code,status,password_version) VALUES (?,'synthetic',?,'active',1) RETURNING id",Long.class,"synthetic-"+UUID.randomUUID(),role);
        String bah="7654318";
        int patient=0,scan=0;long box=0,archive=0,run=0,order=0;
        var tx=new TransactionTemplate(manager);
        var access=new com.zjcxph.imgapi.security.MedicalAccess(users,roles);
        var patientService=new PatientService(patients,access);
        var scanService=new ScanServiceImpl(scans,mock(ImageTypeService.class),access);
        var boxService=new ArchiveBoxRecordServiceImpl(boxes,access);
        var repair=new DataQualityRepairOrderService(jdbc,new DataQualityServiceImpl(jdbc,new SimpleMeterRegistry()),mock(DeploymentReadinessService.class),mock(MaintenanceModeService.class),true,access);
        try {
            patient=jdbc.queryForObject("INSERT INTO app.mr_patient(bah,name) VALUES (?,'synthetic-original') RETURNING id",Integer.class,bah);
            scan=jdbc.queryForObject("INSERT INTO app.mr_scan(bah,filename,uploadflag) VALUES (?,'synthetic-original.png',1) RETURNING id",Integer.class,bah);
            box=jdbc.queryForObject("INSERT INTO app.mr_archive_box_record(bah,box_no,status) VALUES (?,'synthetic-box','NORMAL') RETURNING id",Long.class,bah);
            if("repair-apply".equals(operation)) {
                archive=jdbc.queryForObject("INSERT INTO app.mr_archive(bah) VALUES (?) RETURNING id",Long.class,bah);
                run=jdbc.queryForObject("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','synthetic') RETURNING id",Long.class);
                long issue=jdbc.queryForObject("""
                    INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_type,entity_id,bah,detail)
                    VALUES (?,'SCAN_ARCHIVE_LINK_MISSING_ESTIMATED','synthetic','WARNING','mr_scan',?,?,'synthetic') RETURNING id
                    """,Long.class,run,Integer.toString(scan),bah);
                var submitter=new AuthSession();submitter.setId(proposer);submitter.setPasswordVersion(1);
                AuthContext.setCurrentUser(submitter);
                order=tx.execute(t->repair.propose(issue,proposer)).id();
                final long target=order;
                var approver=new AuthSession();approver.setId(actor);approver.setPasswordVersion(1);
                AuthContext.setCurrentUser(approver,hash);
                tx.execute(t->repair.approve(target,actor));
            }
            // The in-flight request has already passed admission; this is an actual committed revocation.
            var admitted=new AuthSession();admitted.setId(actor);admitted.setPasswordVersion(1);
            AuthContext.setCurrentUser(admitted,hash);
            if("scan-delete".equals(operation)||"repair-apply".equals(operation))
                jdbc.update("UPDATE app.mr_auth_role SET permissions='record:read' WHERE code=?",role);
            else new TokenBlacklist(jdbc).revoke(jti,System.currentTimeMillis()+60000);
            int p=patient,s=scan;long b=box,o=order;
            assertThatThrownBy(()->tx.executeWithoutResult(t->{
                switch(operation) {
                    case "patient" -> {var req=new PatientUpdateRequest();req.setBah(bah);req.setName("synthetic-changed");patientService.update(p,req);}
                    case "scan-update" -> {var req=new Scan();req.setId(s);req.setFilename("synthetic-changed.png");scanService.update(req);}
                    case "scan-delete" -> scanService.softDeleteById(s);
                    case "box-delete" -> boxService.delete(b);
                    case "repair-apply" -> repair.apply(o,actor);
                    default -> throw new AssertionError(operation);
                }
            })).isInstanceOf(BusinessException.class).satisfies(f->assertThat(((BusinessException)f).getCode()).isEqualTo(403));
            assertThat(jdbc.queryForObject("SELECT name FROM app.mr_patient WHERE id=?",String.class,patient)).isEqualTo("synthetic-original");
            assertThat(jdbc.queryForObject("SELECT filename FROM app.mr_scan WHERE id=?",String.class,scan)).isEqualTo("synthetic-original.png");
            assertThat(jdbc.queryForObject("SELECT uploadflag FROM app.mr_scan WHERE id=?",Integer.class,scan)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mr_archive_box_record WHERE id=?",Long.class,box)).isEqualTo(1L);
            if(order!=0) {
                assertThat(repair.get(order).status()).isEqualTo("APPROVED");
                assertThat(jdbc.queryForObject("SELECT archive_id FROM app.mr_scan WHERE id=?",Long.class,scan)).isNull();
                assertThat(repair.events(order,0)).hasSize(2);
            }
        } finally {
            AuthContext.clear();
            if(order!=0) {
                jdbc.update("DELETE FROM app.mrr_data_quality_repair_event WHERE order_id=?",order);
                jdbc.update("DELETE FROM app.mrr_data_quality_repair_order WHERE id=?",order);
            }
            if(run!=0) jdbc.update("DELETE FROM app.mrr_data_quality_run WHERE id=?",run);
            if(box!=0) jdbc.update("DELETE FROM app.mr_archive_box_record WHERE id=?",box);
            if(scan!=0) jdbc.update("DELETE FROM app.mr_scan WHERE id=?",scan);
            if(patient!=0) jdbc.update("DELETE FROM app.mr_patient WHERE id=?",patient);
            if(archive!=0) jdbc.update("DELETE FROM app.mr_archive WHERE id=?",archive);
            jdbc.update("DELETE FROM app.token_revocation WHERE token_hash=?",hash);
            jdbc.update("DELETE FROM app.mr_auth_user WHERE id IN (?,?)",actor,proposer);
            jdbc.update("DELETE FROM app.mr_auth_role WHERE code=?",role);
        }
    }
}
