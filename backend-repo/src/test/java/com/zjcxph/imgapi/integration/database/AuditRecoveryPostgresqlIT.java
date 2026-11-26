package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.impl.AsyncLogServiceImpl;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Date;
import java.util.List;
import java.sql.Connection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyList;

@MybatisTest(properties="mybatis.configuration.map-underscore-to-camel-case=true")
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AuditRecoveryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired LogMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.apache.ibatis.session.SqlSessionFactory factory;

    private Log row(String id) {
        var log=new Log();
        log.setRequestId(id);log.setUsername("synthetic-audit");
        log.setMethod("GET");log.setRequestUri("/synthetic");log.setAccessTime(new Date());
        log.setResponseStatus("200");log.setAuditAction("VIEW_IMAGE");log.setAuditTarget("synthetic");
        return log;
    }

    @Test
    void realCommitThenLostAcknowledgementIsReconciledBeforeRetry() {
        var row=row("batch7-ack");
        var fault=mock(LogMapper.class,delegatesTo(mapper));
        doAnswer(call->{
            List<Log> rows=call.getArgument(0);
            mapper.batchInsert(rows); // autocommit, real PG write
            throw new org.springframework.dao.DataAccessResourceFailureException("synthetic lost acknowledgement");
        }).doAnswer(call->mapper.batchInsert(call.getArgument(0))).when(fault).batchInsert(anyList());
        var service=new AsyncLogServiceImpl(fault);
        try {
            service.saveLogAsync(row);service.flushPendingLogs();
            assertThat(service.pendingCount()).isEqualTo(1);
            assertThat(mapper.containsAudit(row)).isTrue();
            service.flushPendingLogs();
            assertThat(service.pendingCount()).isZero();
            verify(fault,times(1)).batchInsert(anyList());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.access_log WHERE request_id=?",Long.class,row.getRequestId())).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.access_log_partitioned WHERE request_id=?",Long.class,row.getRequestId())).isEqualTo(1L);
        } finally {jdbc.update("DELETE FROM app.access_log WHERE request_id=?",row.getRequestId());}
    }

    @Test
    void actualTerminatedDatabaseConnectionRetainsAuditAndLaterFlushRecovers() throws Exception {
        var row=row("batch7-terminated");
        var fault=mock(LogMapper.class,delegatesTo(mapper));
        var service=new AsyncLogServiceImpl(fault);
        try(Connection victim=java.sql.DriverManager.getConnection(postgresqlJdbcUrl(),postgresqlUsername(),postgresqlPassword())) {
            int pid;
            try(var statement=victim.createStatement();var rs=statement.executeQuery("SELECT pg_backend_pid()")) {rs.next();pid=rs.getInt(1);}
            assertThat(jdbc.queryForObject("SELECT pg_terminate_backend(?)",Boolean.class,pid)).isTrue();
            doAnswer(call->{
                try(var session=factory.openSession(victim)) {
                    session.getMapper(LogMapper.class).batchInsert(call.getArgument(0));
                } catch(RuntimeException failure) {
                    throw new org.springframework.dao.DataAccessResourceFailureException("isolated terminated connection",failure);
                }
                throw new AssertionError("terminated connection unexpectedly wrote");
            }).doAnswer(call->mapper.batchInsert(call.getArgument(0))).when(fault).batchInsert(anyList());
            service.saveLogAsync(row);service.flushPendingLogs();
            assertThat(service.pendingCount()).isEqualTo(1);
            assertThat(mapper.containsAudit(row)).isFalse();
            service.flushPendingLogs();
            assertThat(service.pendingCount()).isZero();
            assertThat(mapper.containsAudit(row)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.access_log WHERE request_id=?",Long.class,row.getRequestId())).isEqualTo(1L);
            System.out.println("MRR_AUDIT_RECOVERY terminated isolated PG connection; retained then persisted exactly once");
        } finally {jdbc.update("DELETE FROM app.access_log WHERE request_id=?",row.getRequestId());}
    }
}
