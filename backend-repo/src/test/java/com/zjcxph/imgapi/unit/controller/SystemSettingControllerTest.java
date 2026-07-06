package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.SystemSettingController;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingController 控制器测试")
class SystemSettingControllerTest {

    @Mock
    private SystemSettingService systemSettingService;

    private SystemSettingController systemSettingController;

    @BeforeEach
    void setUp() {
        systemSettingController = new SystemSettingController(systemSettingService);
    }

    @Test
    @DisplayName("GET /api/v1/settings — 返回全部设置")
    void getAllSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("systemName", "MRR");
        settings.put("logLevel", "info");
        when(systemSettingService.getAllSettings()).thenReturn(settings);

        Result<Map<String, String>> result = systemSettingController.getAllSettings();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("systemName", "MRR");
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 批量保存成功")
    void saveSettings() {
        Map<String, String> body = Map.of("systemName", "MRR-Prod");
        doNothing().when(systemSettingService).saveSettings(anyMap(), eq(null));

        Result<Void> result = systemSettingController.saveSettings(body);

        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 空 body 返回 400")
    void saveSettings_emptyBody() {
        Map<String, String> emptyBody = new LinkedHashMap<>();

        Result<Void> result = systemSettingController.saveSettings(emptyBody);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 存在时返回 200")
    void getSetting_found() {
        when(systemSettingService.getSetting("logLevel")).thenReturn("info");

        Result<String> result = systemSettingController.getSetting("logLevel");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isEqualTo("info");
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 不存在返回 404")
    void getSetting_notFound() {
        when(systemSettingService.getSetting("unknown")).thenReturn(null);

        Result<String> result = systemSettingController.getSetting("unknown");

        assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("DELETE /api/v1/settings/{key} — 删除成功")
    void deleteSetting() {
        doNothing().when(systemSettingService).deleteSetting("key");

        Result<Void> result = systemSettingController.deleteSetting("key");

        assertThat(result.getCode()).isEqualTo(200);
    }
}
