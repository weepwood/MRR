package com.zjcxph.imgapi.integration.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.DataExchangeExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verify the real export outside a test transaction, with only isolated synthetic patients. */
@MybatisTest
@Import(DataExchangeExportService.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CsvExportConnectionPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired DataExchangeExportService exporter;
    private String prefix;

    @BeforeEach void seed() {
        prefix = "synthetic-csv-" + UUID.randomUUID();
        jdbc.update("""
                insert into app.mr_patient (bah, name, department)
                select i::text, ? || '-' || i, 'synthetic department'
                from generate_series(1,2501) i
                """, prefix);
    }

    @AfterEach void clean() {
        if (prefix != null) jdbc.update("delete from app.mr_patient where name like ?", prefix + "-%");
    }

    @Test void completeExportPreservesOrderAndReturnsConnection() throws Exception {
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        int baseline = pool.getActiveConnections();
        var output = new StringWriter();
        exporter.exportPatients(prefix, output);
        var lines = output.toString().lines().toList();
        assertThat(lines).hasSize(2502);
        assertThat(lines.getFirst()).isEqualTo("\uFEFFbah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei");
        for (int i=1; i<=2501; i++) {
            assertThat(lines.get(i)).startsWith(i + "," + prefix + "-" + i + ",");
        }
        assertThat(pool.getActiveConnections()).isEqualTo(baseline);
    }

    @Test void failedConsumerReturnsConnectionAndDoesNotPoisonPool() throws Exception {
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        int baseline = pool.getActiveConnections();
        var observed = new java.util.concurrent.atomic.AtomicBoolean();
        IOException disconnect = new IOException("synthetic client disconnected");
        Writer consumer = new Writer() {
            int lines;
            @Override public void write(char[] chars, int offset, int length) throws IOException {
                for (int i=offset; i<offset+length; i++) {
                    if (chars[i]=='\n' && ++lines==2) {
                        assertThat(pool.getActiveConnections()).isGreaterThan(baseline);
                        // Observe the actual server-side connection while a downstream consumer is stalled.
                        String state=jdbc.queryForObject("""
                                select state || '/xact=' || (xact_start is not null)::text
                                from pg_stat_activity
                                where pid<>pg_backend_pid() and datname=current_database()
                                  and query like 'select bah, name, idcard,%'
                                order by query_start desc limit 1
                                """,String.class);
                        assertThat(state).isNotBlank();
                        System.out.println("MRR_CSV_ACTUAL_CONSUMER " + state);
                        observed.set(true);
                        throw disconnect;
                    }
                }
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        assertThatThrownBy(() -> exporter.exportPatients(prefix, consumer)).isSameAs(disconnect);
        assertThat(observed.get()).isTrue();
        assertThat(pool.getActiveConnections()).isEqualTo(baseline);
        assertThat(jdbc.queryForObject("select count(*) from app.mr_patient where name like ?",Long.class,prefix+"-%")).isEqualTo(2501L);
        assertThat(pool.getActiveConnections()).isEqualTo(baseline);
    }
}
