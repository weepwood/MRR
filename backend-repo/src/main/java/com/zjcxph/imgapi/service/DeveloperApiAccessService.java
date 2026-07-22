package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class DeveloperApiAccessService {

    public static final String SETTING_KEY = "developerModeApiAccessEnabled";
    public static final String API_FULL_ACCESS_MODE = "api-full";

    private static final Logger logger = LoggerFactory.getLogger(DeveloperApiAccessService.class);
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on", "enabled");
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final SystemSettingMapper systemSettingMapper;
    private final DeveloperModeService developerModeService;
    private volatile boolean cachedEnabled;
    private volatile long cacheExpiresAtNanos;

    public DeveloperApiAccessService(SystemSettingMapper systemSettingMapper,
                                     DeveloperModeService developerModeService) {
        this.systemSettingMapper = systemSettingMapper;
        this.developerModeService = developerModeService;
    }

    public boolean isEnabled() {
        ensureLoaded();
        return cachedEnabled && developerModeService.isEnabled();
    }

    public boolean isRequestAllowed(HttpServletRequest request) {
        if (request == null || !isEnabled()) {
            return false;
        }
        if (StringUtils.hasText(request.getHeader("Authorization"))) {
            return false;
        }
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path) || !path.startsWith("/api/")) {
            return false;
        }
        return developerModeService.isArchiveLegacyRequestAvailable(request);
    }

    public synchronized void refreshFromValue(String value) {
        cachedEnabled = parseEnabled(value);
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者完整 API 访问状态已更新: enabled={}", cachedEnabled);
    }

    public synchronized void disableImmediately() {
        cachedEnabled = false;
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者完整 API 访问已关闭");
    }

    public synchronized void invalidate() {
        cacheExpiresAtNanos = 0L;
    }

    private void ensureLoaded() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos) {
            return;
        }
        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheExpiresAtNanos) {
                return;
            }
            try {
                SystemSetting setting = systemSettingMapper.findByKey(SETTING_KEY);
                cachedEnabled = setting != null && parseEnabled(setting.getSettingValue());
            } catch (Exception exception) {
                cachedEnabled = false;
                logger.error("读取开发者完整 API 访问设置失败，已按关闭处理", exception);
            }
            cacheExpiresAtNanos = now + CACHE_TTL_NANOS;
        }
    }

    static boolean parseEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }
}
