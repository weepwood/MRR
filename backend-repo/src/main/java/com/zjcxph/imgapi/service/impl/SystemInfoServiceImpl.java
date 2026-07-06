package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.service.SystemInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 系统信息服务实现。
 * <p>
 * 将原 SystemInfoController 中的数据组装逻辑下沉到 Service 层。
 * Controller 各端点改为调用本 Service，避免 Controller 内部互调（this.xxx().getData()）
 * 产生的临时 Result 对象与分层混乱。
 * </p>
 */
@Service
public class SystemInfoServiceImpl implements SystemInfoService {

    private static final Logger logger = LoggerFactory.getLogger(SystemInfoServiceImpl.class);

    @Value("${server.port:8045}")
    private String serverPort;

    @Value("${spring.application.name:imgapi}")
    private String applicationName;

    @Autowired(required = false)
    private DataSource dataSource;

    @Override
    public Map<String, Object> getOverview() {
        Map<String, Object> overview = new HashMap<>();
        overview.put("info", getSystemInfo());
        overview.put("memory", getMemoryInfo());
        overview.put("runtime", getRuntimeInfo());
        overview.put("health", getHealth());
        overview.put("properties", getSystemProperties());
        overview.put("gc", getGcStats());
        overview.put("threads", getThreadStats());
        return overview;
    }

    @Override
    public Map<String, Object> getSystemInfo() {
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
        osInfo.put("systemLoadAverage", String.valueOf(osBean.getSystemLoadAverage()));
        info.put("operatingSystem", osInfo);

        return info;
    }

    @Override
    public Map<String, Object> getMemoryInfo() {
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

    @Override
    public Map<String, Object> getRuntimeInfo() {
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

    @Override
    public Map<String, Object> getHealth() {
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

        Map<String, Object> dbHealth = new HashMap<>();
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                dbHealth.put("status", conn.isValid(3) ? "UP" : "DOWN");
            } catch (Exception e) {
                dbHealth.put("status", "DOWN");
                dbHealth.put("error", e.getMessage());
            }
        } else {
            dbHealth.put("status", "UNKNOWN");
        }
        components.put("database", dbHealth);

        health.put("components", components);
        return health;
    }

    @Override
    public Map<String, String> getSystemProperties() {
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

    @Override
    public Map<String, Object> getGcStats() {
        Map<String, Object> gc = new HashMap<>();
        java.util.List<java.lang.management.GarbageCollectorMXBean> gcBeans =
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
        long totalCollections = 0;
        long totalTimeMs = 0;
        for (var bean : gcBeans) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", bean.getName());
            item.put("count", bean.getCollectionCount());
            item.put("timeMs", bean.getCollectionTime());
            gc.put(bean.getName(), item);
            totalCollections += bean.getCollectionCount();
            totalTimeMs += bean.getCollectionTime();
        }
        gc.put("totalCollections", totalCollections);
        gc.put("totalTimeMs", totalTimeMs);
        return gc;
    }

    @Override
    public Map<String, Object> getThreadStats() {
        java.lang.management.ThreadMXBean threadMX = java.lang.management.ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new HashMap<>();
        threads.put("currentCount", threadMX.getThreadCount());
        threads.put("daemonCount", threadMX.getDaemonThreadCount());
        threads.put("peakCount", threadMX.getPeakThreadCount());
        threads.put("totalStarted", threadMX.getTotalStartedThreadCount());
        return threads;
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
