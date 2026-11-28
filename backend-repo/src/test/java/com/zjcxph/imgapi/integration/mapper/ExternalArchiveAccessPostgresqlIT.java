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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"context","list","image","download","exchange","logout"})
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void unavailableDedicatedAuditDeniesDisclosureButNotRevocation(String action) throws Exception {
        String token=java.util.UUID.randomUUID().toString();
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String client="synthetic-audit-failure";
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        String cases=json.writeValueAsString(java.util.List.of(new com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO("123456","1234",null,null,null)));
        var search=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.SearchService.class);
        var scanService=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ScanService.class);
        var images=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ImageContentService.class);
        var exports=org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ArchiveExportService.class);
        var access=new com.zjcxph.imgapi.service.ExternalArchiveAccessService(new com.zjcxph.imgapi.config.IntegrationProperties(),search,scanService,mapper,json);
        var controller=new com.zjcxph.imgapi.controller.ExternalArchiveController(json,access,exports,images);
        var req=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/external/archive/"+action);
        req.setAttribute("requestId","synthetic-"+action);
        req.setCookies(new jakarta.servlet.http.Cookie(com.zjcxph.imgapi.service.ExternalArchiveAccessService.SESSION_COOKIE_NAME,token));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(req));
        org.mockito.Mockito.when(scanService.getFreshImageListByCode(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(images.openExternal(org.mockito.ArgumentMatchers.eq(42),org.mockito.ArgumentMatchers.any())).thenAnswer(call->new com.zjcxph.imgapi.service.ImageContentService.ScopedImage(
                new com.zjcxph.imgapi.service.ImageContentService.ImageContent(new java.io.ByteArrayInputStream(new byte[]{1,2}),"synthetic.jpg",org.springframework.http.MediaType.IMAGE_JPEG,2L),"123456","1234"));
        org.mockito.Mockito.when(exports.prepareExternalArchive("123456","1234")).thenReturn(new com.zjcxph.imgapi.service.ArchiveExportService.BatchZipExport(java.util.List.of(new com.zjcxph.imgapi.entity.PathDO())));
        try {
            if("exchange".equals(action)) mapper.insertTicket(hash,client,"synthetic-user",true,cases,LocalDateTime.now().plusMinutes(2),"127.0.0.1");
            else mapper.insertSession(hash,client,"synthetic-user",true,cases,LocalDateTime.now().plusMinutes(2),"127.0.0.1");
            jdbc.execute("ALTER TABLE app.mr_external_archive_access_log ADD CONSTRAINT ck_test_external_audit_unavailable CHECK(client_id<>'synthetic-audit-failure') NOT VALID");
            if("logout".equals(action)) {
                controller.logout(req,new org.springframework.mock.web.MockHttpServletResponse());
                assertThat(mapper.findSession(hash)).isNull();
            } else {
                org.assertj.core.api.Assertions.assertThatThrownBy(()->{
                    switch(action) {
                        case "context" -> controller.context(req);
                        case "list" -> controller.images("123456","1234",req);
                        case "image" -> controller.image(42,req);
                        case "download" -> controller.download("123456","1234",req);
                        case "exchange" -> new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx->access.consumeTicket(token,"127.0.0.1"));
                        default -> throw new AssertionError(action);
                    }
                }).isInstanceOfSatisfying(com.zjcxph.imgapi.exception.BusinessException.class,e->assertThat(e.getCode()).isEqualTo(503));
                org.mockito.Mockito.verifyNoInteractions(scanService,images,exports);
                if("exchange".equals(action)) {
                    assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM app.mr_external_archive_ticket WHERE ticket_hash=?",Boolean.class,hash)).isTrue();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM app.mr_external_archive_session WHERE client_id=?",Long.class,client)).isZero();
                }
            }
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            jdbc.execute("ALTER TABLE app.mr_external_archive_access_log DROP CONSTRAINT IF EXISTS ck_test_external_audit_unavailable");
            jdbc.update("DELETE FROM app.mr_external_archive_access_log WHERE client_id=?",client);
            jdbc.update("DELETE FROM app.mr_external_archive_session WHERE client_id=?",client);
            jdbc.update("DELETE FROM app.mr_external_archive_ticket WHERE client_id=?",client);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"success","broken-stream","terminal-unavailable"})
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void externalLocalFileStreamRetainsDurableAdmissionAndOutcome(String outcome,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        String token=java.util.UUID.randomUUID().toString();
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String client="synthetic-stream-"+outcome;
        String trace="synthetic-stream-"+java.util.UUID.randomUUID();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var cases=json.writeValueAsString(java.util.List.of(new com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO("8765433","",null,null,null)));
        var settings=new com.zjcxph.imgapi.config.ImageProperties();settings.setBasePath(root.toString());
        var storage=new com.zjcxph.imgapi.storage.LocalImageStorage(settings);
        var images=new com.zjcxph.imgapi.service.impl.ImageContentServiceImpl(contentMapper,storage);
        var access=new com.zjcxph.imgapi.service.ExternalArchiveAccessService(new com.zjcxph.imgapi.config.IntegrationProperties(),
                org.mockito.Mockito.mock(com.zjcxph.imgapi.service.SearchService.class),
                org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ScanService.class),mapper,json);
        var controller=new com.zjcxph.imgapi.controller.ExternalArchiveController(json,access,
                org.mockito.Mockito.mock(com.zjcxph.imgapi.service.ArchiveExportService.class),images);
        java.nio.file.Files.write(root.resolve("a.jpg"),new byte[]{1,2,3});
        Integer scan=null;
        try {
            mapper.insertSession(hash,client,"synthetic-user",false,cases,LocalDateTime.now().plusMinutes(2),"127.0.0.1");
            scan=jdbc.queryForObject("INSERT INTO app.mr_scan(bah,filename,folder,uploadflag,source_type,source_ref) VALUES ('8765433','a.jpg','synthetic',1,'LOCAL','a.jpg') RETURNING id",Integer.class);
            var req=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/external/archive/image/"+scan);
            req.setAttribute("requestId",trace);
            req.setCookies(new jakarta.servlet.http.Cookie(com.zjcxph.imgapi.service.ExternalArchiveAccessService.SESSION_COOKIE_NAME,token));
            req.setAsyncSupported(true);
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(req));
            var response=controller.image(scan,req);
            assertThat(jdbc.queryForList("SELECT result FROM app.mr_external_archive_access_log WHERE request_id=? ORDER BY id",String.class,trace)).containsExactly("INTENT");
            if("terminal-unavailable".equals(outcome))
                jdbc.execute("ALTER TABLE app.mr_external_archive_access_log ADD CONSTRAINT ck_test_external_terminal_unavailable CHECK(client_id<>'synthetic-stream-terminal-unavailable' OR result='INTENT') NOT VALID");
            if("broken-stream".equals(outcome)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(()->response.getBody().writeTo(new java.io.OutputStream(){
                    @Override public void write(int b)throws java.io.IOException{throw new java.io.IOException("synthetic client disconnect");}
                })).isInstanceOf(java.io.IOException.class);
            } else {
                var bytes=new java.io.ByteArrayOutputStream();response.getBody().writeTo(bytes);
                assertThat(bytes.toByteArray()).containsExactly((byte)1,(byte)2,(byte)3);
            }
            var results=jdbc.queryForList("SELECT result FROM app.mr_external_archive_access_log WHERE request_id=? ORDER BY id",String.class,trace);
            if("terminal-unavailable".equals(outcome)) assertThat(results).containsExactly("INTENT");
            else assertThat(results).containsExactly("INTENT","success".equals(outcome)?"SUCCESS":"FAILED");
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            jdbc.execute("ALTER TABLE app.mr_external_archive_access_log DROP CONSTRAINT IF EXISTS ck_test_external_terminal_unavailable");
            jdbc.update("DELETE FROM app.mr_external_archive_access_log WHERE client_id=?",client);
            jdbc.update("DELETE FROM app.mr_external_archive_session WHERE client_id=?",client);
            if(scan!=null) jdbc.update("DELETE FROM app.mr_scan WHERE id=?",scan);
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
