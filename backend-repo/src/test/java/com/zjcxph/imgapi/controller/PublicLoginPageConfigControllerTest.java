package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicLoginPageConfigControllerTest {

    private static SystemSettingService serviceWith(Map<String, String> settings) {
        SystemSettingService service = mock(SystemSettingService.class);
        when(service.getAllSettings()).thenReturn(new LinkedHashMap<>(settings));
        return service;
    }

    @Test
    void shouldReturnOnlyWhitelistedBrandingAndVisibleSupportInformation() {
        SystemSettingService service = serviceWith(Map.of(
                "systemName", "医院病案影像平台",
                "systemAdminContactEnabled", "true",
                "systemAdminPublicVisible", "true",
                "systemAdminPhone", "0571-12345678",
                "developerModeEnabled", "true"
        ));

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
        SystemSettingService service = serviceWith(Map.of(
                "systemAdminContactEnabled", "true",
                "systemAdminPublicVisible", "false",
                "systemAdminPhone", "0571-12345678"
        ));

        Map<String, String> data = new PublicLoginPageConfigController(service).getLoginPageConfig().getData();

        assertThat(data.get("systemAdminContactVisible")).isEqualTo("false");
        assertThat(data).doesNotContainKey("systemAdminPhone");
        assertThat(data).doesNotContainKey("systemAdminEmail");
    }

    @Test
    void shouldTrimAndLimitPublicTextLength() {
        SystemSettingService service = serviceWith(Map.of(
                "loginHelpText", "  " + "a".repeat(300) + "  "
        ));

        String value = new PublicLoginPageConfigController(service)
                .getLoginPageConfig().getData().get("loginHelpText");

        assertThat(value).hasSize(240);
    }

    /**
     * 这个端点是匿名的，也不在限流覆盖范围内。逐个键调用 {@code getSetting} 会让
     * 一次登录页加载打出二十多次库查询，等于给未认证访问者一个放大倍数很高的
     * 打点。整表读一次即可，配置总量是几十行的量级。
     */
    @Test
    void shouldReadSettingsTableOncePerRequest() {
        SystemSettingService service = serviceWith(Map.of("systemName", "医院病案影像平台"));

        new PublicLoginPageConfigController(service).getLoginPageConfig();

        verify(service, times(1)).getAllSettings();
        verify(service, never()).getSetting(anyString());
    }

    @Test
    void missingKeyFallsBackToDefaultWithoutExtraQueries() {
        SystemSettingService service = serviceWith(Map.of());

        Map<String, String> data = new PublicLoginPageConfigController(service).getLoginPageConfig().getData();

        assertThat(data.get("systemName")).isEqualTo("MRR 病案文件管理系统");
        assertThat(data.get("loginFeatureEnabled")).isEqualTo("true");
        assertThat(data.get("systemAdminContactVisible")).isEqualTo("false");
        verify(service, never()).getSetting(anyString());
    }
}
