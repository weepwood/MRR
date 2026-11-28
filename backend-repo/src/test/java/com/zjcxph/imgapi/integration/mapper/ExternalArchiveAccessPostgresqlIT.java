package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ExternalArchiveAccessMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "mybatis.configuration.map-underscore-to-camel-case=true")
class ExternalArchiveAccessPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired ExternalArchiveAccessMapper mapper;

    @Autowired com.zjcxph.imgapi.mapper.ImageContentMapper contentMapper;
    @Autowired com.zjcxph.imgapi.mapper.ScanMapper scans;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void actualReassignedScanCannotOpenAnotherArchivesLocalFile() throws Exception {
        var root=java.nio.file.Files.createTempDirectory("mrr-external-scope-");
        java.nio.file.Files.write(root.resolve("a.jpg"),new byte[]{1,2});
        java.nio.file.Files.write(root.resolve("b.jpg"),new byte[]{8,9});
        var settings=new com.zjcxph.imgapi.config.ImageProperties();
        settings.setBasePath(root.toString());
        var service=new com.zjcxph.imgapi.service.impl.ImageContentServiceImpl(contentMapper,
                new com.zjcxph.imgapi.storage.LocalImageStorage(settings));
        var scope=new com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO();
        scope.setBah("8765431"); scope.setSjh("");
        var grant=new com.zjcxph.imgapi.security.ExternalArchiveGrant("synthetic","synthetic",false,
                System.currentTimeMillis()+60000,java.util.List.of(scope));
        Integer id=null;
        try {
            id=jdbc.queryForObject("""
                    INSERT INTO app.mr_scan(bah,brxh,filename,folder,uploadflag,source_type,source_ref)
                    VALUES ('8765431','8765431','a.jpg','synthetic',1,'LOCAL','a.jpg') RETURNING id
                    """,Integer.class);
            final int scanId=id;
            var stale=scans.findById(id);
            assertThat(grant.allows(stale.getBah(),stale.getSjh())).isTrue();
            try(var opened=service.openExternal(id,grant).content()) {
                assertThat(opened.inputStream().readAllBytes()).containsExactly((byte)1,(byte)2);
            }
            var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
            tx.executeWithoutResult(status->jdbc.update(
                    "UPDATE app.mr_scan SET bah='8765432', filename='b.jpg',source_ref='b.jpg' WHERE id=?",scanId));
            assertThat(grant.allows(stale.getBah(),stale.getSjh())).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.openExternal(scanId,grant))
                    .isInstanceOfSatisfying(com.zjcxph.imgapi.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo(403));
            jdbc.update("UPDATE app.mr_scan SET uploadflag=0 WHERE id=?",id);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.openExternal(scanId,grant))
                    .isInstanceOfSatisfying(com.zjcxph.imgapi.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo(404));
            System.out.println("MRR_EXTERNAL_SCOPE allowed local bytes; committed reassignment denied; inactive denied");
        } finally {
            if(id!=null) jdbc.update("DELETE FROM app.mr_scan WHERE id=?",id);
            java.nio.file.Files.deleteIfExists(root.resolve("a.jpg"));
            java.nio.file.Files.deleteIfExists(root.resolve("b.jpg"));
            java.nio.file.Files.deleteIfExists(root);
        }
    }

    @Test
    void ticketCannotBeConsumedTwiceEvenInSameMapperSession() {
        mapper.insertTicket("synthetic-ticket", "synthetic", "synthetic-user", false,
                "[]", LocalDateTime.now().plusMinutes(2), "127.0.0.1");
        assertThat(mapper.consumeTicket("synthetic-ticket")).isNotNull();
        assertThat(mapper.consumeTicket("synthetic-ticket")).isNull();
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void independentConsumersCompeteForOneTicketAndRollbackRestoresUnusedTicket() throws Exception {
        String ticket = "synthetic-concurrent-ticket";
        String rollbackTicket = "synthetic-rollback-ticket";
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            mapper.insertTicket(ticket, "synthetic", "synthetic-user", false, "[]", LocalDateTime.now().plusMinutes(2), "127.0.0.1");
            mapper.insertTicket(rollbackTicket, "synthetic", "synthetic-user", false, "[]", LocalDateTime.now().plusMinutes(2), "127.0.0.1");
            java.util.concurrent.Callable<Boolean> consume = () -> {
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return Boolean.TRUE.equals(tx.execute(status -> mapper.consumeTicket(ticket) != null));
            };
            var one = executor.submit(consume);
            var two = executor.submit(consume);
            start.countDown();
            assertThat(java.util.List.of(one.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    two.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            tx.executeWithoutResult(status -> {
                assertThat(mapper.consumeTicket(rollbackTicket)).isNotNull();
                status.setRollbackOnly();
            });
            assertThat(mapper.consumeTicket(rollbackTicket)).isNotNull();
            assertThat(mapper.consumeTicket(rollbackTicket)).isNull();
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM app.mr_external_archive_ticket WHERE ticket_hash IN (?,?)", ticket, rollbackTicket);
        }
    }

    @Test
    void nonceUniquenessAndSessionRevocationAreEnforcedByDatabase() {
        var expiry = LocalDateTime.now().plusMinutes(2);
        assertThat(mapper.insertNonce("synthetic", "synthetic-nonce", expiry)).isEqualTo(1);
        assertThat(mapper.insertNonce("synthetic", "synthetic-nonce", expiry)).isZero();
        mapper.insertSession("synthetic-session", "synthetic", "synthetic-user", false, "[]", expiry, "127.0.0.1");
        assertThat(mapper.findSession("synthetic-session")).isNotNull();
        assertThat(mapper.touchSession("synthetic-session")).isEqualTo(1);
        assertThat(mapper.revokeSession("synthetic-session")).isEqualTo(1);
        assertThat(mapper.touchSession("synthetic-session")).isZero();
        assertThat(mapper.findSession("synthetic-session")).isNull();
        mapper.insertSession("synthetic-expired", "synthetic", "synthetic-user", false, "[]",
                LocalDateTime.now().minusMinutes(1), "127.0.0.1");
        assertThat(mapper.touchSession("synthetic-expired")).isZero();
        assertThat(mapper.findSession("synthetic-expired")).isNull();
    }
}
