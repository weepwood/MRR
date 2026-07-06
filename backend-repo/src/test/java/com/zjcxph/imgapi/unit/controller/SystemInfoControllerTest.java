package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.SystemInfoController;
import com.zjcxph.imgapi.service.SystemInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemInfoController 控制器测试")
class SystemInfoControllerTest {

    @Mock
    private SystemInfoService systemInfoService;

    @InjectMocks
    private SystemInfoController systemInfoController;

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("GET /api/v1/system/info — 返回系统信息")
    void getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", Map.of("name", "imgapi"));
        info.put("jvm", Map.of("javaVersion", "21"));
        info.put("operatingSystem", Map.of("name", "Windows"));
        when(systemInfoService.getSystemInfo()).thenReturn(info);

        Result<Map<String, Object>> result = systemInfoController.getSystemInfo();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsKey("application");
        Object appObj = result.getData().get("application");
        assertThat(appObj).isInstanceOf(Map.class);
        Map<String, Object> app = (Map<String, Object>) appObj;
        assertThat(app).containsEntry("name", "imgapi");
    }

    @Test
    @DisplayName("GET /api/v1/system/memory — 返回内存信息")
    void getMemoryInfo() {
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("heap", Map.of("used", "128MB"));
        mem.put("usagePercent", "45.00%");
        when(systemInfoService.getMemoryInfo()).thenReturn(mem);

        Result<Map<String, Object>> result = systemInfoController.getMemoryInfo();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("usagePercent", "45.00%");
    }

    @Test
    @DisplayName("GET /api/v1/system/runtime — 返回运行时信息")
    void getRuntimeInfo() {
        when(systemInfoService.getRuntimeInfo()).thenReturn(Map.of("uptimeFormatted", "2小时"));

        Result<Map<String, Object>> result = systemInfoController.getRuntimeInfo();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("uptimeFormatted", "2小时");
    }

    @Test
    @DisplayName("GET /api/v1/system/health — 返回健康检查")
    void healthCheck() {
        when(systemInfoService.getHealth()).thenReturn(Map.of("status", "UP"));

        Result<Map<String, Object>> result = systemInfoController.healthCheck();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("status", "UP");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("GET /api/v1/system/overview — 返回综合概览")
    void getOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("info", Map.of());
        overview.put("memory", Map.of());
        overview.put("runtime", Map.of());
        overview.put("health", Map.of("status", "UP"));
        when(systemInfoService.getOverview()).thenReturn(overview);

        Result<Map<String, Object>> result = systemInfoController.getOverview();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
        Map<String, Object> health = (Map<String, Object>) result.getData().get("health");
        assertThat(health).containsEntry("status", "UP");
    }
}
