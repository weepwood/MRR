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
        var delivery=new DurableAuditDelivery(mapper,jdbc,tx);
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

    private DurableAuditDelivery delivery(String url) throws Exception {
        var ds=new DriverManagerDataSource(url,postgresqlUsername(),postgresqlPassword());
        var factory=new org.mybatis.spring.SqlSessionFactoryBean();
        factory.setDataSource(ds);
        var config=new org.apache.ibatis.session.Configuration();
        config.addMapper(LogMapper.class);
        factory.setConfiguration(config);
        var session=new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
        return new DurableAuditDelivery(session.getMapper(LogMapper.class),new JdbcTemplate(ds),new DataSourceTransactionManager(ds));
    }

    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void actualCommitAcknowledgementLostOnTcpWireThenEncryptedReplayConverges() throws Exception {
        var record=row("batch8-wire-commit");
        Path root=Files.createDirectory(temp.toRealPath().resolve("spool"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        byte[] key=new byte[32];
        URI target=URI.create(postgresqlJdbcUrl().substring(5));
        try {
            try(var spool=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper());
                var proxy=new DropCommitProxy(target.getHost(),target.getPort())) {
                spool.append(record);
                String proxyUrl="jdbc:postgresql://127.0.0.1:"+proxy.port()+target.getPath()+"?currentSchema=app&sslmode=disable&gssEncMode=disable&connectTimeout=3&socketTimeout=5";
                var proxied=delivery(proxyUrl);
                assertThatThrownBy(()->proxied.deliver(List.of(spool.peek(1).getFirst().log()))).isInstanceOf(RuntimeException.class);
                assertThat(proxy.dropped.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(mapper.countRequestIdentity(record.getRequestId())).isEqualTo(1);
                assertThat(spool.pendingCount()).isEqualTo(1);
            }
            try(var reopened=new EncryptedAuditSpool(root,key,10,65536,new ObjectMapper())) {
                var entries=reopened.peek(50);
                new DurableAuditDelivery(mapper,jdbc,tx).deliver(entries.stream().map(EncryptedAuditSpool.Entry::log).toList());
                reopened.acknowledge(entries.getFirst().name());
                assertThat(mapper.countRequestIdentity(record.getRequestId())).isEqualTo(1);
                assertThat(reopened.pendingCount()).isZero();
            }
            System.out.println("MRR_DURABLE_WIRE real server COMMIT acknowledgement intercepted and connection closed; restart replay exactly once");
        } finally {jdbc.update("DELETE FROM app.access_log WHERE request_id=?",record.getRequestId());}
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
