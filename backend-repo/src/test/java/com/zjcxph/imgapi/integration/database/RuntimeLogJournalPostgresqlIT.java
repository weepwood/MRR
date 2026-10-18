package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.RuntimeLogJournalService;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuntimeLogJournalPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemErrorEventMapper realErrors;
    @TempDir Path directory;

    @Test void disabledEnabledDisabledEnabledProjectsEachPhysicalEventOnlyOnce() throws Exception {
        Path file = directory.resolve("mode-switch.log");
        var encoder = new com.zjcxph.imgapi.config.PrivateJsonLogEncoder();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("synthetic.modeSwitch");
        var tx = new DataSourceTransactionManager(jdbc.getDataSource());
        var fallback = new com.zjcxph.imgapi.service.SystemErrorEventService(realErrors);
        try {
            for (int i = 0; i < 2; i++) {
                var event = new ch.qos.logback.classic.spi.LoggingEvent(getClass().getName(), logger,
                        ch.qos.logback.classic.Level.WARN, "Query failed for {}", new IllegalStateException("synthetic"), new Object[]{"synthetic"});
                event.prepareForDeferredProcessing();
                fallback.capture(event); fallback.flushPending();
                byte[] encoded = encoder.encode(event);
                Files.write(file, encoded, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                var journal = new RuntimeLogJournalService(jdbc, tx, realErrors, file.toString());
                journal.ingest(); journal.ingest();
                assertThat(jdbc.queryForObject("SELECT SUM(occurrence_count) FROM app.system_error_event WHERE logger_name='synthetic.modeSwitch'", Long.class)).isEqualTo(i + 1L);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.system_error_event WHERE logger_name='synthetic.modeSwitch'", Long.class)).isEqualTo(1);
                assertThat(encoder.encode(event)).isEqualTo(encoded);
                // 反向重试同一物理事件也必须被收据拦截。
                fallback.capture(event); fallback.flushPending();
                assertThat(jdbc.queryForObject("SELECT SUM(occurrence_count) FROM app.system_error_event WHERE logger_name='synthetic.modeSwitch'", Long.class)).isEqualTo(i + 1L);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE logger_name='synthetic.modeSwitch'", Long.class)).isEqualTo(2);
        } finally {
            fallback.destroy();
            jdbc.update("DELETE FROM app.system_error_event WHERE logger_name='synthetic.modeSwitch'");
            jdbc.update("DELETE FROM app.runtime_log_event WHERE logger_name='synthetic.modeSwitch'");
            jdbc.update("DELETE FROM app.runtime_log_source WHERE file_name='mode-switch.log'");
        }
    }

    @Test void legacyLogsKeepDetailsWithoutRecountingUnknownHistoricalProjections() throws Exception {
        Path file = directory.resolve("legacy.log");
        Files.writeString(file, "{\"@timestamp\":\"2026-10-17T00:00:00Z\",\"level\":\"WARN\",\"logger_name\":\"synthetic.legacy\"}\n");
        var errors=mock(SystemErrorEventMapper.class);
        new RuntimeLogJournalService(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),errors,file.toString()).ingest();
        verifyNoInteractions(errors);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE logger_name='synthetic.legacy' AND projected",Long.class)).isEqualTo(1);
        jdbc.update("DELETE FROM app.runtime_log_event WHERE logger_name='synthetic.legacy'");
        jdbc.update("DELETE FROM app.runtime_log_source WHERE file_name='legacy.log'");
    }

    @Test void restartAutomaticallyRecoversPrivateRotationCreatedWhileDatabaseWasUnavailable() throws Exception {
        Path live = directory.resolve("recovery.log");
        Path rotated = directory.resolve("recovery.log.2026-10-17.0.gz");
        UUID id = UUID.randomUUID();
        try (var gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(rotated))) {
            gzip.write(("{\"event_id\":\""+id+"\",\"@timestamp\":\"2026-10-17T00:00:00Z\",\"level\":\"INFO\",\"privacy_version\":1}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Files.setLastModifiedTime(rotated, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(86400)));
        var service = new RuntimeLogJournalService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), mock(SystemErrorEventMapper.class), live.toString());
        service.ingest();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE event_id=?", Long.class, id)).isEqualTo(1);
        jdbc.update("DELETE FROM app.runtime_log_event WHERE event_id=?", id);
        jdbc.update("DELETE FROM app.runtime_log_source WHERE file_name=?", rotated.getFileName().toString());
    }

    @Test void disappearanceOfUnacknowledgedSourcePersistsPossibleGap() {
        String key = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app.runtime_log_source(source_key,file_name) VALUES (?,?)", key, "missing.log.2026-10-17.0.gz");
        var service = new RuntimeLogJournalService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), mock(SystemErrorEventMapper.class), directory.resolve("missing.log").toString());
        service.ingest();
        assertThat(jdbc.queryForObject("SELECT failure_code FROM app.runtime_log_source WHERE source_key=?", String.class, key)).isEqualTo("SOURCE_DISAPPEARED");
        assertThat(service.health().get("unknownGap")).isEqualTo(true);
        jdbc.update("DELETE FROM app.runtime_log_source WHERE source_key=?", key);
    }

    @Test void queryCursorUsesEventIdToDisambiguateIdenticalTimestamps() throws Exception {
        Path file = directory.resolve("cursor.log");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        StringBuilder content = new StringBuilder();
        for (UUID id : java.util.List.of(first, second)) content.append("{\"event_id\":\"").append(id).append("\",\"@timestamp\":\"2026-10-17T00:00:00Z\",\"level\":\"INFO\",\"logger_name\":\"synthetic.cursor\",\"privacy_version\":1}\n");
        Files.writeString(file, content.toString());
        new RuntimeLogJournalService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), mock(SystemErrorEventMapper.class), file.toString()).ingest();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE event_id IN (?,?) AND error_id IS NULL",Long.class,first,second)).isEqualTo(2);
        var queries = new com.zjcxph.imgapi.service.RuntimeLogQueryService(jdbc);
        var from = java.time.Instant.parse("2026-10-16T00:00:00Z"); var to = from.plusSeconds(172800);
        var page = queries.search(from,to,null,null,null,"synthetic.cursor",null,null,1);
        var rows = (java.util.List<java.util.Map<String,Object>>) page.get("list");
        assertThat(page.get("hasMore")).isEqualTo(true);
        var next = queries.search(from,to,null,null,null,"synthetic.cursor",java.time.Instant.parse((String)rows.getFirst().get("occurred_at")),UUID.fromString((String)rows.getFirst().get("event_id")),1);
        var nextRows = (java.util.List<java.util.Map<String,Object>>) next.get("list");
        assertThat(nextRows).hasSize(1); assertThat(nextRows.getFirst().get("event_id")).isNotEqualTo(rows.getFirst().get("event_id"));
        assertThat(next.get("hasMore")).isEqualTo(false);
        assertThatThrownBy(() -> queries.search(from,to,null,null,null,null,null,first,1)).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        jdbc.update("DELETE FROM app.runtime_log_event WHERE event_id IN (?,?)", first, second);
        jdbc.update("DELETE FROM app.runtime_log_source WHERE file_name='cursor.log'");
    }

    @Test void failedProjectionRollsBackBothEventsAndCursorAndRestartRetriesExactlyOnce() throws Exception {
        Path file = directory.resolve("journal.log");
        UUID id = UUID.randomUUID();
        Files.writeString(file, "{\"event_id\":\""+id+"\",\"@timestamp\":\"2026-10-18T00:00:00Z\",\"level\":\"WARN\",\"logger_name\":\"synthetic\",\"privacy_version\":1,\"message\":\"failure\"}\n");
        SystemErrorEventMapper errors = mock(SystemErrorEventMapper.class);
        when(errors.upsert(any())).thenThrow(new IllegalStateException("synthetic failure"));
        var tx = new DataSourceTransactionManager(jdbc.getDataSource());
        new RuntimeLogJournalService(jdbc, tx, errors, file.toString()).ingest();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE event_id=?", Long.class, id)).isZero();
        reset(errors);
        var restarted = new RuntimeLogJournalService(jdbc, tx, errors, file.toString());
        restarted.ingest(); restarted.ingest();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_log_event WHERE event_id=?", Long.class, id)).isEqualTo(1);
        verify(errors, times(1)).upsert(any());
        jdbc.update("DELETE FROM app.runtime_log_event WHERE event_id=?", id);
        jdbc.update("DELETE FROM app.runtime_log_source WHERE file_name='journal.log'");
    }
}
