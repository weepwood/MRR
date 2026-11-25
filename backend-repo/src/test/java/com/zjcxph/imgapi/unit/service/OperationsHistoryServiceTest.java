package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.OperationsHistoryRepository;
import com.zjcxph.imgapi.service.OperationsCenterService;
import com.zjcxph.imgapi.service.OperationsHistoryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsHistoryServiceTest {
    @Test void disabledDoesNotReadOrWriteOrProbe() {
        var center = mock(OperationsCenterService.class);
        var repo = mock(OperationsHistoryRepository.class);
        var service = new OperationsHistoryService(center,repo,false);
        service.sample();
        assertThat(service.history(24).enabled()).isFalse();
        assertThat(service.history(168).samples()).isEmpty();
        assertThatThrownBy(() -> service.history(999)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(center,repo);
    }

    @Test void storesOnlyAvailableObservationsAndLimitsReadRange() {
        var center = mock(OperationsCenterService.class);
        var repo = mock(OperationsHistoryRepository.class);
        var service = new OperationsHistoryService(center,repo,true);
        service.sample();
        verifyNoInteractions(repo);
        var at = Instant.now();
        var sample = new OperationsHistoryRepository.Sample(at,at,"READ_WRITE",0,1,2,null,null,null);
        when(center.historySample(any())).thenReturn(sample);
        service.sample();
        verify(repo).record(sample);
        verify(repo).prune(any());
        when(repo.read(any(),any())).thenReturn(List.of(sample));
        var result = service.history(24);
        assertThat(result.samples()).containsExactly(sample);
        assertThat(java.time.Duration.between(result.from(),result.to()).toHours()).isEqualTo(24);
        assertThat(result.bucketSeconds()).isEqualTo(300);
    }
}
