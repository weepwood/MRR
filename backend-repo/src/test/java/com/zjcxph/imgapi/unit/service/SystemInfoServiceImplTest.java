package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.impl.SystemInfoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemInfoServiceImpl 系统信息服务测试")
class SystemInfoServiceImplTest {

    // SystemInfoServiceImpl 的无参构造器使用 @Value 字段注入，
    // 在单元测试中（无 Spring 容器）会得到默认值，仅验证返回结构
    private final SystemInfoServiceImpl service = new SystemInfoServiceImpl();

    @Test
    @DisplayName("getSystemInfo — 返回 application/jvm/operatingSystem 三段结构")
    void getSystemInfo_hasExpectedSections() {
        Map<String, Object> info = service.getSystemInfo();

        assertThat(info).containsKeys("application", "jvm", "operatingSystem");

        @SuppressWarnings("unchecked")
        Map<String, String> app = (Map<String, String>) info.get("application");
        assertThat(app).containsKeys("name", "startTime", "runTime");
    }

    @Test
    @DisplayName("getMemoryInfo — 返回 heap/nonHeap/usagePercent")
    void getMemoryInfo_hasExpectedSections() {
        Map<String, Object> mem = service.getMemoryInfo();

        assertThat(mem).containsKeys("heap", "nonHeap", "usagePercent");
        assertThat((String) mem.get("usagePercent")).endsWith("%");
    }

    @Test
    @DisplayName("getRuntimeInfo — 返回运行持续时间")
    void getRuntimeInfo_hasUptime() {
        Map<String, Object> runtime = service.getRuntimeInfo();

        assertThat(runtime).containsKeys("uptimeFormatted", "startTime", "uptimeMillis");
    }

    @Test
    @DisplayName("getHealth — 状态为 UP")
    void getHealth_statusIsUp() {
        Map<String, Object> health = service.getHealth();

        assertThat(health).containsEntry("status", "UP");
        assertThat(health).containsKey("components");
    }

    @Test
    @DisplayName("getGcStats — 包含 totalCollections")
    void getGcStats_hasTotals() {
        Map<String, Object> gc = service.getGcStats();

        assertThat(gc).containsKeys("totalCollections", "totalTimeMs");
        assertThat((Long) gc.get("totalCollections")).isGreaterThanOrEqualTo(0);
    }
}
