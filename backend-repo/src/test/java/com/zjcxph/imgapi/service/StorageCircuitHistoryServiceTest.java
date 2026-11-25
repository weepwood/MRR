package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.repository.StorageCircuitHistoryRepository;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageCircuitHistoryServiceTest {
    @Test void disabledDoesNotCollectOrQuery() {
        var center=mock(OperationsCenterService.class);
        var repository=mock(StorageCircuitHistoryRepository.class);
        var service=new StorageCircuitHistoryService(center,repository,false);
        service.collect();
        assertThat(service.history(24).enabled()).isFalse();
        assertThatThrownBy(()->service.history(48)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(center,repository);
    }

    @Test void collectorOnlyPersistsAllowedActualEventsAndBoundsNewest() {
        Instant now=Instant.parse("2026-01-08T00:00:00Z");
        var circuit=UUID.randomUUID();
        var buffer=new ArrayList<Map<String,Object>>();
        for(int i=1;i<=260;i++) buffer.add(Map.of("sequence",i,"type","OPENED","at",now.minusSeconds(261-i).toString()));
        buffer.add(Map.of("sequence",261,"type","RECOVERED","at",now.plusSeconds(1).toString()));
        buffer.add(Map.of("sequence",262,"type","secret raw message","at",now.toString()));
        buffer.add(Map.of("sequence",263,"type","OPENED","at","invalid"));
        buffer.add(Map.of("sequence",264,"type","OPENED","at",now.minusSeconds(700000).toString()));
        Map<String,Object> node=Map.of("ordinal",1,"history",Map.of("circuitId",circuit.toString(),"events",buffer));
        var events=StorageCircuitHistoryService.events(Map.of("NAS",Map.of("nodes",List.of(node)),"INVALID",Map.of("nodes",List.of(node))),now,now.minusSeconds(604800));
        assertThat(events).hasSize(256);
        assertThat(events.getFirst().sequence()).isEqualTo(5);
        assertThat(events.getLast().sequence()).isEqualTo(260);
        assertThat(events).allSatisfy(e->{assertThat(e.sourceKind()).isEqualTo("NAS");assertThat(e.eventType()).isEqualTo("OPENED");});
    }

    @Test void collectionIsIndependentOfReadinessAndHistoryReportsTruncation() {
        var center=mock(OperationsCenterService.class);
        var repo=mock(StorageCircuitHistoryRepository.class);
        var service=new StorageCircuitHistoryService(center,repo,true);
        var now=Instant.now();
        var circuit=UUID.randomUUID();
        when(center.storageCircuitSnapshots()).thenReturn(Map.of("OSS",Map.of("nodes",List.of(Map.of("ordinal",1,
                "history",Map.of("circuitId",circuit.toString(),"events",List.of(Map.of("sequence",1,"type","RECOVERED","at",now.minusSeconds(2).toString()))))))));
        service.collect();
        verify(center).storageCircuitSnapshots();verifyNoMoreInteractions(center);
        verify(repo).record(argThat(rows->rows.size()==1 && rows.getFirst().sequence()==1));
        verify(repo).prune(any());
        var events=java.util.stream.IntStream.rangeClosed(1,101).mapToObj(i->new StorageCircuitHistoryRepository.Event(circuit,i,"OSS",1,"OPENED",now,now)).toList();
        when(repo.read(any(),any())).thenReturn(events);
        var result=service.history(168);
        assertThat(result.truncated()).isTrue();assertThat(result.events()).hasSize(100);
    }
}
