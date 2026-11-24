package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.SystemAvailabilityRepository;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AvailabilityRetentionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void boundedCleanupPreservesOpenRecentAndCutoffPeriods() {
        // Test transaction rolls back all fixtures and cleanup. No production DB.
        jdbc.update("DELETE FROM app.system_availability_period");
        jdbc.update("""
                INSERT INTO app.system_availability_period(status, started_at, ended_at, last_heartbeat_at)
                SELECT 'UP', '2000-01-01T00:00:00Z'::timestamptz + i * interval '1 second',
                    '2000-01-02T00:00:00Z'::timestamptz, '2000-01-02T00:00:00Z'::timestamptz
                FROM generate_series(1,1001) i
                """);
        jdbc.update("""
                INSERT INTO app.system_availability_period(status, started_at, ended_at, last_heartbeat_at)
                VALUES
                ('UP','2000-01-01T00:00:00Z','2001-01-01T00:00:00Z','2001-01-01T00:00:00Z'),
                ('DOWN','2001-01-01T00:00:00Z','2001-01-02T00:00:00Z','2001-01-02T00:00:00Z'),
                ('UP','2000-01-01T00:00:00Z',NULL,'2001-01-02T00:00:00Z')
                """);
        var repository = new SystemAvailabilityRepository(jdbc);
        var cutoff = Instant.parse("2001-01-01T00:00:00Z");
        assertThat(repository.deleteEndedBefore(cutoff)).isEqualTo(1000);
        assertThat(repository.deleteEndedBefore(cutoff)).isEqualTo(1);
        assertThat(repository.deleteEndedBefore(cutoff)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app.system_availability_period", Long.class)).isEqualTo(3L);
        assertThat(repository.findOpenPeriod()).isPresent();
    }
}
