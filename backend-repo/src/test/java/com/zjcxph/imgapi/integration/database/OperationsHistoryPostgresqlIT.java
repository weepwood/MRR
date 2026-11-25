package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.OperationsHistoryRepository;
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
class OperationsHistoryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test
    void deduplicatesBucketAndPreservesUnknownAndRestartedReader() {
        var repo = new OperationsHistoryRepository(jdbc);
        var start = Instant.parse("2026-01-01T00:00:10Z");
        var a = new OperationsHistoryRepository.Sample(start,start,"READ_WRITE",0,100,200,null,null,null);
        var b = new OperationsHistoryRepository.Sample(start.plusSeconds(300),start,"READ_ONLY_DEGRADED",1,150,200,1000L,2,3);
        assertThat(repo.record(a)).isEqualTo(1);
        assertThat(repo.record(a)).isZero();
        assertThat(repo.record(b)).isEqualTo(1);
        assertThat(new OperationsHistoryRepository(jdbc).read(start,start.plusSeconds(600))).containsExactly(a,b);
        assertThat(repo.prune(start.plusSeconds(300))).isEqualTo(1);
        assertThat(repo.read(start,start.plusSeconds(600))).containsExactly(b);
    }
}
