package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.MaintenanceModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceModeServiceTest {

    @Test
    void loadsPersistedMaintenanceStateAtStartup() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.getSetting("operationsMaintenanceEnabled")).thenReturn("true");
        when(settings.getSetting("operationsMaintenanceReason")).thenReturn("数据库维护");
        when(settings.getSetting("operationsMaintenanceUpdatedAt")).thenReturn("2026-07-23T00:00:00Z");
        when(settings.getSetting("operationsMaintenanceUpdatedBy")).thenReturn("admin");

        MaintenanceModeService service = new MaintenanceModeService(settings);
        service.initialize();

        assertTrue(service.isEnabled());
        assertEquals("数据库维护", service.getStatus().get("reason"));
        assertEquals("admin", service.getStatus().get("updatedBy"));
    }

    @Test
    void enableAndDisablePersistAnInMemorySnapshot() {
        SystemSettingService settings = mock(SystemSettingService.class);
        MaintenanceModeService service = new MaintenanceModeService(settings);

        Map<String, Object> enabled = service.enable("版本升级", "operator");
        assertTrue(service.isEnabled());
        assertEquals("版本升级", enabled.get("reason"));
        verify(settings).saveSettings(
                org.mockito.ArgumentMatchers.argThat(value -> "true".equals(value.get("operationsMaintenanceEnabled"))),
                eq("operator")
        );

        Map<String, Object> disabled = service.disable("operator");
        assertFalse(service.isEnabled());
        assertEquals(false, disabled.get("enabled"));
        verify(settings).saveSettings(
                org.mockito.ArgumentMatchers.argThat(value -> "false".equals(value.get("operationsMaintenanceEnabled"))),
                eq("operator")
        );
    }
}
