package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperModeServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    @Test
    void shouldRemainDisabledWhenStartupSwitchIsOff() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenReturn(new SystemSetting(DeveloperModeService.SETTING_KEY, "true", null));
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
    void shouldAllowOnlyReadOnlyArchivePathsFromTrustedProxy() {
        DeveloperModeService service = createService(true, List.of("127.0.0.1", "::1"));
        service.refreshFromValue("true");

        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/search", "127.0.0.1")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/12345678", "127.0.0.1")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/image/123/1/folder/1.jpg", "127.0.0.1")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/url/1", "127.0.0.1")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/oss-image/1", "127.0.0.1")))
                .isTrue();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/search/patient/12345678", "127.0.0.1")))
                .isTrue();

        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/settings", "127.0.0.1")))
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/download/12345678", "127.0.0.1")))
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(request("PUT", "/api/v1/img/updateImageType/1", "127.0.0.1")))
                .isFalse();
        assertThat(service.isArchiveLegacyRequestAllowed(request("GET", "/api/v1/img/search", "10.10.20.15")))
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

    private DeveloperModeService createService(boolean startupAllowed, List<String> allowedRemoteAddresses) {
        return new DeveloperModeService(systemSettingMapper, startupAllowed, allowedRemoteAddresses);
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
