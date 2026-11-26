package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.audit.DurableAuditDelivery;
import com.zjcxph.imgapi.audit.EncryptedAuditSpool;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@MybatisTest(properties="mybatis.configuration.map-underscore-to-camel-case=true")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class DurableAuditPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired LogMapper mapper;
    @Autowired com.zjcxph.imgapi.mapper.ScanMapper scans;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    @TempDir Path temp;
    private Log row(String id) {
        var log=new Log(); log.setRequestId(id);log.setAccessTime(new Date());
        log.setUsername("synthetic");log.setRequestUri("/synthetic");log.setMethod("GET");
        log.setResponseStatus("200");log.setRequestBody("SYNTHETIC_BODY");log.setAuditTarget("synthetic");
        return log;
    }

    @Test void twoCooperatingWritersSerializeAndRejectIdentityConflicts() throws Exception {
        var delivery=new DurableAuditDelivery(mapper,jdbc,tx,true);
        var record=row("batch8-cooperative");
        var executor=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            Callable<Void> send=()->{start.await();delivery.deliver(List.of(record));return null;};
            var a=executor.submit(send);var b=executor.submit(send);start.countDown();
            a.get(15,TimeUnit.SECONDS);b.get(15,TimeUnit.SECONDS);
            assertThat(mapper.countRequestIdentity(record.getRequestId())).isEqualTo(1);
            var conflict=row(record.getRequestId());conflict.setAccessTime(record.getAccessTime());conflict.setRequestBody("different");
            assertThatThrownBy(()->delivery.deliver(List.of(conflict))).isInstanceOf(IllegalStateException.class);
            assertThat(mapper.matchesDurableAudit(record)).isTrue();
        } finally {executor.shutdownNow();jdbc.update("DELETE FROM app.access_log WHERE request_id=?",record.getRequestId());}
    }

    @Test void batchReplayMatchesAllFieldsAndConflictingBatchRollsBack() {
        var delivery=new DurableAuditDelivery(mapper,jdbc,tx,true);
        var a=row("batch9-a");var b=row("batch9-b");var c=row("batch9-c");
        try{
            delivery.deliver(List.of(a,b,a));
            delivery.deliver(List.of(a,b));
            assertThat(mapper.countRequestIdentity(a.getRequestId())).isEqualTo(1);
            assertThat(mapper.countRequestIdentity(b.getRequestId())).isEqualTo(1);
            var conflict=row(a.getRequestId());conflict.setAccessTime(a.getAccessTime());conflict.setRequestBody("different");
            assertThatThrownBy(()->delivery.deliver(List.of(c,conflict))).isInstanceOf(IllegalStateException.class);
            assertThat(mapper.countRequestIdentity(c.getRequestId())).isZero();
            assertThat(mapper.matchesDurableAudit(a)).isTrue();
        }finally{jdbc.update("DELETE FROM app.access_log WHERE request_id IN (?,?,?)",a.getRequestId(),b.getRequestId(),c.getRequestId());}
    }

    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void deterministicConflictAndExplicitExpiryHoldCiphertextWithoutBlockingHealthySegment()throws Exception{
        Path root=Files.createDirectory(temp.toRealPath().resolve("held-spool"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        byte[] key=new byte[32];key[0]=23;
        var conflict=row("batch10-conflict");var healthy=row("batch10-healthy");var expired=row("batch10-expired");
        expired.setAccessTime(new Date(System.currentTimeMillis()-java.time.Duration.ofDays(9).toMillis()));
        try{
            new DurableAuditDelivery(mapper,jdbc,tx,true).deliver(List.of(conflict));
            var mismatch=row(conflict.getRequestId());mismatch.setAccessTime(conflict.getAccessTime());mismatch.setRequestBody("different");
            try(var spool=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper())){
                spool.append(mismatch);spool.append(expired);spool.append(healthy);
            }
            var env=new org.springframework.mock.env.MockEnvironment()
                .withProperty("MRR_AUDIT_SPOOL_KEY",Base64.getEncoder().encodeToString(key))
                .withProperty("mrr.audit.spool.directory",root.toString())
                .withProperty("mrr.audit.spool.grouped","true")
                .withProperty("mrr.audit.spool.max-replay-age-days","7");
            var metrics=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try{
                var service=new com.zjcxph.imgapi.audit.DurableAuditLogService(env,new ObjectMapper(),mapper,jdbc,tx,metrics);
                try{
                    service.flush();service.flush();service.flush();
                    assertThat(metrics.get("mrr.audit.spool.held").gauge().value()).isEqualTo(2);
                    assertThat(mapper.countRequestIdentity(healthy.getRequestId())).isEqualTo(1);
                    assertThat(mapper.matchesDurableAudit(conflict)).isTrue();
                    assertThat(mapper.countRequestIdentity(expired.getRequestId())).isZero();
                }finally{service.close();}
            }finally{metrics.close();}
            try(var spool=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper())){
                assertThat(spool.heldCount()).isEqualTo(2);
                assertThat(spool.peek(50)).isEmpty();
            }
            System.out.println("MRR_HELD_SEGMENTS conflict/expired preserved; healthy delivered; existing DB unchanged");
        }finally{jdbc.update("DELETE FROM app.access_log WHERE request_id IN (?,?,?)",conflict.getRequestId(),healthy.getRequestId(),expired.getRequestId());}
    }

    private DurableAuditDelivery delivery(String url) throws Exception {
        var ds=new DriverManagerDataSource(url,postgresqlUsername(),postgresqlPassword());
        var factory=new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var config=new org.apache.ibatis.session.Configuration();
        config.addMapper(LogMapper.class);
        factory.setConfiguration(config);
        var session=new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
        return new DurableAuditDelivery(session.getMapper(LogMapper.class),new JdbcTemplate(ds),new DataSourceTransactionManager(ds),true);
    }

    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void actualCommitAcknowledgementLostOnTcpWireThenEncryptedReplayConverges() throws Exception {
        var record=row("batch8-wire-commit");
        var second=row("batch9-wire-second");
        Path root=Files.createDirectory(temp.toRealPath().resolve("spool"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        byte[] key=new byte[32];
        URI target=URI.create(postgresqlJdbcUrl().substring(5));
        try {
            try(var spool=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper());
                var proxy=new DropCommitProxy(target.getHost(),target.getPort())) {
                spool.appendGroup(List.of(record,second));
                String proxyUrl="jdbc:postgresql://127.0.0.1:"+proxy.port()+target.getPath()+"?currentSchema=app&sslmode=disable&gssEncMode=disable&connectTimeout=3&socketTimeout=5";
                var proxied=delivery(proxyUrl);
                assertThatThrownBy(()->proxied.deliver(spool.peek(50).stream().map(EncryptedAuditSpool.Entry::log).toList())).isInstanceOf(RuntimeException.class);
                assertThat(proxy.dropped.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(mapper.countRequestIdentity(record.getRequestId())).isEqualTo(1);
                assertThat(mapper.countRequestIdentity(second.getRequestId())).isEqualTo(1);
                assertThat(spool.pendingCount()).isEqualTo(2);
            }
            try(var reopened=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper())) {
                var entries=reopened.peek(50);
                new DurableAuditDelivery(mapper,jdbc,tx,true).deliver(entries.stream().map(EncryptedAuditSpool.Entry::log).toList());
                reopened.acknowledgeSegments(entries.stream().map(EncryptedAuditSpool.Entry::name).toList());
                assertThat(mapper.countRequestIdentity(record.getRequestId())).isEqualTo(1);
                assertThat(mapper.countRequestIdentity(second.getRequestId())).isEqualTo(1);
                assertThat(reopened.pendingCount()).isZero();
            }
            System.out.println("MRR_DURABLE_WIRE real server COMMIT acknowledgement intercepted and connection closed; restart replay exactly once");
        } finally {jdbc.update("DELETE FROM app.access_log WHERE request_id IN (?,?)",record.getRequestId(),second.getRequestId());}
    }

    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void migrationCompletionLostCommitReplyReconcilesExactCurrentSource()throws Exception{
        int id=jdbc.queryForObject("""
            INSERT INTO app.mr_scan(bah,sjh,brxh,filename,folder,uploadflag,source_type,source_ref,
              migration_status,migration_attempts,migration_claim_id)
            VALUES ('8765451','12345678','8765451','synthetic.jpg','synthetic',1,'LOCAL','synthetic.jpg',
              'migrating',1,'batch11-claim') RETURNING id
            """,Integer.class);
        var scan=scans.findById(id);
        URI target=URI.create(postgresqlJdbcUrl().substring(5));
        try{
            try(var proxy=new DropCommitProxy(target.getHost(),target.getPort())){
                String url="jdbc:postgresql://127.0.0.1:"+proxy.port()+target.getPath()+"?currentSchema=app&sslmode=disable&gssEncMode=disable&connectTimeout=3&socketTimeout=5";
                var ds=new DriverManagerDataSource(url,postgresqlUsername(),postgresqlPassword());
                var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(ds);
                var config=new org.apache.ibatis.session.Configuration();config.addMapper(com.zjcxph.imgapi.mapper.ScanMapper.class);factory.setConfiguration(config);
                var proxied=new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(com.zjcxph.imgapi.mapper.ScanMapper.class);
                var transaction=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(ds));
                assertThatThrownBy(()->transaction.executeWithoutResult(status->{
                    assertThat(proxied.completeClaimedMigration(scan,1,"batch11-claim","synthetic-key",4L,"md5")).isEqualTo(1);
                })).isInstanceOf(RuntimeException.class);
                assertThat(proxy.dropped.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(scans.confirmCompletedMigration(scan,"synthetic-key",4L,"md5")).isEqualTo(1);
                assertThat(scans.confirmCompletedMigration(scan,"wrong-key",4L,"md5")).isZero();
                jdbc.update("UPDATE app.mr_scan SET source_ref='changed.jpg' WHERE id=?",id);
                assertThat(scans.confirmCompletedMigration(scan,"synthetic-key",4L,"md5")).isZero();
            }
            System.out.println("MRR_MIGRATION_COMMIT_ACK actual DB commit response lost; exact state confirmed; changed source rejected");
        }finally{jdbc.update("DELETE FROM app.mr_scan WHERE id=?",id);}
    }

    /** Plain PG protocol only on isolated loopback: forwards all frames until real COMMIT CommandComplete. */
    static final class DropCommitProxy implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService threads=Executors.newVirtualThreadPerTaskExecutor();
        private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
        final CountDownLatch dropped=new CountDownLatch(1);
        DropCommitProxy(String host,int port) throws IOException {
            listener=new ServerSocket(0,8,InetAddress.getLoopbackAddress());
            threads.submit(()->{
                try {
                    while(!listener.isClosed()) {
                        Socket client=listener.accept(); sockets.add(client);
                        Socket server=new Socket(host,port); sockets.add(server);
                        threads.submit(()->{
                            try {client.getInputStream().transferTo(server.getOutputStream());}
                            catch(IOException ignored) {} finally {closeSocket(client);closeSocket(server);}
                        });
                        threads.submit(()->{
                            try {
                                var in=new DataInputStream(server.getInputStream());
                                var out=new DataOutputStream(client.getOutputStream());
                                while(true) {
                                    int type=in.readUnsignedByte(); int length=in.readInt();
                                    if(length<4 || length>16*1024*1024) throw new IOException("invalid PG frame");
                                    byte[] payload=in.readNBytes(length-4);
                                    if(payload.length!=length-4) throw new EOFException();
                                    if(type=='C' && new String(payload,StandardCharsets.US_ASCII).equals("COMMIT\0")) {
                                        dropped.countDown();return;
                                    }
                                    out.writeByte(type);out.writeInt(length);out.write(payload);out.flush();
                                }
                            } catch(IOException ignored) {} finally {closeSocket(client);closeSocket(server);}
                        });
                    }
                } catch(IOException ignored) {}
            });
        }
        int port(){return listener.getLocalPort();}
        private static void closeSocket(Socket s){try{s.close();}catch(IOException ignored){}}
        public void close() throws IOException {listener.close();sockets.forEach(DropCommitProxy::closeSocket);threads.shutdownNow();}
    }
}
