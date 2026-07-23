package com.zjcxph.imgapi.unit.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.interceptors.ReadOnlyDegradationInterceptor;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadOnlyDegradationInterceptorTest {

    private DeploymentReadinessService readinessService;
    private ReadOnlyDegradationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        readinessService = mock(DeploymentReadinessService.class);
        interceptor = new ReadOnlyDegradationInterceptor(readinessService, new ObjectMapper());
    }

    @Test
    void getRequestRemainsAvailableInReadOnlyMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void searchPostRemainsAvailableInReadOnlyMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/search/archive-cases");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void externalArchiveTicketRemainsAvailableInReadOnlyMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/integration/archive/tickets"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void businessWriteIsRejectedInReadOnlyMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(true);
        when(readinessService.getSnapshot()).thenReturn(Map.of(
                "mode", "READ_ONLY_DEGRADED",
                "checkedAt", "2026-07-23T00:00:00Z"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/patients/import");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("READ_ONLY_DEGRADED"));
    }

    @Test
    void writeRemainsAvailableWhenSystemIsHealthy() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/img/updateImageType/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
