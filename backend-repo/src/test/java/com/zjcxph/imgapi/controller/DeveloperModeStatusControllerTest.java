package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.DeveloperApiAccessService;
import com.zjcxph.imgapi.service.DeveloperModeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeveloperModeStatusControllerTest {

    @Test
    void shouldExposeArchiveAndPermissionBypassAvailability() {
        DeveloperModeService archiveService = mock(DeveloperModeService.class);
        DeveloperApiAccessService apiService = mock(DeveloperApiAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/status/developer-mode");
        when(archiveService.isArchiveLegacyRequestAvailable(request)).thenReturn(true);
        when(apiService.isPermissionBypassAllowed(request)).thenReturn(true);
        DeveloperModeStatusController controller = new DeveloperModeStatusController(archiveService, apiService);

        var result = controller.status(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsOnlyKeys(
                "enabled", "accessMode", "apiPermissionBypassEnabled");
        assertThat(result.getData().get("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(result.getData().get("accessMode")).isEqualTo("ARCHIVE_LEGACY");
        assertThat(result.getData().get("apiPermissionBypassEnabled")).isEqualTo(Boolean.TRUE);
        assertThat(result.getData()).doesNotContainKeys("session", "permissions", "roleCode");
    }

    @Test
    void shouldReportDisabledWithoutExposingIdentity() {
        DeveloperModeService archiveService = mock(DeveloperModeService.class);
        DeveloperApiAccessService apiService = mock(DeveloperApiAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/status/developer-mode");
        DeveloperModeStatusController controller = new DeveloperModeStatusController(archiveService, apiService);

        var result = controller.status(request);

        assertThat(result.getData().get("enabled")).isEqualTo(Boolean.FALSE);
        assertThat(result.getData().get("accessMode")).isEqualTo("DISABLED");
        assertThat(result.getData().get("apiPermissionBypassEnabled")).isEqualTo(Boolean.FALSE);
    }
}
