package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 读取并缓存开发者档案袋兼容模式开关。
 *
 * <p>该模式只用于旧系统无 Token 打开影像档案袋，不再作为全系统认证旁路。
 * 必须同时满足启动配置允许、数据库运行时开关开启、请求来自受信任的本机代理，
 * 并且请求属于明确的只读档案袋接口。</p>
 */
@Service
public class DeveloperModeService {

    public static final String SETTING_KEY = "developerModeEnabled";
    public static final String ARCHIVE_LEGACY_ACCESS_MODE = "archive-legacy";

    private static final Logger logger = LoggerFactory.getLogger(DeveloperModeService.class);
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on", "enabled");
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(3);

    private static final Pattern IMAGE_BY_BAH = Pattern.compile("^/api/v1/img/\\d{1,8}$");
    private static final Pattern IMAGE_CONTENT = Pattern.compile("^/api/v1/img/image/[^/]+/[^/]+/[^/]+/[^/]+$");
    private static final Pattern IMAGE_URL = Pattern.compile("^/api/v1/img/url/\\d+$");
    private static final Pattern OSS_IMAGE = Pattern.compile("^/api/v1/img/oss-image/\\d+$");
    private static final Pattern PATIENT_BY_BAH = Pattern.compile("^/api/v1/search/patient/\\d{1,8}$");

    private final SystemSettingMapper systemSettingMapper;
    private final boolean startupAllowed;
    private final Set<String> allowedRemoteAddresses;

    private volatile boolean cachedDatabaseEnabled;
    private volatile long cacheExpiresAtNanos;

    public DeveloperModeService(
            SystemSettingMapper systemSettingMapper,
            @Value("${mrr.developer-mode.allowed:false}") boolean startupAllowed,
            @Value("${mrr.developer-mode.allowed-remote-addresses:127.0.0.1,::1}") List<String> allowedRemoteAddresses
    ) {
        this.systemSettingMapper = systemSettingMapper;
        this.startupAllowed = startupAllowed;
        this.allowedRemoteAddresses = allowedRemoteAddresses == null
                ? Set.of()
                : allowedRemoteAddresses.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 返回启动配置与数据库开关共同决定的有效状态。
     */
    public boolean isEnabled() {
        if (!startupAllowed) {
            return false;
        }
        return isDatabaseEnabled();
    }

    /**
     * 判断当前请求是否可以使用旧版档案袋兼容访问。
     */
    public boolean isArchiveLegacyRequestAllowed(HttpServletRequest request) {
        if (!isArchiveLegacyRequestAvailable(request)) {
            return false;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            return false;
        }
        if ("/api/v1/img/search".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/v1/img/download/")) {
            return false;
        }
        return IMAGE_BY_BAH.matcher(path).matches()
                || IMAGE_CONTENT.matcher(path).matches()
                || IMAGE_URL.matcher(path).matches()
                || OSS_IMAGE.matcher(path).matches()
                || PATIENT_BY_BAH.matcher(path).matches();
    }

    /**
     * 公共状态接口使用该方法，避免向无法通过受信任代理访问的客户端宣告兼容模式可用。
     */
    public boolean isArchiveLegacyRequestAvailable(HttpServletRequest request) {
        if (!isEnabled() || request == null) {
            return false;
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) && allowedRemoteAddresses.contains(remoteAddr.trim());
    }

    /**
     * 系统设置写入成功后立即刷新数据库开关缓存，使开关无需重启即可生效。
     */
    public synchronized void refreshFromValue(String value) {
        cachedDatabaseEnabled = parseEnabled(value);
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn(
                "开发者档案袋兼容模式运行时状态已更新: startupAllowed={}, databaseEnabled={}, effectiveEnabled={}",
                startupAllowed,
                cachedDatabaseEnabled,
                startupAllowed && cachedDatabaseEnabled
        );
    }

    public synchronized void disableImmediately() {
        cachedDatabaseEnabled = false;
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者档案袋兼容模式已关闭");
    }

    public synchronized void invalidate() {
        cacheExpiresAtNanos = 0L;
    }

    private boolean isDatabaseEnabled() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos) {
            return cachedDatabaseEnabled;
        }

        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheExpiresAtNanos) {
                return cachedDatabaseEnabled;
            }

            boolean enabled = loadFromDatabase();
            cachedDatabaseEnabled = enabled;
            cacheExpiresAtNanos = now + CACHE_TTL_NANOS;
            return enabled;
        }
    }

    private boolean loadFromDatabase() {
        try {
            SystemSetting setting = systemSettingMapper.findByKey(SETTING_KEY);
            boolean databaseEnabled = setting != null && parseEnabled(setting.getSettingValue());
            if (databaseEnabled && !startupAllowed) {
                logger.warn("数据库中的开发者模式开关已开启，但启动配置未允许，兼容模式保持关闭");
            }
            return databaseEnabled;
        } catch (Exception exception) {
            logger.error("读取开发者模式设置失败，已按关闭处理", exception);
            return false;
        }
    }

    static boolean parseEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
