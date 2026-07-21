package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchiveExportJobOwnershipTest {

    @Test
    void sameUsernameCannotAccessTaskOwnedByDifferentUserId() {
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJob job = job(7L, "same-name");
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
        ArchiveExportJobService service = service(repository);

        AuthSession session = session(8L, "same-name");

        assertThatThrownBy(() -> service.requireOwned(session, "job-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void renamedUserCanStillAccessTaskByStableUserId() {
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJob job = job(7L, "old-name");
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
        ArchiveExportJobService service = service(repository);

        AuthSession session = session(7L, "new-name");

        assertThat(service.requireOwned(session, "job-1")).isSameAs(job);
    }

    @Test
    void legacyTaskWithoutUserIdTemporarilyFallsBackToUsername() {
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJob job = job(null, "legacy-user");
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
        ArchiveExportJobService service = service(repository);

        assertThat(service.requireOwned(session(9L, "legacy-user"), "job-1")).isSameAs(job);
    }

    private ArchiveExportJobService service(ArchiveExportJobRepository repository) {
        return new ArchiveExportJobService(
                mock(ArchiveExportService.class),
                repository,
                mock(ArchiveExportTempFileManager.class),
                new ArchiveExportProperties(),
                (Executor) Runnable::run);
    }

    private ArchiveExportJob job(Long ownerId, String username) {
        ArchiveExportJob job = new ArchiveExportJob();
        job.setId("job-1");
        job.setOwnerUserId(ownerId);
        job.setOwnerUsername(username);
        job.setFormat("ZIP");
        job.setStatus("SUCCESS");
        return job;
    }

    private AuthSession session(Long id, String username) {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setUsername(username);
        session.setRoleCode("USER");
        session.setPermissions(List.of("record:read", "record:download"));
        return session;
    }
}
