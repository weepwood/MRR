package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperApiAccessServiceTest {

    @Mock private SystemSettingMapper systemSettingMapper;
    @Mock private DeveloperModeService developerModeService;

    @Test
    void shouldRequireBothDeveloperModeAndApiSwitch() {
        DeveloperApiAccessService service = new DeveloperApiAccessService(systemSettingMapper, developerModeService);
        service.refreshFromValue("true");
        when(developerModeService.isEnabled()).thenReturn(false);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldAllowOnlyNoTokenApiRequestFromTrustedDeveloperSource() {
        DeveloperApiAccessService service = new DeveloperApiAccessService(systemSettingMapper, developerModeService);
        service.refreshFromValue("true");
        when(developerModeService.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/oss/migration/jobs");
        when(developerModeService.isArchiveLegacyRequestAvailable(request)).thenReturn(true);
        assertThat(service.isRequestAllowed(request)).isTrue();
    }

    @Test
    void shouldNeverAcceptRequestThatCarriesAuthorization() {
        DeveloperApiAccessService service = new DeveloperApiAccessService(systemSettingMapper, developerModeService);
        service.refreshFromValue("true");
        when(developerModeService.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/settings");
        request.addHeader("Authorization", "Bearer invalid-token");
        assertThat(service.isRequestAllowed(request)).isFalse();
        verify(developerModeService, never()).isArchiveLegacyRequestAvailable(request);
    }

    @Test
    void shouldFailClosedWhenApiSettingCannotBeRead() {
        when(systemSettingMapper.findByKey(DeveloperApiAccessService.SETTING_KEY))
                .thenThrow(new IllegalStateException("database unavailable"));
        DeveloperApiAccessService service = new DeveloperApiAccessService(systemSettingMapper, developerModeService);
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void shouldLoadEnabledSettingFromDatabase() {
        when(systemSettingMapper.findByKey(DeveloperApiAccessService.SETTING_KEY))
                .thenReturn(new SystemSetting(DeveloperApiAccessService.SETTING_KEY, "true", null));
        when(developerModeService.isEnabled()).thenReturn(true);
        DeveloperApiAccessService service = new DeveloperApiAccessService(systemSettingMapper, developerModeService);
        assertThat(service.isEnabled()).isTrue();
    }
}
