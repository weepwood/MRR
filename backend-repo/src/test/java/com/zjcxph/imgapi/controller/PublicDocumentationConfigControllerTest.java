package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicDocumentationConfigControllerTest {

    @Test
    void shouldReturnConfiguredDocumentationUrlsOnly() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting(PublicDocumentationConfigController.USER_GUIDE_KEY))
                .thenReturn(" https://docs.example.test/user/ ");
        when(service.getSetting(PublicDocumentationConfigController.DEVELOPER_GUIDE_KEY))
                .thenReturn("/internal/development/");
        when(service.getSetting(PublicDocumentationConfigController.OPERATIONS_GUIDE_KEY))
                .thenReturn("http://192.168.1.20:8080/operations");
        when(service.getSetting("developerModeEnabled")).thenReturn("true");

        Map<String, String> data = new PublicDocumentationConfigController(service)
                .getDocumentationConfig().getData();

        assertThat(data).hasSize(3)
                .containsEntry(PublicDocumentationConfigController.USER_GUIDE_KEY,
                        "https://docs.example.test/user/")
                .containsEntry(PublicDocumentationConfigController.DEVELOPER_GUIDE_KEY,
                        "/internal/development/")
                .containsEntry(PublicDocumentationConfigController.OPERATIONS_GUIDE_KEY,
                        "http://192.168.1.20:8080/operations")
                .doesNotContainKey("developerModeEnabled");
    }

    @Test
    void shouldKeepAnExplicitlyClearedDocumentationLinkDisabled() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting(PublicDocumentationConfigController.USER_GUIDE_KEY)).thenReturn("  ");

        Map<String, String> data = new PublicDocumentationConfigController(service)
                .getDocumentationConfig().getData();

        assertThat(data.get(PublicDocumentationConfigController.USER_GUIDE_KEY)).isEmpty();
        assertThat(data.get(PublicDocumentationConfigController.DEVELOPER_GUIDE_KEY)).isEqualTo("/docs/internal/");
    }

    @Test
    void shouldFallBackWhenConfiguredUrlIsUnsafeOrInvalid() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting(PublicDocumentationConfigController.USER_GUIDE_KEY))
                .thenReturn("javascript:alert(1)");
        when(service.getSetting(PublicDocumentationConfigController.DEVELOPER_GUIDE_KEY))
                .thenReturn("//untrusted.example.test/docs");
        when(service.getSetting(PublicDocumentationConfigController.OPERATIONS_GUIDE_KEY))
                .thenReturn("https://user:password@example.test/docs");

        Map<String, String> data = new PublicDocumentationConfigController(service)
                .getDocumentationConfig().getData();

        assertThat(data.get(PublicDocumentationConfigController.USER_GUIDE_KEY)).isEqualTo("/docs/");
        assertThat(data.get(PublicDocumentationConfigController.DEVELOPER_GUIDE_KEY)).isEqualTo("/docs/internal/");
        assertThat(data.get(PublicDocumentationConfigController.OPERATIONS_GUIDE_KEY))
                .isEqualTo("/docs/internal/deployment.html");
    }

    @Test
    void shouldAcceptHttpHttpsAndRootRelativeUrls() {
        assertThat(PublicDocumentationConfigController.isAllowedUrl("/docs/")).isTrue();
        assertThat(PublicDocumentationConfigController.isAllowedUrl("http://127.0.0.1:8002/docs")).isTrue();
        assertThat(PublicDocumentationConfigController.isAllowedUrl("https://docs.example.test/guide")).isTrue();
        assertThat(PublicDocumentationConfigController.isAllowedUrl("javascript:alert(1)")).isFalse();
        assertThat(PublicDocumentationConfigController.isAllowedUrl("//docs.example.test/guide")).isFalse();
        assertThat(PublicDocumentationConfigController.isAllowedUrl("/docs\\internal")).isFalse();
    }
}
