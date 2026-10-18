package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.entity.SystemErrorEvent;
import com.zjcxph.imgapi.mapper.SystemErrorEventMapper;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.utils.RuntimeErrorSanitizer;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker=true)
class RuntimeErrorHistoryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemErrorEventMapper mapper;
    @Test void olderImportedEventsCannotMoveLastSeenBackwardsOrReopenResolvedErrors() {
        String fingerprint=UUID.randomUUID().toString();
        LocalDateTime current=LocalDateTime.now().minusHours(1).withNano(0);
        mapper.upsert(event(fingerprint,current,"current-request"));
        long id=jdbc.queryForObject("SELECT id FROM app.system_error_event WHERE fingerprint=?",Long.class,fingerprint);
        mapper.updateStatus(id,"RESOLVED","synthetic-operator");
        mapper.upsert(event(fingerprint,current.minusHours(1),"historical-request"));
        SystemErrorEvent result=mapper.findById(id);
        assertThat(result.getFirstSeenAt()).isEqualTo(current.minusHours(1));
        assertThat(result.getLastSeenAt()).isEqualTo(current);
        assertThat(result.getStatus()).isEqualTo("RESOLVED");
        assertThat(result.getRequestId()).isEqualTo("current-request");
        assertThat(result.getOccurrenceCount()).isEqualTo(2);
        mapper.upsert(event(fingerprint,LocalDateTime.now().plusSeconds(2).withNano(0),"new-request"));
        assertThat(mapper.findById(id).getStatus()).isEqualTo("OPEN");
        assertThat(mapper.findById(id).getRequestId()).isEqualTo("new-request");
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void aggregateSqlFailureRollsBackReceiptAndRetryCommitsExactlyOnce() {
        var event=event(UUID.randomUUID().toString(),LocalDateTime.now(),"synthetic-retry");
        event.setLevel("INVALID-LEVEL-TOO-LONG");
        try {
            assertThatThrownBy(() -> mapper.upsert(event)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app.runtime_error_projection_receipt WHERE event_id=CAST(? AS UUID)",Long.class,event.getProjectionEventId())).isZero();
            event.setLevel("ERROR");
            assertThat(mapper.upsert(event)).isEqualTo(1);
            assertThat(mapper.upsert(event)).isZero();
            assertThat(jdbc.queryForObject("SELECT occurrence_count FROM app.system_error_event WHERE fingerprint=?",Long.class,event.getFingerprint())).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM app.system_error_event WHERE fingerprint=?",event.getFingerprint());
            jdbc.update("DELETE FROM app.runtime_error_projection_receipt WHERE event_id=CAST(? AS UUID)",event.getProjectionEventId());
        }
    }

    private SystemErrorEvent event(String fingerprint,LocalDateTime at,String requestId) {
        var event=new SystemErrorEvent();event.setProjectionEventId(UUID.randomUUID().toString());event.setFingerprint(fingerprint);event.setErrorId(RuntimeErrorSanitizer.newErrorId());
        event.setFirstSeenAt(at);event.setLastSeenAt(at);event.setLevel("ERROR");event.setLoggerName("synthetic");
        event.setModule("synthetic");event.setMessageSummary("[MESSAGE OMITTED]");event.setStackTrace("");event.setRequestId(requestId);
        return event;
    }
}
