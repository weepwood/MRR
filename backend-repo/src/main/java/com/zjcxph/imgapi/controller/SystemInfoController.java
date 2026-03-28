package com.zjcxph.imgapi.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping("/v1/system")
@Tag(name = "System Management", description = "系统管理接口")
public class SystemInfoController {

    private static final Logger logger = LoggerFactory.getLogger(SystemInfoController.class);

    @Value("${server.port:8045}")
    private String serverPort;

    @Value("${spring.application.name:imgapi}")
    private String applicationName;

    @Operation(summary = "获取系统基本信息")
    @GetMapping("/info")
    public Map<String, Object> getSystemInfo() {
        logger.info("获取系统基本信息");

        Map<String, Object> info = new HashMap<>();

        Map<String, String> appInfo = new HashMap<>();
        appInfo.put("name", applicationName);
        appInfo.put("startTime", getStartTime());
        appInfo.put("runTime", getRunTime());
        info.put("application", appInfo);

        Map<String, Object> jvmInfo = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        jvmInfo.put("javaVersion", System.getProperty("java.version"));
        jvmInfo.put("javaVendor", System.getProperty("java.vendor"));
        jvmInfo.put("javaHome", System.getProperty("java.home"));
        jvmInfo.put("availableProcessors", runtime.availableProcessors());
        jvmInfo.put("maxMemory", formatBytes(runtime.maxMemory()));
        jvmInfo.put("totalMemory", formatBytes(runtime.totalMemory()));
        jvmInfo.put("freeMemory", formatBytes(runtime.freeMemory()));
        jvmInfo.put("usedMemory", formatBytes(runtime.totalMemory() - runtime.freeMemory()));
        info.put("jvm", jvmInfo);

        Map<String, String> osInfo = new HashMap<>();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        osInfo.put("name", osBean.getName());
        osInfo.put("version", osBean.getVersion());
        osInfo.put("arch", osBean.getArch());
        osInfo.put("availableProcessors", String.valueOf(osBean.getAvailableProcessors()));
        info.put("operatingSystem", osInfo);

        return info;
    }

    @Operation(summary = "获取内存详细信息")
    @GetMapping("/memory")
    public Map<String, Object> getMemoryInfo() {
        logger.info("获取内存详细信息");

        Map<String, Object> memoryInfo = new HashMap<>();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Map<String, Object> heapMemory = new HashMap<>();
        heapMemory.put("init", formatBytes(memoryMXBean.getHeapMemoryUsage().getInit()));
        heapMemory.put("used", formatBytes(memoryMXBean.getHeapMemoryUsage().getUsed()));
        heapMemory.put("committed", formatBytes(memoryMXBean.getHeapMemoryUsage().getCommitted()));
        heapMemory.put("max", formatBytes(memoryMXBean.getHeapMemoryUsage().getMax()));
        memoryInfo.put("heap", heapMemory);

        Map<String, Object> nonHeapMemory = new HashMap<>();
        nonHeapMemory.put("init", formatBytes(memoryMXBean.getNonHeapMemoryUsage().getInit()));
        nonHeapMemory.put("used", formatBytes(memoryMXBean.getNonHeapMemoryUsage().getUsed()));
        nonHeapMemory.put("committed", formatBytes(memoryMXBean.getNonHeapMemoryUsage().getCommitted()));
        nonHeapMemory.put("max", formatBytes(memoryMXBean.getNonHeapMemoryUsage().getMax()));
        memoryInfo.put("nonHeap", nonHeapMemory);

        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double usagePercent = (maxMemory > 0) ? (usedMemory * 100.0 / maxMemory) : 0;
        memoryInfo.put("usagePercent", String.format("%.2f%%", usagePercent));

        return memoryInfo;
    }

    @Operation(summary = "获取运行时信息")
    @GetMapping("/runtime")
    public Map<String, Object> getRuntimeInfo() {
        logger.info("获取运行时信息");

        Map<String, Object> runtimeInfo = new HashMap<>();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        runtimeInfo.put("name", runtimeMXBean.getName());
        runtimeInfo.put("startTime", runtimeMXBean.getStartTime());
        runtimeInfo.put("uptimeMillis", runtimeMXBean.getUptime());
        runtimeInfo.put("uptimeFormatted", formatDuration(runtimeMXBean.getUptime()));
        runtimeInfo.put("classPath", System.getProperty("java.class.path"));
        runtimeInfo.put("inputArguments", runtimeMXBean.getInputArguments());

        return runtimeInfo;
    }

    @Operation(summary = "系统健康检查")
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        logger.debug("健康检查");

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("port", serverPort);
        health.put("application", applicationName);

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double usagePercent = (maxMemory > 0) ? (usedMemory * 100.0 / maxMemory) : 0;

        Map<String, Object> components = new HashMap<>();
        Map<String, Object> memoryHealth = new HashMap<>();
        memoryHealth.put("status", usagePercent < 90 ? "UP" : "WARNING");
        memoryHealth.put("usagePercent", String.format("%.2f%%", usagePercent));
        components.put("memory", memoryHealth);
        health.put("components", components);

        return health;
    }

    @Operation(summary = "获取系统属性")
    @GetMapping("/properties")
    public Map<String, String> getSystemProperties() {
        logger.info("获取系统属性");

        Map<String, String> properties = new HashMap<>();
        Properties props = System.getProperties();

        String[] commonProps = {
                "java.version", "java.vendor", "java.home",
                "os.name", "os.version", "os.arch",
                "user.name", "user.dir", "user.home",
                "file.encoding", "sun.stdout.encoding"
        };

        for (String prop : commonProps) {
            if (props.containsKey(prop)) {
                properties.put(prop, props.getProperty(prop));
            }
        }

        return properties;
    }

    @Operation(summary = "获取统一监控数据")
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("info", getSystemInfo());
        overview.put("memory", getMemoryInfo());
        overview.put("runtime", getRuntimeInfo());
        overview.put("health", healthCheck());
        overview.put("properties", getSystemProperties());
        return overview;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }

        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2f KB", kb);
        }

        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2f MB", mb);
        }

        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("天 ");
        }
        if (hours % 24 > 0) {
            sb.append(hours % 24).append("小时 ");
        }
        if (minutes % 60 > 0) {
            sb.append(minutes % 60).append("分钟 ");
        }
        sb.append(seconds % 60).append("秒");

        return sb.toString();
    }

    private String getStartTime() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long startTime = runtimeMXBean.getStartTime();
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTime),
                java.time.ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String getRunTime() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return formatDuration(runtimeMXBean.getUptime());
    }
}
