package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.dto.resp.ArchiveExportSummary;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "mybatis.mapper-locations=classpath*:mapper/*.xml")
class ArchiveExportSummaryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired ScanMapper mapper;

    @Test
    void summaryCountsOnlyActiveRowsFromTheResolvedArchive() {
        long archive = archive("001234", "009999");
        long other = archive("005678", "008888");
        scan(archive, 1, 5L); scan(archive, 1, 7L); scan(archive, 0, 100L); scan(other, 1, 500L);
        assertThat(mapper.summarizeActiveByArchiveId(archive)).isEqualTo(new ArchiveExportSummary(2, 12));
    }

    @Test
    void anEmptyOrInactiveArchiveHasAZeroSummary() {
        long archive = archive("001234", "009999");
        assertThat(mapper.summarizeActiveByArchiveId(archive)).isEqualTo(new ArchiveExportSummary(0, 0));
        scan(archive, 0, 100L);
        assertThat(mapper.summarizeActiveByArchiveId(archive)).isEqualTo(new ArchiveExportSummary(0, 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void unknownOrNonPositiveSizesKeepTheExistingConservativeEstimate(Long size) {
        long archive = archive("001234", "009999");
        scan(archive, 1, 5L); scan(archive, 1, size);
        assertThat(mapper.summarizeActiveByArchiveId(archive)).isEqualTo(new ArchiveExportSummary(2, 0));
    }

    @Test
    void sizeSumSaturatesInsteadOfOverflowingTheLongBoundary() {
        long archive = archive("001234", "009999");
        scan(archive, 1, Long.MAX_VALUE); scan(archive, 1, 1L);
        assertThat(mapper.summarizeActiveByArchiveId(archive))
                .isEqualTo(new ArchiveExportSummary(2, Long.MAX_VALUE));
    }

    @Test
    void onlyTheActiveWorkerCanUpdateItsPlanAndCompletionUsesTheNewCount() {
        var repository = new ArchiveExportJobRepository(jdbc);
        String id = job(repository);
        assertThat(repository.updatePlan(id, 3, 50, "NGINX")).isZero();
        repository.markProcessing(id, "synthetic.zip", null, LocalDateTime.now().plusHours(1));
        assertThat(repository.updatePlan(id, 3, 50, "NGINX")).isEqualTo(1);
        assertThat(repository.markSuccess(id, 1, "synthetic-checksum", LocalDateTime.now())).isEqualTo(1);
        var completed = repository.findById(id).orElseThrow();
        assertThat(completed.getPlannedCount()).isEqualTo(3);
        assertThat(completed.getProcessedCount()).isEqualTo(3);
        assertThat(completed.getEstimatedBytes()).isEqualTo(50);
        assertThat(completed.getSourceSummary()).isEqualTo("NGINX");
        assertThat(repository.updatePlan(id, 100, 200, "OSS")).isZero();
        assertThat(repository.findById(id).orElseThrow().getPlannedCount()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "CANCELLED", "EXPIRED"})
    void terminalJobsCannotReceiveLatePlanUpdates(String status) {
        var repository = new ArchiveExportJobRepository(jdbc);
        String id = job(repository);
        jdbc.update("UPDATE app.archive_export_job SET status=? WHERE id=CAST(? AS UUID)", status, id);
        assertThat(repository.updatePlan(id, 99, 200, "OSS")).isZero();
        assertThat(repository.findById(id).orElseThrow().getPlannedCount()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancellationCommittingWhilePlanWaitsPreventsTheLateUpdate() throws Exception {
        var repository = new ArchiveExportJobRepository(jdbc);
        String id = job(repository);
        repository.markProcessing(id, "synthetic.zip", null, LocalDateTime.now().plusHours(1));
        var executor = Executors.newSingleThreadExecutor();
        try (Connection cancellation = jdbc.getDataSource().getConnection()) {
            cancellation.setAutoCommit(false);
            try (var statement = cancellation.prepareStatement(
                    "UPDATE app.archive_export_job SET cancel_requested=TRUE WHERE id=CAST(? AS UUID)")) {
                statement.setString(1, id); statement.executeUpdate();
            }
            var started = new CountDownLatch(1);
            var update = executor.submit(() -> {
                started.countDown(); return repository.updatePlan(id, 99, 200, "OSS");
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> update.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            cancellation.commit();
            assertThat(update.get(5, TimeUnit.SECONDS)).isZero();
            assertThat(repository.findById(id).orElseThrow().getPlannedCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM app.archive_export_job WHERE id=CAST(? AS UUID)", id);
        }
    }

    private long archive(String bah, String sjh) {
        return jdbc.queryForObject("INSERT INTO app.mr_archive(bah,sjh) VALUES (?,?) RETURNING id", Long.class, bah, sjh);
    }

    private void scan(long archive, int active, Long size) {
        jdbc.update("INSERT INTO app.mr_scan(archive_id,bah,sjh,folder,filename,uploadflag,file_size) "
                + "SELECT id,bah,sjh,'synthetic',?,?,? FROM app.mr_archive WHERE id=?",
                UUID.randomUUID() + ".jpg", active, size, archive);
    }

    private String job(ArchiveExportJobRepository repository) {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId(UUID.randomUUID().toString()); job.setOwnerUserId(1L); job.setOwnerUsername("synthetic-user");
        job.setFormat("ZIP"); job.setScope("WHOLE_ARCHIVE"); job.setStatus("PENDING");
        job.setPlannedCount(1); job.setEstimatedBytes(1L);
        repository.insert(job);
        return job.getId();
    }
}
