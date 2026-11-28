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

    @Test void groupedConcurrentReservationsNeverOverrunAndAllAcceptedOutcomesFit()throws Exception{
        var env=configuration().withProperty("mrr.audit.admission.grouped","true")
                .withProperty("mrr.audit.admission.fast-capacity","true")
                .withProperty("mrr.audit.admission.max-records","16").withProperty("mrr.audit.admission.max-bytes","262144");
        var metrics=new SimpleMeterRegistry();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(16);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var gate=new MedicalAuditAdmission(env,new ObjectMapper(),jdbc,tx,metrics)){
            var futures=new ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<16;i++){
                int n=i;
                futures.add(pool.submit(()->{start.await();try{return gate.admit(999998,"batch11-"+n,"GET","/api/v1/img/content/{id}","[REDACTED]");}
                catch(java.io.IOException rejected){return null;}}));
            }
            start.countDown();
            var accepted=new ArrayList<String>();
            for(var future:futures){String id=future.get(10,java.util.concurrent.TimeUnit.SECONDS);if(id!=null)accepted.add(id);}
            assertThat(accepted).hasSizeBetween(1,8);
            assertThat(gate.reserved()).isEqualTo(accepted.size());
            var done=new ArrayList<java.util.concurrent.Future<?>>();
            for(String id:accepted)done.add(pool.submit(()->gate.complete(id,200,false)));
            for(var future:done)future.get(10,java.util.concurrent.TimeUnit.SECONDS);
            gate.flush();
            assertThat(gate.reserved()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE actor_id=999998",Integer.class)).isEqualTo(accepted.size()*2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT correlation_id FROM app.medical_audit_event WHERE actor_id=999998 GROUP BY correlation_id HAVING count(*)=2) pairs",Integer.class)).isEqualTo(accepted.size());
            System.out.println("MRR_GROUPED_GATE accepted="+accepted.size()+" rejected="+(16-accepted.size())+" forces="+metrics.get("mrr.medical.audit.file.forces").gauge().value());
        }finally{pool.shutdownNow();metrics.close();jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999998");}
    }

    @Test void agedUnpairedMonitorDistinguishesUnknownFromCompleted()throws Exception{
        var env=configuration().withProperty("mrr.audit.admission.grouped","true");
        var metrics=new SimpleMeterRegistry();
        try(var gate=new MedicalAuditAdmission(env,new ObjectMapper(),jdbc,tx,metrics)){
            String id=gate.admit(999997,"batch11-aged","GET","/api/v1/img/content/{id}","[REDACTED]");
            gate.flush();
            jdbc.update("UPDATE app.medical_audit_event SET observed_at=NOW()-interval '11 minutes' WHERE correlation_id=?",id);
            gate.monitorUnpaired();
            assertThat(metrics.get("mrr.medical.audit.aged.unpaired").gauge().value()).isEqualTo(1);
            gate.complete(id,200,false);gate.flush();gate.monitorUnpaired();
            assertThat(metrics.get("mrr.medical.audit.aged.unpaired").gauge().value()).isZero();
        }finally{metrics.close();jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999997");}
    }

    @Test void actualBatchCommitAcknowledgementLossKeepsCiphertextAndReplaysExactly()throws Exception{
        var env=configuration().withProperty("mrr.audit.admission.grouped","true")
                .withProperty("mrr.audit.admission.max-records","16");
        var target=java.net.URI.create(postgresqlJdbcUrl().substring(5));
        String id;
        try{
            try(var proxy=new DurableAuditPostgresqlIT.DropCommitProxy(target.getHost(),target.getPort())){
                String url="jdbc:postgresql://127.0.0.1:"+proxy.port()+target.getPath()+"?currentSchema=app&sslmode=disable&gssEncMode=disable&connectTimeout=3&socketTimeout=5";
                var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,postgresqlUsername(),postgresqlPassword());
                var metrics=new SimpleMeterRegistry();
                try(var gate=new MedicalAuditAdmission(env,new ObjectMapper(),new JdbcTemplate(ds),
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds),metrics)){
                    id=gate.admit(999996,"batch11-wire","GET","/api/v1/img/content/{id}","[REDACTED]");
                    gate.complete(id,200,false);
                    gate.flush();
                    assertThat(proxy.dropped.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE correlation_id=?",Integer.class,id)).isEqualTo(2);
                    assertThat(metrics.get("mrr.medical.audit.pending").gauge().value()).isEqualTo(2);
                }finally{metrics.close();}
            }
            try(var reopened=service(env)){
                reopened.flush();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE correlation_id=?",Integer.class,id)).isEqualTo(2);
                try(var files=Files.list(Path.of(env.getRequiredProperty("mrr.audit.admission.directory")))){
                    assertThat(files.noneMatch(x->x.toString().endsWith(".pending"))).isTrue();
                }
            }
            System.out.println("MRR_MEDICAL_BATCH_ACK actual COMMIT reply lost; two durable events reconciled");
        }finally{jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999996");}
    }

    @Test void actualOutcomeDiskPermissionFailureRecoversOriginalObservation()throws Exception{
        var env=configuration().withProperty("mrr.audit.admission.grouped","true")
                .withProperty("mrr.audit.admission.fast-capacity","true");
        var root=Path.of(env.getRequiredProperty("mrr.audit.admission.directory"));
        var metrics=new SimpleMeterRegistry();
        try(var gate=new MedicalAuditAdmission(env,new ObjectMapper(),jdbc,tx,metrics)){
            String id=gate.admit(999995,"batch12-outcome","GET","/api/v1/img/content/{id}","[REDACTED]");
            gate.flush();
            Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("r-x------"));
            try{
                gate.complete(id,206,true);
                assertThat(gate.reserved()).isEqualTo(1);
                assertThatThrownBy(()->gate.admit(999995,"blocked","GET","/api/v1/img/content/{id}","[REDACTED]")).isInstanceOf(java.io.IOException.class);
                gate.complete(id,200,false); // duplicate callback must not overwrite original observation
            }finally{Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"));}
            gate.flush();gate.flush();
            assertThat(gate.reserved()).isZero();
            assertThat(jdbc.queryForObject("SELECT observation||':'||http_status FROM app.medical_audit_event WHERE correlation_id=? AND phase='OUTCOME'",String.class,id)).isEqualTo("HTTP_EXCEPTION:206");
            String next=gate.admit(999995,"after-recovery","GET","/api/v1/img/content/{id}","[REDACTED]");
            gate.complete(next,200,false);gate.flush();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE actor_id=999995",Integer.class)).isEqualTo(4);
            System.out.println("MRR_OUTCOME_RECOVERY real directory write failure; preserved first observed 206 exception; resumed");
        }finally{metrics.close();jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999995");}
    }

    @Test void independentInstanceSpoolsDeliverToSameLedgerWithoutSharedDirectoryOwnership()throws Exception{
        var first=configuration().withProperty("mrr.audit.admission.grouped","true")
                .withProperty("mrr.audit.admission.fast-capacity","true");
        var secondRoot=Files.createDirectory(temp.toRealPath().resolve("second"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var second=new MockEnvironment().withProperty("MRR_MEDICAL_AUDIT_KEY",first.getRequiredProperty("MRR_MEDICAL_AUDIT_KEY"))
                .withProperty("mrr.audit.admission.directory",secondRoot.toString())
                .withProperty("mrr.audit.admission.max-records","2").withProperty("mrr.audit.admission.max-bytes","65536")
                .withProperty("mrr.audit.admission.grouped","true").withProperty("mrr.audit.admission.fast-capacity","true");
        try(var a=service(first);var b=service(second)){
            assertThatThrownBy(()->service(first)).isInstanceOf(java.io.IOException.class);
            String one=a.admit(999994,"instance-a","GET","/api/v1/img/content/{id}","[REDACTED]");
            String two=b.admit(999994,"instance-b","GET","/api/v1/img/content/{id}","[REDACTED]");
            assertThat(one).isNotEqualTo(two);
            a.complete(one,200,false);b.complete(two,206,false);
            var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
            try{
                var f=workers.submit(a::flush);var g=workers.submit(b::flush);
                f.get(10,java.util.concurrent.TimeUnit.SECONDS);g.get(10,java.util.concurrent.TimeUnit.SECONDS);
            }finally{workers.shutdownNow();}
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE actor_id=999994",Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT count(DISTINCT correlation_id) FROM app.medical_audit_event WHERE actor_id=999994",Integer.class)).isEqualTo(2);
            assertThat(a.reserved()).isZero();assertThat(b.reserved()).isZero();
            System.out.println("MRR_MEDICAL_INSTANCES private spools shared ledger, four exact events; shared directory refused");
        }finally{jdbc.update("DELETE FROM app.medical_audit_event WHERE actor_id=999994");}
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
