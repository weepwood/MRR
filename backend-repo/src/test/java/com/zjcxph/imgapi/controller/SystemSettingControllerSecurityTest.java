package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.DeveloperModeService;
import com.zjcxph.imgapi.service.SystemSettingService;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SystemSettingControllerSecurityTest {

    /**
     * 构造被测控制器。注册表与运行参数读取器使用真实对象，
     * 运行参数读取器的 Mapper 为 mock（未配置即无覆盖值），
     * 因此这些用例只覆盖控制器分支，不依赖数据库。
     */
    private SystemSettingController controller(SystemSettingService service) {
        SettingRegistry registry = new SettingRegistry(new LogRetentionProperties());
        RuntimeSettingStore store = new RuntimeSettingStore(mock(SystemSettingMapper.class), registry);
        return new SystemSettingController(service, registry, store);
    }

    @Test
    void bulkSaveShouldPersistDeveloperModeWithOtherManagedSettings() {
        SystemSettingService service = mock(SystemSettingService.class);
        SystemSettingController controller = controller(service);
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
        SystemSettingController controller = controller(service);

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
        SystemSettingController controller = controller(service);

        var result = controller.deleteSetting(DeveloperModeService.SETTING_KEY);

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).deleteSetting(DeveloperModeService.SETTING_KEY);
    }
}
