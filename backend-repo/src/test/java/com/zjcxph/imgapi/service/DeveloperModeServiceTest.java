package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperModeServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @Test
    void shouldRemainDisabledWhenStartupSwitchIsOff() {
        DeveloperModeService service = createService(false, List.of("127.0.0.1", "::1"));

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldBeDisabledWhenSettingDoesNotExist() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY)).thenReturn(null);
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldRequireStartupAndDatabaseSwitches() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenReturn(new SystemSetting(DeveloperModeService.SETTING_KEY, "true", null));
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void shouldAllowExactClientIpConfiguredInSystemSettings() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("true");
        service.refreshAllowedSourcesFromValue("192.168.10.25");

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.10.25")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.10.26")))
                .isFalse();
    }

    @Test
    void shouldAllowClientIpInsideConfiguredCidr() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("true");
        service.refreshAllowedSourcesFromValue("192.168.20.0/24\n10.10.0.0/16");

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.20.88")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "10.10.15.9")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.21.1")))
                .isFalse();
    }

    @Test
    void shouldIgnoreForwardedHeadersFromUntrustedProxy() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("true");
        service.refreshAllowedSourcesFromValue("192.168.10.0/24");

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "10.0.0.8", "192.168.10.25")))
                .isFalse();
    }

    @Test
    void shouldRejectInvalidAllowedSourceRule() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        assertThatThrownBy(() -> service.validateAllowedSourcesValue("192.168.1.999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可信来源格式不正确");
        assertThatThrownBy(() -> service.validateAllowedSourcesValue("192.168.1.0/99"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可信来源格式不正确");
    }

    @Test
    void shouldFailClosedWhenStoredAllowedSourcesContainInvalidRule() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenReturn(new SystemSetting(DeveloperModeService.SETTING_KEY, "true", null));
        when(systemSettingMapper.findByKey(DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY))
                .thenReturn(new SystemSetting(
                        DeveloperModeService.ALLOWED_SOURCES_SETTING_KEY,
                        "192.168.10.0/24\n192.168.1.999",
                        null));
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.10.25")))
                .isFalse();
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldAllowOnlyAuditedReadOnlyArchivePaths() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("true");
        service.refreshAllowedSourcesFromValue("192.168.10.0/24");

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/search", "127.0.0.1", "192.168.10.25")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/image/123/1/folder/1.jpg", "127.0.0.1", "192.168.10.25")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/search/patient/1234567", "127.0.0.1", "192.168.10.25")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/search/patient/12345678", "127.0.0.1", "192.168.10.25")))
                .as("1000 万及以上病案必须携带上架号，不能匿名调用仅按病案号查询患者接口")
                .isFalse();

        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/12345678", "127.0.0.1", "192.168.10.25")))
                .as("直接按病案号接口不执行 userid/IP 绑定，必须拒绝匿名兼容访问")
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/settings", "127.0.0.1", "192.168.10.25")))
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("GET", "/api/v1/img/download/12345678", "127.0.0.1", "192.168.10.25")))
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(
                proxiedRequest("PUT", "/api/v1/img/updateImageType/1", "127.0.0.1", "192.168.10.25")))
                .isFalse();
    }

    @Test
    void shouldRefreshImmediatelyAfterSystemSettingSave() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        service.refreshFromValue("on");
        assertThat(service.isEnabled()).isTrue();

        service.refreshFromValue("false");
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldDisableImmediatelyWhenSettingIsDeleted() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("enabled");
        assertThat(service.isEnabled()).isTrue();

        service.disableImmediately();
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldFailClosedWhenDatabaseReadFails() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenThrow(new IllegalStateException("database unavailable"));
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));

        assertThat(service.isEnabled()).isFalse();
    }

    private DeveloperModeService createService(boolean startupAllowed, List<String> trustedProxyAddresses) {
        return new DeveloperModeService(systemSettingMapper, startupAllowed, trustedProxyAddresses);
    }

    private MockHttpServletRequest proxiedRequest(
            String method,
            String path,
            String proxyAddress,
            String clientAddress
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(proxyAddress);
        request.addHeader("X-Forwarded-For", clientAddress);
        request.addHeader("X-Real-IP", clientAddress);
        return request;
    }
}
