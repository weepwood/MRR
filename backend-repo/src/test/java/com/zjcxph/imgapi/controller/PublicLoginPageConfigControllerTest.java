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
    void shouldReturnOnlyWhitelistedLoginCopyWithDefaults() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting("loginBrandTitle")).thenReturn("医院病案影像平台");
        when(service.getSetting("developerModeEnabled")).thenReturn("true");

        PublicLoginPageConfigController controller = new PublicLoginPageConfigController(service);
        Result<Map<String, String>> result = controller.getLoginPageConfig();

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().get("loginBrandTitle")).isEqualTo("医院病案影像平台");
        assertThat(result.getData().get("loginFormTitle")).isEqualTo("登录 MRR");
        assertThat(result.getData()).doesNotContainKey("developerModeEnabled");
        assertThat(result.getData()).doesNotContainKey("imageSource");
    }

    @Test
    void shouldTrimAndLimitPublicTextLength() {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getSetting("loginHelpText")).thenReturn("  " + "a".repeat(300) + "  ");

        PublicLoginPageConfigController controller = new PublicLoginPageConfigController(service);
        String value = controller.getLoginPageConfig().getData().get("loginHelpText");

        assertThat(value).hasSize(240);
    }
}
