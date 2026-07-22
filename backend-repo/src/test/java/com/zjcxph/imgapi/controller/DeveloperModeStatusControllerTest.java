package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.DeveloperModeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeveloperModeStatusControllerTest {

    @Test
    void shouldExposeArchiveLegacyAvailabilityWithoutSensitiveConfiguration() {
        DeveloperModeService service = mock(DeveloperModeService.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/public/status/developer-mode");
        request.setRemoteAddr("127.0.0.1");
        when(service.isArchiveLegacyRequestAvailable(request)).thenReturn(true);
        DeveloperModeStatusController controller = new DeveloperModeStatusController(service);

        var result = controller.status(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsOnlyKeys("enabled", "accessMode");
        assertThat(result.getData().get("enabled")).isEqualTo(Boolean.TRUE);
        assertThat(result.getData().get("accessMode")).isEqualTo("ARCHIVE_LEGACY");
    }
}
