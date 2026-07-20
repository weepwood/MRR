package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SystemSettingControllerSecurityTest {

    @Test
    void bulkSaveShouldIgnoreLegacyDeveloperModeKeyAndPersistOtherSettings() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);

        var result = controller.saveSettings(Map.of(
                DeveloperModeService.SETTING_KEY, "true",
                "systemName", "MRR"
        ));

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).saveSettings(argThat(settings ->
                settings.size() == 1
                        && "MRR".equals(settings.get("systemName"))
                        && !settings.containsKey(DeveloperModeService.SETTING_KEY)), isNull());
    }

    @Test
    void singleSettingMutationShouldRejectLegacyDeveloperModeKey() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);

        var result = controller.setSetting(
                DeveloperModeService.SETTING_KEY,
                Map.of("value", "true")
        );

        assertThat(result.getCode()).isEqualTo(400);
        verify(service, never()).setSetting(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteShouldRejectLegacyDeveloperModeKey() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = new SystemSettingController(service);

        var result = controller.deleteSetting(DeveloperModeService.SETTING_KEY);

        assertThat(result.getCode()).isEqualTo(400);
        verify(service, never()).deleteSetting(org.mockito.ArgumentMatchers.anyString());
    }
}
