package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.StorageCircuitHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class StorageCircuitHistoryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void preservesEventsAcrossReaderRestartAndDeduplicatesOverlappingBuffers() {
        var repo=new StorageCircuitHistoryRepository(jdbc);
        Instant at=Instant.parse("2026-01-01T00:00:00Z");
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var opened=new StorageCircuitHistoryRepository.Event(first,1,"NAS",1,"OPENED",at,at.plusSeconds(5));
        var recovered=new StorageCircuitHistoryRepository.Event(first,2,"NAS",1,"RECOVERED",at.plusSeconds(2),at.plusSeconds(5));
        var restarted=new StorageCircuitHistoryRepository.Event(second,1,"NAS",1,"OPENED",at.plusSeconds(10),at.plusSeconds(12));
        repo.record(List.of(opened,recovered));
        repo.record(List.of(new StorageCircuitHistoryRepository.Event(first,1,"NAS",2,"OPENED",at,at.plusSeconds(7)),restarted));
        assertThat(new StorageCircuitHistoryRepository(jdbc).read(at,at.plusSeconds(20))).containsExactly(restarted,recovered,opened);
        assertThat(repo.prune(at.plusSeconds(2))).isEqualTo(1);
        assertThat(repo.read(at,at.plusSeconds(20))).containsExactly(restarted,recovered);
    }

    @Test void readAndPruneAreBounded() {
        var repo=new StorageCircuitHistoryRepository(jdbc);
        var at=Instant.parse("2026-01-01T00:00:00Z");
        var circuit=UUID.randomUUID();
        var rows=java.util.stream.IntStream.rangeClosed(1,510).mapToObj(i->new StorageCircuitHistoryRepository.Event(
                circuit,i,"HTTP",1,"OPENED",at.plusSeconds(i),at.plusSeconds(520))).toList();
        assertThatThrownBy(()->repo.record(rows)).isInstanceOf(IllegalArgumentException.class);
        repo.record(rows.subList(0,256));repo.record(rows.subList(256,510));
        assertThat(repo.read(at,at.plusSeconds(600))).hasSize(101);
        assertThat(repo.prune(at.plusSeconds(600))).isEqualTo(500);
        assertThat(repo.read(at,at.plusSeconds(600))).hasSize(10);
    }
}
