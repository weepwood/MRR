package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.ArchiveSearchHistoryRequest;
import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import com.zjcxph.imgapi.mapper.ArchiveSearchHistoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArchiveSearchHistoryServiceTest {
    private final ArchiveSearchHistoryMapper mapper = mock(ArchiveSearchHistoryMapper.class);
    private final ArchiveSearchHistoryService service = new ArchiveSearchHistoryService(mapper);

    @Test void rejectsBlankRecordWithoutWriting() {
        var request = new ArchiveSearchHistoryRequest();
        request.setBah("  ");
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, request));
        verifyNoInteractions(mapper);
    }

    @Test void normalizesCountsAndUsesAuthenticatedOwner() {
        var request = new ArchiveSearchHistoryRequest();
        request.setBah("SYNTHETIC-ONLY");
        request.setSuccess(true);
        request.setImageCount(-1);
        request.setQueryCount(-2);
        var before = LocalDateTime.now();
        service.create(7L, request);
        var captured = ArgumentCaptor.forClass(ArchiveSearchHistory.class);
        verify(mapper).insert(captured.capture());
        var history = captured.getValue();
        assertNull(history.getId());
        assertEquals(7L, history.getUserId());
        assertEquals(0, history.getImageCount());
        assertEquals(1, history.getQueryCount());
        assertFalse(history.getSearchedAt().isBefore(before));
    }

    @Test void failedSearchHasZeroQueryCountAndPreservesExplicitTime() {
        var request = new ArchiveSearchHistoryRequest();
        request.setSjh("SYNTHETIC-ONLY");
        request.setQueryCount(999);
        request.setSearchedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        var result = service.create(7L, request);
        assertEquals(0, result.getQueryCount());
        assertEquals(request.getSearchedAt(), result.getSearchedAt());
    }

    @Test void favoritesAndListingAreScopedToAuthenticatedOwner() {
        when(mapper.findByUserId(7L)).thenReturn(List.of());
        assertTrue(service.list(7L).isEmpty());
        assertFalse(service.updateFavorite(7L, 9L, true));
        verify(mapper).updateFavorite(9L, 7L, true);
    }

    @Test void missingIdentityNeverReachesMapper() {
        assertThrows(IllegalStateException.class, () -> service.list(null));
        assertThrows(IllegalStateException.class, () -> service.updateFavorite(null, 9L, true));
        verifyNoInteractions(mapper);
    }
}
