package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.req.ArchiveSearchHistoryRequest;
import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import com.zjcxph.imgapi.mapper.ArchiveSearchHistoryMapper;
import com.zjcxph.imgapi.service.ArchiveSearchHistoryService;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveSearchHistoryControllerTest {
    private final ArchiveSearchHistoryMapper mapper = mock(ArchiveSearchHistoryMapper.class);
    private final ArchiveSearchHistoryController controller = new ArchiveSearchHistoryController(new ArchiveSearchHistoryService(mapper));

    @AfterEach void clearIdentity() { AuthContext.clear(); }

    @Test void legacyClientIdentityFieldsCannotReplaceServerIdentity() {
        var session = new AuthSession();
        session.setId(7L);
        AuthContext.setCurrentUser(session);
        var request = JsonMapper.builder().build().readValue("""
                {"id":999,"userId":999,"bah":"SYNTHETIC-ONLY","success":true,"createdAt":"2026-01-01T00:00:00"}
                """, ArchiveSearchHistoryRequest.class);
        doAnswer(invocation -> {
            ArchiveSearchHistory history = invocation.getArgument(0);
            assertNull(history.getId());
            assertEquals(7L, history.getUserId());
            history.setId(123L);
            return 1;
        }).when(mapper).insert(any());
        var result = controller.create(request);
        assertEquals(200, result.getCode());
        assertEquals(123L, result.getData().id());
        assertEquals(7L, result.getData().userId());
        assertEquals("SYNTHETIC-ONLY", result.getData().bah());
    }

    @Test void unauthenticatedAccessNeverReachesMapper() {
        assertThrows(IllegalStateException.class, controller::list);
        verifyNoInteractions(mapper);
    }
}
