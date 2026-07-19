package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicLoginPageConfigControllerTest {

    @Test
    void shouldReturnOnlyWhitelistedBrandingAndVisibleSupportInformation() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting("systemName")).thenReturn("医院病案影像平台");
        when(service.getSetting("systemAdminContactEnabled")).thenReturn("true");
        when(service.getSetting("systemAdminPublicVisible")).thenReturn("true");
        when(service.getSetting("systemAdminPhone")).thenReturn("0571-12345678");
        when(service.getSetting("developerModeEnabled")).thenReturn("true");

        PublicLoginPageConfigController controller = new PublicLoginPageConfigController(service);
        Result<Map<String, String>> result = controller.getLoginPageConfig();

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().get("systemName")).isEqualTo("医院病案影像平台");
        assertThat(result.getData().get("systemShortName")).isEqualTo("MRR");
        assertThat(result.getData().get("systemAdminContactVisible")).isEqualTo("true");
        assertThat(result.getData().get("systemAdminPhone")).isEqualTo("0571-12345678");
        assertThat(result.getData()).doesNotContainKey("developerModeEnabled");
        assertThat(result.getData()).doesNotContainKey("imageSource");
    }

    @Test
    void shouldNotExposeContactDetailsWhenPublicVisibilityIsDisabled() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting("systemAdminContactEnabled")).thenReturn("true");
        when(service.getSetting("systemAdminPublicVisible")).thenReturn("false");
        when(service.getSetting("systemAdminPhone")).thenReturn("0571-12345678");

        Map<String, String> data = new PublicLoginPageConfigController(service).getLoginPageConfig().getData();

        assertThat(data.get("systemAdminContactVisible")).isEqualTo("false");
        assertThat(data).doesNotContainKey("systemAdminPhone");
        assertThat(data).doesNotContainKey("systemAdminEmail");
    }

    @Test
    void shouldTrimAndLimitPublicTextLength() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting("loginHelpText")).thenReturn("  " + "a".repeat(300) + "  ");

        String value = new PublicLoginPageConfigController(service)
                .getLoginPageConfig().getData().get("loginHelpText");

        assertThat(value).hasSize(240);
    }
}
