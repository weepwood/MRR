package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.repository.ArchiveManifestRepository;
import com.zjcxph.imgapi.service.ArchiveManifestService;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
import com.zjcxph.imgapi.storage.ResolvedImageStorage;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ArchiveManifestServiceTest {
    final ArchiveManifestRepository repo = mock(ArchiveManifestRepository.class);
    final ImageContentMapper mapper = mock(ImageContentMapper.class);
    final ResolvedImageStorage storage = mock(ResolvedImageStorage.class);
    final DeploymentReadinessService readiness = mock(DeploymentReadinessService.class);
    final MaintenanceModeService maintenance = mock(MaintenanceModeService.class);
    final ArchiveManifestRepository.Lease lease = new ArchiveManifestRepository.Lease(1L, 9L, 50, null, 0, 0, UUID.randomUUID());
    final ArchiveManifestRepository.Candidate candidate = new ArchiveManifestRepository.Candidate(10, 1, "synthetic");

    @Test
    void disabledOrReadOnlyNeverClaimsOrCreates() {
        service(false).tick();
        assertThatThrownBy(() -> service(false).start(9L, 7L)).hasMessageContaining("关闭");
        when(readiness.isReadOnly()).thenReturn(true);
        service(true).tick();
        assertThatThrownBy(() -> service(true).start(9L, 7L)).hasMessageContaining("只读");
        verifyNoInteractions(repo, mapper, storage);
    }

    @Test
    void verifiesOnlyOneCandidateAndRetriesWithoutAdvancingOnBusy() {
        when(repo.claim(any())).thenReturn(Optional.of(lease));
        when(repo.next(lease)).thenReturn(Optional.of(candidate));
        Scan scan = new Scan();
        scan.setId(10);
        scan.setArchiveId(9L);
        when(mapper.findActiveById(10)).thenReturn(scan);
        when(storage.verifyContent(any(), any())).thenReturn(new ResolvedImageStorage.ContentVerification("BUSY", 0, ""));
        service(true).tick();
        verify(repo).retry(lease, false);
        verify(repo, never()).advance(any(), any(), any(), anyLong(), any());
        verify(mapper).findActiveById(10);
    }

    @Test
    void missingCandidateIsRecordedNotTreatedAsVerified() {
        when(repo.claim(any())).thenReturn(Optional.of(lease));
        when(repo.next(lease)).thenReturn(Optional.of(candidate));
        service(true).tick();
        verify(repo).advance(lease, candidate, "STALE_SCAN", 0L, "");
        verifyNoInteractions(storage);
    }

    @Test
    void limitNeverPublishesCompleteIfMoreCandidatesExist() {
        var bounded = new ArchiveManifestRepository.Lease(1L, 9L, 50, 1, 9, ArchiveManifestRepository.MAX_FILES, UUID.randomUUID());
        when(repo.claim(any())).thenReturn(Optional.of(bounded));
        when(repo.next(bounded)).thenReturn(Optional.of(candidate));
        service(true).tick();
        verify(repo).finish(bounded, "PARTIAL");
        verifyNoInteractions(mapper, storage);
    }

    @Test
    void missingFixedOrdinalConsumesFailureBudgetInsteadOfLoopingCompletion() {
        var fixed = new ArchiveManifestRepository.Lease(1L,9L,50,1,9,1,UUID.randomUUID(),2);
        when(repo.claim(any())).thenReturn(Optional.of(fixed));
        when(repo.next(fixed)).thenReturn(Optional.empty());
        service(true).tick();
        verify(repo).retry(fixed,true);
        verify(repo,never()).finish(any(),any());
        verifyNoInteractions(mapper,storage);
    }

    @Test
    void resumeRequiresEnabledWritableSystemAndValidIdentity() {
        assertThatThrownBy(() -> service(false).resume(9,1,7)).hasMessageContaining("关闭");
        when(readiness.isReadOnly()).thenReturn(true);
        assertThatThrownBy(() -> service(true).resume(9,1,7)).hasMessageContaining("只读");
        when(readiness.isReadOnly()).thenReturn(false);
        assertThatThrownBy(() -> service(true).resume(9,1,0)).hasMessageContaining("无效");
        verifyNoInteractions(repo);
    }

    private ArchiveManifestService service(boolean enabled) {
        return new ArchiveManifestService(repo, mapper, storage, readiness, maintenance, enabled);
    }
}
