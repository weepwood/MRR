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
    void shouldExposeArchiveLegacyAvailabilityWithoutSensitiveConfiguration() {
        DeveloperModeService archiveService = mock(DeveloperModeService.class);
        DeveloperApiAccessService apiService = mock(DeveloperApiAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/status/developer-mode");
        when(archiveService.isArchiveLegacyRequestAvailable(request)).thenReturn(true);
        DeveloperModeStatusController controller = new DeveloperModeStatusController(archiveService, apiService);

        var result = controller.status(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsOnlyKeys("enabled", "accessMode");
        assertThat(result.getData().get("accessMode")).isEqualTo("ARCHIVE_LEGACY");
    }

    @Test
    void shouldExposeVirtualSessionOnlyForFullApiMode() {
        DeveloperModeService archiveService = mock(DeveloperModeService.class);
        DeveloperApiAccessService apiService = mock(DeveloperApiAccessService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/status/developer-mode");
        when(apiService.isRequestAllowed(request)).thenReturn(true);
        DeveloperModeStatusController controller = new DeveloperModeStatusController(archiveService, apiService);

        var result = controller.status(request);

        assertThat(result.getData().get("accessMode")).isEqualTo("API_FULL");
        @SuppressWarnings("unchecked")
        var session = (java.util.Map<String, Object>) result.getData().get("session");
        assertThat(session.get("roleCode")).isEqualTo("DEVELOPER_API");
        assertThat((java.util.List<String>) session.get("permissions"))
                .contains("record:manage", "system:manage", "user:manage");
    }
}
