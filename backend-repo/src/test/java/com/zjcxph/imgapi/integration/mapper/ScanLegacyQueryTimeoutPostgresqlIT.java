package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ScanMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Proves driver cancellation against real PostgreSQL lock waits, not just mapper metadata. */
@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {"mybatis.mapper-locations=classpath*:mapper/*.xml"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScanLegacyQueryTimeoutPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired DataSource dataSource;
    @Autowired SqlSessionFactory sessions;

    enum LegacyRead { LIST, PAGE, CONDITION, CONDITION_PAGE, COUNT }

    @ParameterizedTest(name = "{0} cancels its statement and recovers after lock release")
    @EnumSource(LegacyRead.class)
    void cancelsBlockedLegacyReadAndCanQueryAgain(LegacyRead read) throws Exception {
        var release = Executors.newSingleThreadScheduledExecutor();
        try (var session = sessions.openSession(true);
             Connection blocker = dataSource.getConnection()) {
            ScanMapper reader = session.getMapper(ScanMapper.class);
            Connection connection = session.getConnection();
            assertThat(connection.getAutoCommit()).isTrue();
            int backend = backendPid(connection);
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("LOCK TABLE app.mr_scan IN ACCESS EXCLUSIVE MODE");
            }
            // RED terminates too: the old unbounded query succeeds after this lock release.
            var fallback = release.schedule(() -> {
                try { blocker.rollback(); }
                catch (java.sql.SQLException exception) { throw new IllegalStateException(exception); }
            }, 6, TimeUnit.SECONDS);
            long started = System.nanoTime();
            Throwable failure;
            try {
                failure = catchThrowable(() -> invoke(reader, read));
            } finally {
                fallback.cancel(false);
                blocker.rollback();
            }
            assertThat(failure).as("JDBC must cancel %s before the six-second fallback", read).isNotNull();
            Throwable root = failure;
            while (root.getCause() != null) root = root.getCause();
            assertThat(root).isInstanceOf(PSQLException.class);
            assertThat(((PSQLException) root).getSQLState()).isEqualTo("57014");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(6));
            // Reuse the actual cancelled connection and rerun the same read after release.
            invoke(reader, read);
            assertThat(backendPid(connection)).isEqualTo(backend);
        } finally {
            release.shutdownNow();
            assertThat(release.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private int backendPid(Connection connection) throws java.sql.SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private void invoke(ScanMapper mapper, LegacyRead read) {
        switch (read) {
            case LIST -> mapper.findAll(1);
            case PAGE -> mapper.findAllWithPagination(0, 1);
            case CONDITION -> mapper.findByCondition(new ScanRequest(), 1);
            case CONDITION_PAGE -> mapper.findByConditionWithPagination(new ScanRequest(), 0, 1);
            case COUNT -> mapper.countByCondition(new ScanRequest());
        }
    }
}
