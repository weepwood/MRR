package com.zjcxph.imgapi.unit.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateLogbackConfigurationTest {
    @TempDir Path directory;
    @Test void realConfigurationCapturesRootApplicationAndNonAdditiveMapperAtEveryEmittedLevel() throws Exception {
        Path file = directory.resolve("levels.log");
        var environment = new MockEnvironment().withProperty("logging.file.name", file.toString());
        environment.setActiveProfiles("test");
        var logging = new LogbackLoggingSystem(getClass().getClassLoader());
        try {
            logging.beforeInitialize();
            logging.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
            LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
            for (String name : List.of("synthetic.thirdparty", "com.zjcxph.imgapi.synthetic", "com.zjcxph.imgapi.mapper.synthetic")) {
                var logger = context.getLogger(name); logger.setLevel(Level.TRACE);
                logger.trace("SYNTHETIC-PRIVATE"); logger.debug("SYNTHETIC-PRIVATE"); logger.info("SYNTHETIC-PRIVATE");
                logger.warn("SYNTHETIC-PRIVATE"); logger.error("SYNTHETIC-PRIVATE", new IllegalStateException("SYNTHETIC-PRIVATE"));
            }
            // stop drains AsyncAppender; no timing-based sleep assertion.
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_FILE").stop();
            String text = Files.readString(file);
            assertThat(text).doesNotContain("SYNTHETIC-PRIVATE");
            var json = new ObjectMapper();
            Map<String,Set<String>> levels = new HashMap<>();
            for (String line : text.lines().toList()) {
                var row = json.readTree(line);
                levels.computeIfAbsent(row.path("logger_name").asText(), ignored -> new HashSet<>()).add(row.path("level").asText());
            }
            for (String name : List.of("synthetic.thirdparty", "com.zjcxph.imgapi.synthetic", "com.zjcxph.imgapi.mapper.synthetic"))
                assertThat(levels.get(name)).containsExactlyInAnyOrder("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        } finally { logging.cleanUp(); }
    }

    @Test void realFilePreservesSecurityIdentityAndWarnProjectionContract() throws Exception {
        Path file=directory.resolve("security.log");
        var env=new MockEnvironment().withProperty("logging.file.name",file.toString());env.setActiveProfiles("test");
        var logging=new LogbackLoggingSystem(getClass().getClassLoader());
        var mapper=org.mockito.Mockito.mock(com.zjcxph.imgapi.mapper.SystemErrorEventMapper.class);
        var service=new com.zjcxph.imgapi.service.SystemErrorEventService(mapper);
        var capture=new com.zjcxph.imgapi.config.RuntimeErrorLogCapture(service);
        try {
            logging.beforeInitialize();logging.initialize(new LoggingInitializationContext(env),"classpath:logback-spring.xml",null);
            var context=(LoggerContext)LoggerFactory.getILoggerFactory();
            capture.start();
            var logger=context.getLogger("SECURITY_AUDIT");
            logger.warn("event=USER_DISABLED actorUserId={} targetUserId={} previousStatus={} result=SUCCESS",42L,77L,"SYNTHETIC_PRIVATE");
            logger.warn("event=USER_PASSWORD_RESET actorUserId={} targetUserId={} sourceIp={} result=DENIED reason=ADMIN_PASSWORD_MISMATCH",42L,77L,"SYNTHETIC_PRIVATE");
            context.getLogger("com.zjcxph.imgapi.synthetic").error("Synthetic operation failed for {}","SYNTHETIC_PRIVATE");
            capture.stop(); service.flushPending();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getAppender("ASYNC_FILE").stop();
            String text=Files.readString(file);assertThat(text).doesNotContain("SYNTHETIC_PRIVATE");
            var json=new ObjectMapper();var audits=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for(String line:text.lines().toList()) {var row=json.readTree(line);if(row.has("security_audit")) audits.add(row);}
            assertThat(audits).hasSize(2);
            var captor=org.mockito.ArgumentCaptor.forClass(com.zjcxph.imgapi.entity.SystemErrorEvent.class);
            org.mockito.Mockito.verify(mapper,org.mockito.Mockito.times(3)).upsert(captor.capture());
            for(var row:audits) {
                assertThat(row.path("security_audit").path("actor_user_id").asLong()).isEqualTo(42L);
                assertThat(row.path("security_audit").path("target_user_id").asLong()).isEqualTo(77L);
                assertThat(captor.getAllValues()).anySatisfy(event->{
                    assertThat(event.getProjectionEventId()).isEqualTo(row.path("event_id").asText());
                    assertThat(event.getErrorId()).isEqualTo(row.path("errorId").asText());
                });
            }
            assertThat(audits.stream().map(row->row.path("security_audit").path("result").asText()).toList()).containsExactlyInAnyOrder("SUCCESS","DENIED");
        } finally {capture.stop();service.destroy();logging.cleanUp();}
    }

    @Test void saturatedNonBlockingFileQueueDropsEventsWithoutBlockingProducer() throws Exception {
        var context=new LoggerContext();
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var delivered=new java.util.concurrent.atomic.AtomicInteger();
        var sink=new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                entered.countDown();
                try {release.await();} catch(InterruptedException e){Thread.currentThread().interrupt();}
                delivered.incrementAndGet();
            }
        };
        sink.setContext(context);sink.start();
        var queue=new ch.qos.logback.classic.AsyncAppender();queue.setContext(context);queue.setName("saturation-probe");
        queue.setQueueSize(512);queue.setDiscardingThreshold(0);queue.setNeverBlock(true);queue.addAppender(sink);queue.start();
        var producer=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var logger=context.getLogger("SECURITY_AUDIT");logger.setAdditive(false);logger.addAppender(queue);
            logger.warn("Synthetic warning {}",0);
            assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            producer.submit(()->{for(int i=1;i<=544;i++)logger.warn("Synthetic warning {}",i);}).get(3,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(queue.getNumberOfElementsInQueue()).isEqualTo(512);
            release.countDown();queue.stop();
            assertThat(delivered.get()).isEqualTo(513);
            System.out.println("MRR_ASYNC_QUEUE_PROBE produced=545 delivered="+delivered.get()+" dropped=32");
        } finally {release.countDown();producer.shutdownNow();queue.stop();sink.stop();context.stop();}
    }

}
