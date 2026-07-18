package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperModeServiceTest {

    @Mock
    private SystemSettingMapper systemSettingMapper;

    private DeveloperModeService developerModeService;

    @BeforeEach
    void setUp() {
        developerModeService = new DeveloperModeService(systemSettingMapper);
    }

    @Test
    void shouldBeDisabledWhenSettingDoesNotExist() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY)).thenReturn(null);

        assertThat(developerModeService.isEnabled()).isFalse();
    }

    @Test
    void shouldReadEnabledValueFromDatabase() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenReturn(new SystemSetting(DeveloperModeService.SETTING_KEY, "true", null));

        assertThat(developerModeService.isEnabled()).isTrue();
    }

    @Test
    void shouldRefreshImmediatelyAfterSystemSettingSave() {
        developerModeService.refreshFromValue("on");
        assertThat(developerModeService.isEnabled()).isTrue();

        developerModeService.refreshFromValue("false");
        assertThat(developerModeService.isEnabled()).isFalse();
    }

    @Test
    void shouldFailClosedWhenDatabaseReadFails() {
        when(systemSettingMapper.findByKey(DeveloperModeService.SETTING_KEY))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(developerModeService.isEnabled()).isFalse();
    }
}
