package com.zjcxph.imgapi.integration.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.audit.MedicalAuditAdmission;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@EnabledOnOs({OS.MAC,OS.LINUX})
class MedicalAuditAdmissionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @TempDir Path temp;
    private MockEnvironment configuration()throws Exception{
        Path root=Files.createDirectory(temp.toRealPath().resolve("medical"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        byte[] key=new byte[32];key[0]=42;
        return new MockEnvironment().withProperty("MRR_MEDICAL_AUDIT_KEY",Base64.getEncoder().encodeToString(key))
                .withProperty("mrr.audit.admission.directory",root.toString())
                .withProperty("mrr.audit.admission.max-records","2")
                .withProperty("mrr.audit.admission.max-bytes","65536");
    }
    private MedicalAuditAdmission service(MockEnvironment env)throws Exception{
        return new MedicalAuditAdmission(env,new ObjectMapper(),jdbc,tx,new SimpleMeterRegistry());
    }
    @Test void durableIntentReservesOutcomeAndIncompleteIntentSurvivesReopen()throws Exception{
        MockEnvironment env=configuration();String id;
        try(var gate=service(env)){
            id=gate.admit(999999,"synthetic-gate","GET","/api/v1/img/content/{id}","[REDACTED]");
            assertThat(gate.reserved()).isEqualTo(1);
            assertThatThrownBy(()->gate.admit(999999,"rejected","GET","/api/v1/img/content/{id}","[REDACTED]"))
                    .isInstanceOf(java.io.IOException.class);
            gate.flush();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE correlation_id=? AND phase='INTENT'",Integer.class,id)).isEqualTo(1);
            // Simulate completion after HTTP response; reservation guaranteed logical space.
            gate.complete(id,200,false);gate.complete(id,200,false);gate.flush();
            assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE correlation_id=? ORDER BY phase",String.class,id))
                    .containsExactly("INTENT","OUTCOME");
            assertThat(gate.reserved()).isZero();
            String unknown=gate.admit(999999,"synthetic-unfinished","POST","/api/v1/patients/{id}","[REDACTED]");
            // Close before OUTCOME. No fabricated result or replayed medical operation.
            try{assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE correlation_id=?",Integer.class,unknown)).isZero();}
            finally{}
        }
        try(var reopened=service(env)){
            reopened.flush();
            assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id='synthetic-unfinished'",String.class)).containsExactly("INTENT");
        }finally{jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999999");}
    }

    @Test void rejectsCrossDomainKeyReuseBeforeOpeningSpool()throws Exception{
        var env=configuration();
        String key=env.getRequiredProperty("MRR_MEDICAL_AUDIT_KEY");
        env.withProperty("MRR_AUDIT_SPOOL_KEY_RING","{\"old\":\""+key+"\"}");
        assertThatThrownBy(()->service(env)).isInstanceOf(java.io.IOException.class);
    }

    @Test void actualChildJvmTerminationLeavesOnlyDurableIntent()throws Exception{
        MockEnvironment env=configuration();
        Path root=Path.of(env.getRequiredProperty("mrr.audit.admission.directory"));
        Path output=temp.resolve("child-gate.log");
        String executable=Path.of(System.getProperty("java.home"),"bin","java").toString();
        Process child=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),Child.class.getName(),root.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try{
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<end&&child.isAlive()&&!Files.readString(output).contains("INTENT_DURABLE"))
                Thread.sleep(20);
            assertThat(Files.readString(output)).contains("INTENT_DURABLE");
            child.destroyForcibly();
            assertThat(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try(var reopened=service(env)){
                reopened.flush();
                assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id='synthetic-kill'",String.class))
                    .containsExactly("INTENT");
                assertThat(reopened.reserved()).isZero();
            }
            System.out.println("MRR_MEDICAL_KILL child JVM terminated after durable admission, only unresolved intent recovered");
        }finally{
            if(child.isAlive())child.destroyForcibly();
            jdbc.update("DELETE FROM app.medical_audit_event WHERE request_id='synthetic-kill'");
        }
    }
    public static class Child {
        public static void main(String[] args)throws Exception{
            byte[] key=new byte[32];key[0]=42;
            var env=new MockEnvironment().withProperty("MRR_MEDICAL_AUDIT_KEY",Base64.getEncoder().encodeToString(key))
                    .withProperty("mrr.audit.admission.directory",args[0])
                    .withProperty("mrr.audit.admission.max-records","2")
                    .withProperty("mrr.audit.admission.max-bytes","65536");
            // No database call before admission; use a never-contacted local placeholder.
            var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/unused");
            var gate=new MedicalAuditAdmission(env,new ObjectMapper(),new JdbcTemplate(ds),
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds),new SimpleMeterRegistry());
            gate.admit(999999,"synthetic-kill","POST","/api/v1/patients/{id}","[REDACTED]");
            System.out.println("INTENT_DURABLE");System.out.flush();
            Thread.sleep(60000);
        }
    }
}
