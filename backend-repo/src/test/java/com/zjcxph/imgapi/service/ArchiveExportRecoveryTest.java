package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportSummary;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository.RecoveryCursor;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArchiveExportRecoveryTest {
    private final ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
    private final ArchiveExportService exporter = mock(ArchiveExportService.class);
    private final ArchiveExportTempFileManager files = mock(ArchiveExportTempFileManager.class);
    private final CapacityExecutor executor = new CapacityExecutor(1);
    private final ArchiveExportProperties properties = new ArchiveExportProperties();
    private ArchiveExportJobService service;
    private final List<ArchiveExportJob> rows = new ArrayList<>();
    private final Comparator<ArchiveExportJob> order = Comparator.comparing(ArchiveExportJob::getCreatedAt)
            .thenComparing(ArchiveExportJob::getId);

    @BeforeEach
    void setUp() {
        properties.setWorkerQueueCapacity(1);
        service = new ArchiveExportJobService(exporter, repository, files, properties, executor);
        when(repository.resetToPending(anyString())).thenReturn(1);
        when(repository.findRecoveryUpperBound()).thenAnswer(invocation -> rows.stream().filter(this::recoverable)
                .max(order).map(this::cursor));
        when(repository.findRecoveryPage(nullable(RecoveryCursor.class), any(RecoveryCursor.class), anyInt()))
                .thenAnswer(invocation -> {
                    RecoveryCursor after = invocation.getArgument(0);
                    RecoveryCursor upper = invocation.getArgument(1);
                    int limit = invocation.getArgument(2);
                    return rows.stream().filter(this::recoverable).filter(row -> compare(cursor(row), upper) <= 0)
                            .filter(row -> after == null || compare(cursor(row), after) > 0)
                            .sorted(order).limit(limit).toList();
                });
        when(repository.findById(anyString())).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst());
        doAnswer(invocation -> {
            ArchiveExportJob row = invocation.getArgument(0);
            row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)); rows.add(row); return null;
        }).when(repository).insert(any());
        when(exporter.summarizeArchive(anyString(), anyString()))
                .thenReturn(new ArchiveExportSummary(1, 1L));
    }

    @Test
    void recoveryDoesNotFailJobsWhenTheExecutorIsTemporarilyFull() {
        ArchiveExportJob first = job();
        ArchiveExportJob second = job();
        rows.addAll(List.of(first, second));

        service.recoverJobsAfterRestart();
        service.continueStartupRecovery();

        assertThat(executor.tasks).hasSize(1);
        verify(repository, never()).markFailed(anyString(), anyString(), any());
        executor.tasks.removeFirst().run();
        service.continueStartupRecovery();
        assertThat(executor.tasks).hasSize(1);
    }

    @Test
    void repeatedReadyDoesNotResetAJobAlreadyAcceptedByThisProcess() {
        ArchiveExportJob first = job();
        rows.add(first);
        service.recoverJobsAfterRestart();
        service.recoverJobsAfterRestart();

        verify(repository, times(1)).resetToPending(first.getId());
        verify(repository, never()).markFailed(anyString(), anyString(), any());
        assertThat(executor.tasks).hasSize(1);
    }

    @Test
    void aJobCreatedBeforeReadyIsNotResetByStartupRecovery() {
        AtomicReference<ArchiveExportJob> created = new AtomicReference<>();
        doAnswer(invocation -> {
            ArchiveExportJob row = invocation.getArgument(0);
            row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)); rows.add(row);
            created.set(row); return null;
        }).when(repository).insert(any());
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(created.get()));
        ArchiveExportJobRequest request = new ArchiveExportJobRequest();
        request.setFormat("ZIP");
        request.setBah("00001234");
        AuthSession session = new AuthSession();
        session.setId(1L); session.setUsername("synthetic-export-user");
        session.setPermissions(List.of("record:download"));

        service.create(session, request);
        service.recoverJobsAfterRestart();

        verify(repository, never()).resetToPending(created.get().getId());
        assertThat(executor.tasks).hasSize(1);
    }

    private ArchiveExportJob job() {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId(UUID.randomUUID().toString()); job.setStatus("PENDING");
        job.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return job;
    }

    @Test
    void recoveryDuringInsertCannotClaimTheNewJob() {
        doAnswer(invocation -> {
            ArchiveExportJob row = invocation.getArgument(0);
            row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)); rows.add(row);
            service.recoverJobsAfterRestart();
            return null;
        }).when(repository).insert(any());
        service.create(session(), request());
        verify(repository, never()).resetToPending(anyString());
        assertThat(executor.tasks).hasSize(1);
    }

    @Test
    void startupBoundaryFailureCanRetryWithoutAnotherReadyEvent() {
        ArchiveExportJob row = job(); rows.add(row);
        when(repository.findRecoveryUpperBound()).thenThrow(new DataAccessResourceFailureException("synthetic"))
                .thenReturn(Optional.of(cursor(row)));
        service.recoverJobsAfterRestart();
        assertThat(executor.tasks).isEmpty();
        service.continueStartupRecovery();
        assertThat(executor.tasks).hasSize(1);
        verify(repository, times(2)).findRecoveryUpperBound();
    }

    @Test
    void resetFailureReleasesOwnershipAndRetriesTheSameCandidate() {
        ArchiveExportJob row = job(); rows.add(row);
        when(repository.resetToPending(row.getId()))
                .thenThrow(new DataAccessResourceFailureException("synthetic")).thenReturn(1);
        service.recoverJobsAfterRestart();
        assertThat(owners()).isEmpty();
        service.continueStartupRecovery();
        assertThat(executor.tasks).hasSize(1);
        verify(repository, times(2)).resetToPending(row.getId());
    }

    @Test
    void sameTimestampPagesNeverResetOrSubmitTheSameRowTwice() {
        rows.addAll(List.of(job(), job(), job()));
        service.recoverJobsAfterRestart();
        for (int index = 0; index < 3; index++) {
            executor.tasks.removeFirst().run();
            service.continueStartupRecovery();
        }
        for (ArchiveExportJob row : rows) verify(repository).resetToPending(row.getId());
        assertThat(executor.tasks).isEmpty();
        assertThat(owners()).isEmpty();
    }

    @Test
    void cancellationBetweenQueryAndResetUsesConditionalCleanup() {
        ArchiveExportJob row = job(); row.setFilePath("synthetic-path"); rows.add(row);
        when(repository.resetToPending(row.getId())).thenAnswer(invocation -> {
            row.setCancelRequested(true); return 0;
        });
        when(repository.markCancelled(eq(row.getId()), any())).thenReturn(1);
        service.recoverJobsAfterRestart();
        verify(files).deleteManagedFile("synthetic-path");
        assertThat(executor.tasks).isEmpty(); assertThat(owners()).isEmpty();
    }

    @Test
    void aTerminalWinnerKeepsItsSuccessfulFile() {
        ArchiveExportJob row = job(); row.setCancelRequested(true); row.setFilePath("synthetic-success"); rows.add(row);
        when(repository.markCancelled(eq(row.getId()), any())).thenAnswer(invocation -> {
            row.setStatus("SUCCESS"); return 0;
        });
        service.recoverJobsAfterRestart();
        verifyNoInteractions(files);
        assertThat(owners()).isEmpty();
    }

    @Test
    void normalCreationStillFailsOnRejectionAndDoesNotLeakOwnership() {
        service.create(session(), request());
        service.create(session(), request());
        verify(repository).markFailed(anyString(), contains("队列已满"), any());
        assertThat(owners()).hasSize(1);
        executor.tasks.removeFirst().run();
        assertThat(owners()).isEmpty();
    }

    @Test
    void insertFailureAndUnexpectedExecutorFailureReleaseOwnership() {
        doThrow(new DataAccessResourceFailureException("synthetic")).when(repository).insert(any());
        assertThatThrownBy(() -> service.create(session(), request())).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(owners()).isEmpty();
        doAnswer(invocation -> {
            ArchiveExportJob row = invocation.getArgument(0); row.setCreatedAt(LocalDateTime.now()); rows.add(row); return null;
        }).when(repository).insert(any());
        executor.failure = new IllegalStateException("synthetic");
        assertThatThrownBy(() -> service.create(session(), request())).isInstanceOf(IllegalStateException.class);
        assertThat(owners()).isEmpty();
    }

    @Test
    void idempotencyConflictDoesNotKeepTheUnusedNewIdentity() {
        ArchiveExportJob existing = job(); existing.setFormat("ZIP"); existing.setScope("WHOLE_ARCHIVE");
        existing.setBah("00001234"); existing.setSjh(""); rows.add(existing);
        ArchiveExportJobRequest request = request(); request.setIdempotencyKey("synthetic-key");
        when(repository.findByIdempotency(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        doThrow(new DuplicateKeyException("synthetic")).when(repository).insert(any());
        assertThat(service.create(session(), request).id()).isEqualTo(existing.getId());
        assertThat(owners()).isEmpty(); assertThat(executor.tasks).isEmpty();
    }

    @Test
    void workerPrefixFailureStillReleasesOwnership() {
        service.create(session(), request());
        when(repository.findById(anyString())).thenThrow(new DataAccessResourceFailureException("synthetic"));
        assertThatThrownBy(() -> executor.tasks.removeFirst().run()).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(owners()).isEmpty();
    }

    @Test
    void workerClaimFailureStillReleasesOwnership() {
        service.create(session(), request());
        when(repository.markProcessing(anyString(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("synthetic"));
        assertThatThrownBy(() -> executor.tasks.removeFirst().run()).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(owners()).isEmpty();
    }

    @Test
    void aShuttingDownExecutorLeavesRecoveryPendingForTheNextProcess() {
        rows.add(job()); executor.failure = new RejectedExecutionException("synthetic-shutdown");
        service.recoverJobsAfterRestart();
        service.continueStartupRecovery();
        assertThat(owners()).isEmpty(); assertThat(executor.tasks).isEmpty();
        verify(repository, never()).markFailed(anyString(), anyString(), any());
    }

    @Test
    void aRejectedNewJobStaysOwnedUntilItsFailureHasBeenRecorded() {
        String firstId = service.create(session(), request()).id();
        doAnswer(invocation -> {
            executor.tasks.removeFirst().run();
            rows.stream().filter(row -> row.getId().equals(firstId)).findFirst().orElseThrow().setStatus("SUCCESS");
            service.recoverJobsAfterRestart();
            rows.stream().filter(row -> row.getId().equals(invocation.getArgument(0)))
                    .findFirst().orElseThrow().setStatus("FAILED");
            return 1;
        }).when(repository).markFailed(anyString(), anyString(), any());
        service.create(session(), request());
        verify(repository, never()).resetToPending(anyString());
        assertThat(executor.tasks).isEmpty(); assertThat(owners()).isEmpty();
    }

    private RecoveryCursor cursor(ArchiveExportJob row) { return new RecoveryCursor(row.getCreatedAt(), row.getId()); }
    private boolean recoverable(ArchiveExportJob row) { return List.of("PENDING", "PROCESSING").contains(row.getStatus()); }
    private int compare(RecoveryCursor first, RecoveryCursor second) {
        int time = first.createdAt().compareTo(second.createdAt());
        return time == 0 ? first.id().compareTo(second.id()) : time;
    }
    private Set<?> owners() { return (Set<?>) ReflectionTestUtils.getField(service, "submittedJobIds"); }
    private AuthSession session() {
        AuthSession session = new AuthSession(); session.setId(1L); session.setUsername("synthetic-export-user");
        session.setPermissions(List.of("record:download")); return session;
    }
    private ArchiveExportJobRequest request() {
        ArchiveExportJobRequest request = new ArchiveExportJobRequest(); request.setFormat("ZIP"); request.setBah("00001234");
        return request;
    }

    private static final class CapacityExecutor implements Executor {
        private final int capacity;
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private RuntimeException failure;
        CapacityExecutor(int capacity) { this.capacity = capacity; }
        @Override public void execute(Runnable task) {
            if (failure != null) throw failure;
            if (tasks.size() >= capacity) throw new RejectedExecutionException("synthetic-full");
            tasks.add(task);
        }
    }
}
