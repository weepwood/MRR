package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ArchiveExportJobService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.zjcxph.imgapi.config.PrivateJsonLogEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArchiveExportJobStatePostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;

    @Test
    void legacyOwnerCannotLeakThroughIdempotencyOrListLookup() {
        var repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        jdbc.update("UPDATE app.archive_export_job SET owner_user_id=NULL,idempotency_key='synthetic-legacy-key' WHERE id=CAST(? AS UUID)",id);
        assertThat(repository.findByIdempotency(42L,"synthetic-export-user","synthetic-legacy-key")).isEmpty();
        assertThat(repository.findActiveByOwner(42L,"synthetic-export-user","ZIP",20)).isEmpty();
        jdbc.update("UPDATE app.archive_export_job SET owner_user_id=42 WHERE id=CAST(? AS UUID)",id);
        assertThat(repository.findByIdempotency(42L,"renamed-user","synthetic-legacy-key")).get().extracting(ArchiveExportJob::getId).isEqualTo(id);
        assertThat(repository.findActiveByOwner(42L,"renamed-user","ZIP",20)).extracting(ArchiveExportJob::getId).containsExactly(id);
        assertThat(repository.findActiveByOwner(43L,"synthetic-export-user","ZIP",20)).isEmpty();
    }

    @Test
    void cancellationAtEndOfWritingCannotPublishASuccessfulFile() throws Exception {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        ArchiveExportService exporter = exporter();
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(new byte[]{1});
            repository.requestCancel(id);
            return null;
        }).when(exporter).writeBatchZip(any(), any(), any());

        ReflectionTestUtils.invokeMethod(service(repository, exporter), "runJob", id);

        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(directory.resolve(id + ".zip")).doesNotExist();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ZIP", "PDF"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedCancellationIsObservedDuringWritingWithoutLocalSignal(String format) throws Exception {
        var repository = spy(new ArchiveExportJobRepository(jdbc));
        String id = insert(repository);
        jdbc.update("UPDATE app.archive_export_job SET format=? WHERE id=CAST(? AS UUID)", format, id);
        var exporter = exporter();
        var observed = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.stubbing.Answer<Void> write = invocation -> {
            var progress = invocation.<ArchiveExportService.ExportProgress>getArgument(2);
            invocation.<OutputStream>getArgument(1).write(1);
            // A separate repository writes the durable flag; no in-process worker signal is delivered.
            assertThat(new ArchiveExportJobRepository(jdbc).requestCancel(id)).isEqualTo(1);
            observed.set(progress.isCancelled());
            if (observed.get()) throw new com.zjcxph.imgapi.exception.ArchiveExportCancelledException();
            invocation.<OutputStream>getArgument(1).write(2);
            return null;
        };
        doAnswer(write).when(exporter).writeBatchZip(any(),any(),any());
        doAnswer(write).when(exporter).writeBatchPdf(any(),any(),any());
        try {
            ReflectionTestUtils.invokeMethod(service(repository, exporter), "runJob", id);
            assertThat(observed.get()).as("durable cancellation observed before more output").isTrue();
            var job=repository.findById(id).orElseThrow();
            assertThat(job.getStatus()).isEqualTo("CANCELLED");
            assertThat(job.getOutputBytes()).isZero();
            assertThat(Path.of(job.getFilePath())).doesNotExist();
            verify(repository,never()).markSuccess(anyString(),anyLong(),anyString(),any());
        } finally {
            jdbc.update("DELETE FROM app.archive_export_job WHERE id=CAST(? AS UUID)",id);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"stale-cancel", "lost-claim", "pending-cancel"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unclaimedCancellationCannotSettleActiveWorker(String scenario) throws Exception {
        var repository = spy(new ArchiveExportJobRepository(jdbc));
        String id = insert(repository);
        var exporter = exporter();
        try {
            var independent = new ArchiveExportJobRepository(jdbc);
            Runnable claim = () -> {
                assertThat(independent.markProcessing(id,"synthetic.zip",null,LocalDateTime.now().plusHours(1))).isEqualTo(1);
                independent.updateProgress(id,1);
            };
            var service = service(repository,exporter);
            var session = new com.zjcxph.imgapi.common.AuthSession();
            session.setId(1L); session.setUsername("synthetic-export-user");
            session.setPermissions(List.of("record:read","record:download"));
            if ("lost-claim".equals(scenario)) {
                doAnswer(call -> { claim.run(); assertThat(independent.requestCancel(id)).isEqualTo(1); return 0; })
                        .when(repository).markProcessing(eq(id),any(),any(),any());
                ReflectionTestUtils.invokeMethod(service,"runJob",id);
            } else {
                if ("stale-cancel".equals(scenario)) doAnswer(call -> { claim.run(); return independent.requestCancel(id); })
                        .when(repository).requestCancel(id);
                service.cancel(session,id);
            }
            var current = independent.findById(id).orElseThrow();
            assertThat(current.getCancelRequested()).isTrue();
            if ("pending-cancel".equals(scenario)) {
                assertThat(current.getStatus()).isEqualTo("CANCELLED");
                assertThat(current.getCompletedAt()).isNotNull();
            } else {
                assertThat(current.getStatus()).isEqualTo("PROCESSING");
                assertThat(current.getProcessedCount()).isEqualTo(1);
                assertThat(current.getCompletedAt()).isNull();
                assertThat(independent.markCancelled(id,LocalDateTime.now())).isEqualTo(1);
                assertThat(independent.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
            }
            verifyNoInteractions(exporter);
        } finally { jdbc.update("DELETE FROM app.archive_export_job WHERE id=CAST(? AS UUID)",id); }
    }

    @Test
    void workerFailureDoesNotExposeStorageDetails() throws Exception {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        ArchiveExportService exporter = exporter();
        doThrow(new IOException("synthetic-private-path /synthetic/storage token=synthetic"))
                .when(exporter).writeBatchZip(any(), any(), any());

        AtomicReference<String> encoded = new AtomicReference<>();
        Logger logger = (Logger) LoggerFactory.getLogger(ArchiveExportJobService.class);
        var appender = new AppenderBase<ILoggingEvent>() {
            protected void append(ILoggingEvent event) {
                encoded.set(new String(new PrivateJsonLogEncoder().encode(event), StandardCharsets.UTF_8));
            }
        };
        appender.start();
        logger.addAppender(appender);
        String previous = MDC.get("requestId");
        MDC.put("requestId", "synthetic-parent-request");
        try {
            ReflectionTestUtils.invokeMethod(service(repository, exporter), "runJob", id);
            assertThat(encoded.get()).contains("\"requestId\":\"" + id + "\"")
                    .doesNotContain("synthetic-private-path", "/synthetic/storage", "token=synthetic");
            assertThat(MDC.get("requestId")).isEqualTo("synthetic-parent-request");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }

        ArchiveExportJob job = repository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorMessage()).isEqualTo("病案导出失败，请使用任务编号联系管理员");
    }

    @Test
    void failedCleanupBatchDoesNotStarveLaterExpiredFiles() throws Exception {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        Path blocked = Files.createDirectory(directory.toRealPath().resolve("synthetic-blocked.zip"));
        Files.writeString(blocked.resolve("child"), "synthetic");
        Path removable = Files.writeString(directory.toRealPath().resolve("synthetic-removable.zip"), "synthetic");
        String last = null;
        for (int index = 0; index < 201; index++) {
            String id = insert(repository);
            jdbc.update("UPDATE app.archive_export_job SET status='FAILED',file_path=?,"
                            + "expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour',"
                            + "updated_at=CURRENT_TIMESTAMP-INTERVAL '2 hours'+? * INTERVAL '1 second' WHERE id=CAST(? AS UUID)",
                    (index < 200 ? blocked : removable).toString(), index, id);
            last = id;
        }
        ArchiveExportJobService service = service(repository, mock(ArchiveExportService.class));
        service.cleanupExpiredFiles();
        assertThat(removable).exists();
        service.cleanupExpiredFiles();
        assertThat(removable).doesNotExist();
        assertThat(repository.findById(last).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(blocked.resolve("child")).exists();
    }

    @Test
    void progressWritesAreBatchedForTenThousandImages() throws Exception {
        ArchiveExportJobRepository repository = spy(new ArchiveExportJobRepository(jdbc));
        String id = insert(repository);
        ArchiveExportService exporter = exporter();
        doAnswer(invocation -> {
            var progress = invocation.<ArchiveExportService.ExportProgress>getArgument(2);
            for (int completed = 1; completed <= 10_000; completed++) {
                progress.onItemCompleted(completed, 10_000, null);
            }
            invocation.<OutputStream>getArgument(1).write(new byte[]{1});
            return null;
        }).when(exporter).writeBatchZip(any(), any(), any());

        ReflectionTestUtils.invokeMethod(service(repository, exporter), "runJob", id);

        verify(repository, atMost(110)).updateProgress(eq(id), anyInt());
        verify(repository, atLeastOnce()).updateProgress(eq(id), eq(10_000));
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void ambiguousSuccessAcknowledgementDoesNotDeleteCommittedOutput() throws Exception {
        ArchiveExportJobRepository repository = spy(new ArchiveExportJobRepository(jdbc));
        String id = insert(repository);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new org.springframework.dao.DataAccessResourceFailureException("synthetic acknowledgement lost");
        }).when(repository).markSuccess(eq(id), anyLong(), anyString(), any());
        ArchiveExportService exporter = exporter();
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(new byte[]{1});
            return null;
        }).when(exporter).writeBatchZip(any(), any(), any());

        ReflectionTestUtils.invokeMethod(service(repository, exporter), "runJob", id);

        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("SUCCESS");
        assertThat(directory.resolve(id + ".zip")).exists();
    }

    @Test
    void changedOutputLengthIsRejectedWithoutLeakingDownloadQuotaOrRewritingSuccess() throws Exception {
        var repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        var exporter = exporter();
        byte[] original = {1, 2, 3, 4};
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(original);
            return null;
        }).when(exporter).writeBatchZip(any(), any(), any());
        var service = service(repository, exporter);
        ReflectionTestUtils.invokeMethod(service, "runJob", id);
        var completed = repository.findById(id).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getOutputBytes()).isEqualTo(4L);
        var session = new com.zjcxph.imgapi.common.AuthSession();
        session.setId(1L);
        session.setUsername("synthetic-export-user");
        session.setPermissions(List.of("record:read", "record:download"));
        Path output = Path.of(completed.getFilePath());
        try (var lease = service.openDownload(session, id)) {
            assertThat(lease.length()).isEqualTo(4L);
        }
        Files.write(output, new byte[]{1, 2});
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> {
                try (var ignored = service.openDownload(session, id)) { }
            }).isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class)
              .satisfies(error -> assertThat(((com.zjcxph.imgapi.exception.BusinessException) error).getCode()).isEqualTo(409));
        }
        assertThat(Files.readAllBytes(output)).containsExactly((byte)1, (byte)2);
        var unchanged = repository.findById(id).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo("SUCCESS");
        assertThat(unchanged.getSha256()).isEqualTo(completed.getSha256());
        assertThat(unchanged.getOutputBytes()).isEqualTo(4L);
        Files.write(output, original);
        try (var lease = service.openDownload(session, id)) {
            assertThat(lease.length()).isEqualTo(4L);
        }
    }

    private ArchiveExportService exporter() {
        ArchiveExportService exporter = mock(ArchiveExportService.class);
        PathDO item = new PathDO();
        item.setFileSize(1L);
        when(exporter.prepareSelectedArchive(anyList())).thenReturn(new ArchiveExportService.BatchZipExport(List.of(item)));
        return exporter;
    }

    private ArchiveExportJobService service(ArchiveExportJobRepository repository, ArchiveExportService exporter) throws Exception {
        ArchiveExportProperties properties = new ArchiveExportProperties();
        properties.setTempDirectory(directory.toString());
        var files = new ArchiveExportTempFileManager(properties);
        files.initialize();
        return new ArchiveExportJobService(exporter, repository, files, properties, Runnable::run);
    }

    @Test
    void terminalStatesCannotBeOverwrittenByWorkersOrCancellation() {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        for (String status : List.of("SUCCESS", "FAILED", "CANCELLED", "EXPIRED")) {
            String id = insert(repository);
            jdbc.update("UPDATE app.archive_export_job SET status=? WHERE id=CAST(? AS UUID)", status, id);
            repository.markProcessing(id, "synthetic.zip", "controlled-placeholder", LocalDateTime.now());
            repository.markSuccess(id, 1, "synthetic-checksum", LocalDateTime.now());
            repository.markFailed(id, "synthetic-failure", LocalDateTime.now());
            repository.markCancelled(id, LocalDateTime.now());
            repository.resetToPending(id);
            assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(status);
        }
    }

    @Test
    void cancellationRecordedBeforeCompletionPreventsSuccess() {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        repository.markProcessing(id, "synthetic.zip", "controlled-placeholder", LocalDateTime.now());
        repository.requestCancel(id);
        repository.markSuccess(id, 1, "synthetic-checksum", LocalDateTime.now());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("PROCESSING");
        repository.markCancelled(id, LocalDateTime.now());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void pendingCancellationPreventsWorkerClaimAndFailureCannotReplaceIt() {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        repository.requestCancel(id);
        repository.markProcessing(id, "synthetic.zip", "controlled-placeholder", LocalDateTime.now());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
        repository.markCancelled(id, LocalDateTime.now());
        repository.markFailed(id, "synthetic-failure", LocalDateTime.now());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void expiryDoesNotTerminateAnActiveTask() {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        repository.markExpired(id);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("PENDING");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completionWaitingOnConcurrentCancellationRechecksTheCommittedFlag() throws Exception {
        ArchiveExportJobRepository repository = new ArchiveExportJobRepository(jdbc);
        String id = insert(repository);
        var executor = Executors.newSingleThreadExecutor();
        try (var connection = jdbc.getDataSource().getConnection()) {
            repository.markProcessing(id, "synthetic.zip", "controlled-placeholder", LocalDateTime.now());
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "UPDATE app.archive_export_job SET cancel_requested=TRUE WHERE id=CAST(? AS UUID)")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            CountDownLatch started = new CountDownLatch(1);
            var completion = executor.submit(() -> {
                started.countDown();
                repository.markSuccess(id, 1, "synthetic-checksum", LocalDateTime.now());
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> completion.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            connection.commit();
            completion.get(5, TimeUnit.SECONDS);
            assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("PROCESSING");
            repository.markCancelled(id, LocalDateTime.now());
            assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM app.archive_export_job WHERE id=CAST(? AS UUID)", id);
        }
    }

    private String insert(ArchiveExportJobRepository repository) {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId(UUID.randomUUID().toString());
        job.setOwnerUserId(1L);
        job.setOwnerUsername("synthetic-export-user");
        job.setFormat("ZIP");
        job.setScope("SELECTED_IMAGES");
        job.setStatus("PENDING");
        job.setPlannedCount(1);
        job.setEstimatedBytes(1L);
        repository.insert(job);
        return job.getId();
    }
}
