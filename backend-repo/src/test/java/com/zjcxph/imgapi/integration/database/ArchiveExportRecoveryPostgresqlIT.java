package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository.RecoveryCursor;
import com.zjcxph.imgapi.service.ArchiveExportJobService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArchiveExportRecoveryPostgresqlIT extends PostgresqlIntegrationTestSupport {
    private static final LocalDateTime SAME_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;
    private ArchiveExportJobRepository repository;

    @BeforeEach
    void clearDisposableJobs() {
        jdbc.update("DELETE FROM app.archive_export_job");
        repository = new ArchiveExportJobRepository(jdbc);
    }

    @Test
    void emptyRecoveryHasNoBoundaryOrPage() {
        assertThat(repository.findRecoveryUpperBound()).isEmpty();
        assertThat(repository.findRecoveryPage(null, null, 20)).isEmpty();
    }

    @Test
    void equalTimestampsUseUuidAsAStableTieBreakerAcrossPages() {
        insert(3); insert(1); insert(2);
        var upper = repository.findRecoveryUpperBound().orElseThrow();
        assertThat(upper.id()).isEqualTo(id(3));
        var first = repository.findRecoveryPage(null, upper, 1);
        assertThat(first).extracting(ArchiveExportJob::getId).containsExactly(id(1));
        var second = repository.findRecoveryPage(cursor(first.getFirst()), upper, 1);
        assertThat(second).extracting(ArchiveExportJob::getId).containsExactly(id(2));
        var third = repository.findRecoveryPage(cursor(second.getFirst()), upper, 1);
        assertThat(third).extracting(ArchiveExportJob::getId).containsExactly(id(3));
        assertThat(repository.findRecoveryPage(cursor(third.getFirst()), upper, 1)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED", "CANCELLED", "EXPIRED"})
    void aCapturedBoundaryExcludesNewerJobsAndIgnoresTerminalStates(String terminal) {
        insert(1); insert(2);
        jdbc.update("UPDATE app.archive_export_job SET status=? WHERE id=CAST(? AS UUID)", terminal, id(2));
        var upper = repository.findRecoveryUpperBound().orElseThrow();
        insert(3);
        jdbc.update("UPDATE app.archive_export_job SET created_at=? WHERE id=CAST(? AS UUID)", SAME_TIME.plusSeconds(1), id(3));
        assertThat(repository.findRecoveryPage(null, upper, 20))
                .extracting(ArchiveExportJob::getId).containsExactly(id(1));
    }

    @Test
    void terminalRowsDisappearingBetweenPagesDoNotCauseOffsetSkips() {
        insert(1); insert(2); insert(3);
        var upper = repository.findRecoveryUpperBound().orElseThrow();
        var first = repository.findRecoveryPage(null, upper, 1).getFirst();
        jdbc.update("UPDATE app.archive_export_job SET status='CANCELLED' WHERE id=CAST(? AS UUID)", id(2));
        assertThat(repository.findRecoveryPage(cursor(first), upper, 1))
                .extracting(ArchiveExportJob::getId).containsExactly(id(3));
    }

    @Test
    void repositoryCapsEvenAnOversizedPageRequestAtTwoHundredRows() {
        for (int index = 1; index <= 205; index++) insert(index);
        var upper = repository.findRecoveryUpperBound().orElseThrow();
        assertThat(repository.findRecoveryPage(null, upper, Integer.MAX_VALUE)).hasSize(200);
        assertThat(repository.findRecoveryPage(null, upper, 0)).hasSize(1);
    }

    @Test
    void aOneSlotExecutorEventuallyCompletesAllRecoveredJobsWithoutFailingOverflow() throws Exception {
        insert(1); insert(2); insert(3);
        Path partial = Files.write(directory.resolve(id(1) + ".zip"), new byte[]{2, 3});
        jdbc.update("UPDATE app.archive_export_job SET status='PROCESSING', file_path=? WHERE id=CAST(? AS UUID)",
                partial.toString(), id(1));
        jdbc.update("UPDATE app.archive_export_job SET expires_at=? WHERE id=CAST(? AS UUID)", SAME_TIME.minusDays(1), id(2));
        var executor = new OneSlotExecutor();
        ArchiveExportService exporter = exporter();
        var service = service(repository, exporter, executor);
        service.recoverJobsAfterRestart();
        service.continueStartupRecovery();
        assertThat(repository.findById(id(2)).orElseThrow().getStatus()).isEqualTo("PENDING");
        for (int index = 1; index <= 3; index++) {
            executor.tasks.removeFirst().run();
            service.continueStartupRecovery();
        }
        service.recoverJobsAfterRestart();
        assertThat(executor.tasks).isEmpty();
        for (int index = 1; index <= 3; index++) {
            var job = repository.findById(id(index)).orElseThrow();
            assertThat(job.getStatus()).isEqualTo("SUCCESS");
            assertThat(job.getOutputBytes()).isEqualTo(1);
            assertThat(Path.of(job.getFilePath())).exists().hasSize(1);
            assertThat(job.getExpiresAt()).isAfter(LocalDateTime.now());
        }
        verify(exporter, times(3)).prepareArchive(anyString(), anyString());
    }

    @Test
    void cancellationAfterThePageReadPreventsSubmissionAndCleansOnlyTheCancelledFile() throws Exception {
        insert(1);
        Path partial = Files.write(directory.resolve("synthetic-partial.zip"), new byte[]{1});
        jdbc.update("UPDATE app.archive_export_job SET file_path=? WHERE id=CAST(? AS UUID)", partial.toString(), id(1));
        var racing = new ArchiveExportJobRepository(jdbc) {
            @Override public int resetToPending(String id) {
                requestCancel(id);
                return super.resetToPending(id);
            }
        };
        var executor = new OneSlotExecutor();
        service(racing, exporter(), executor).recoverJobsAfterRestart();
        assertThat(repository.findById(id(1)).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(partial).doesNotExist(); assertThat(executor.tasks).isEmpty();
    }

    @Test
    void aSuccessWinningTheCancellationUpdateKeepsItsFile() throws Exception {
        insert(1); repository.requestCancel(id(1));
        Path success = Files.write(directory.resolve("synthetic-success.zip"), new byte[]{1});
        jdbc.update("UPDATE app.archive_export_job SET file_path=? WHERE id=CAST(? AS UUID)", success.toString(), id(1));
        var racing = new ArchiveExportJobRepository(jdbc) {
            @Override public int markCancelled(String id, LocalDateTime time) {
                jdbc.update("UPDATE app.archive_export_job SET status='SUCCESS' WHERE id=CAST(? AS UUID)", id);
                return super.markCancelled(id, time);
            }
        };
        var executor = new OneSlotExecutor();
        service(racing, exporter(), executor).recoverJobsAfterRestart();
        assertThat(success).exists(); assertThat(executor.tasks).isEmpty();
        assertThat(repository.findById(id(1)).orElseThrow().getStatus()).isEqualTo("SUCCESS");
    }

    private void insert(int number) {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId(id(number)); job.setOwnerUserId(1L); job.setOwnerUsername("synthetic-export-user");
        job.setFormat("ZIP"); job.setScope("WHOLE_ARCHIVE"); job.setStatus("PENDING");
        job.setBah("00001234"); job.setSjh("00009999"); job.setPlannedCount(1); job.setEstimatedBytes(1L);
        job.setFileName("synthetic-" + number + ".zip"); job.setExpiresAt(SAME_TIME.plusYears(2));
        repository.insert(job);
        jdbc.update("UPDATE app.archive_export_job SET created_at=? WHERE id=CAST(? AS UUID)", SAME_TIME, job.getId());
    }

    private ArchiveExportService exporter() throws Exception {
        var exporter = mock(ArchiveExportService.class);
        PathDO image = new PathDO(); image.setFileSize(1L);
        when(exporter.prepareArchive(anyString(), anyString()))
                .thenReturn(new ArchiveExportService.BatchZipExport(List.of(image)));
        doAnswer(invocation -> { invocation.<OutputStream>getArgument(1).write(1); return null; })
                .when(exporter).writeBatchZip(any(), any(), any());
        return exporter;
    }

    private ArchiveExportJobService service(ArchiveExportJobRepository repository,
            ArchiveExportService exporter, Executor executor) throws Exception {
        var properties = new ArchiveExportProperties(); properties.setWorkerQueueCapacity(1);
        properties.setTempDirectory(directory.toString()); properties.setMaxFileBytes(10); properties.setMaxTotalBytes(100);
        var files = new ArchiveExportTempFileManager(properties); files.initialize();
        return new ArchiveExportJobService(exporter, repository, files, properties, executor);
    }
    private String id(int value) { return new UUID(0, value).toString(); }
    private RecoveryCursor cursor(ArchiveExportJob row) { return new RecoveryCursor(row.getCreatedAt(), row.getId()); }
    private static final class OneSlotExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) {
            if (!tasks.isEmpty()) throw new RejectedExecutionException("synthetic-full");
            tasks.add(task);
        }
    }
}
