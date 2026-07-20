package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemSettingControllerSecurityTest {

    @Test
    void bulkSaveShouldPersistDeveloperModeWithOtherManagedSettings() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);
        Map<String, String> settings = Map.of(
                DeveloperModeService.SETTING_KEY, "true",
                "systemName", "MRR"
        );

        var result = controller.saveSettings(settings);

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).saveSettings(settings, null);
    }

    @Test
    void singleSettingMutationShouldAllowDeveloperModeKey() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);

        var result = controller.setSetting(
                DeveloperModeService.SETTING_KEY,
                Map.of("value", "true")
        );

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).setSetting(DeveloperModeService.SETTING_KEY, "true", null);
    }

    @Test
    void deleteShouldDisableDeveloperModeThroughSettingService() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);

        var result = controller.deleteSetting(DeveloperModeService.SETTING_KEY);

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).deleteSetting(DeveloperModeService.SETTING_KEY);
    }
}
