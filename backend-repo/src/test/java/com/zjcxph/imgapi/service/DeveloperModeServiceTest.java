package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

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
    void shouldAlwaysBeDisabledWithoutReadingDatabase() {
        assertThat(developerModeService.isEnabled()).isFalse();
        verifyNoInteractions(systemSettingMapper);
    }

    @Test
    void shouldIgnoreLegacyEnableValues() {
        developerModeService.refreshFromValue("true");
        developerModeService.refreshFromValue("on");
        developerModeService.refreshFromValue("enabled");

        assertThat(developerModeService.isEnabled()).isFalse();
        verifyNoInteractions(systemSettingMapper);
    }

    @Test
    void shouldRemainDisabledAfterRefreshInvalidateAndDisable() {
        developerModeService.refreshFromValue("false");
        developerModeService.invalidate();
        developerModeService.disableImmediately();

        assertThat(developerModeService.isEnabled()).isFalse();
        verifyNoInteractions(systemSettingMapper);
    }
}
