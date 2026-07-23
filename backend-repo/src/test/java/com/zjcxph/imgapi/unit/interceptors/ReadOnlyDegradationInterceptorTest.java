package com.zjcxph.imgapi.unit.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.interceptors.ReadOnlyDegradationInterceptor;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.MaintenanceModeService;
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
    private MaintenanceModeService maintenanceModeService;
    private ReadOnlyDegradationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        readinessService = mock(DeploymentReadinessService.class);
        maintenanceModeService = mock(MaintenanceModeService.class);
        when(maintenanceModeService.getStatus()).thenReturn(Map.of(
                "enabled", false,
                "reason", "",
                "updatedAt", "",
                "updatedBy", ""
        ));
        interceptor = new ReadOnlyDegradationInterceptor(
                readinessService,
                maintenanceModeService,
                new ObjectMapper()
        );
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
    void readinessRefreshRemainsAvailableInReadOnlyMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/operations/readiness/refresh"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void maintenanceDisableRemainsAvailableDuringMaintenance() throws Exception {
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/operations/maintenance/disable"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void businessWriteIsRejectedInAutomaticReadOnlyMode() throws Exception {
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
    void businessWriteIsRejectedInMaintenanceMode() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(false);
        when(readinessService.getSnapshot()).thenReturn(Map.of("checkedAt", "2026-07-23T00:00:00Z"));
        when(maintenanceModeService.isEnabled()).thenReturn(true);
        when(maintenanceModeService.getStatus()).thenReturn(Map.of(
                "enabled", true,
                "reason", "数据库维护",
                "updatedAt", "2026-07-23T00:00:00Z",
                "updatedBy", "admin"
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/patients/import");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("MAINTENANCE_MODE"));
        assertTrue(response.getContentAsString().contains("数据库维护"));
    }

    @Test
    void writeRemainsAvailableWhenSystemIsHealthy() throws Exception {
        when(readinessService.isReadOnly()).thenReturn(false);
        when(maintenanceModeService.isEnabled()).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/img/updateImageType/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
