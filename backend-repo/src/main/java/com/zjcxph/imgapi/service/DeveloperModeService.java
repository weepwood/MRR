package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 读取并缓存系统级开发者模式开关。
 *
 * <p>开发者模式默认关闭。设置缺失、数据库异常或值无法识别时一律按关闭处理，
 * 避免配置故障意外放开认证。</p>
 */
@Service
public class DeveloperModeService {

    public static final String SETTING_KEY = "developerModeEnabled";

    private static final Logger logger = LoggerFactory.getLogger(DeveloperModeService.class);
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on", "enabled");
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final SystemSettingMapper systemSettingMapper;

    private volatile boolean cachedEnabled;
    private volatile long cacheExpiresAtNanos;

    public DeveloperModeService(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    public boolean isEnabled() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos) {
            return cachedEnabled;
        }

        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheExpiresAtNanos) {
                return cachedEnabled;
            }

            boolean enabled = loadFromDatabase();
            cachedEnabled = enabled;
            cacheExpiresAtNanos = now + CACHE_TTL_NANOS;
            return enabled;
        }
    }

    /**
     * 系统设置写入成功后立即刷新缓存，使开关无需重启即可生效。
     */
    public synchronized void refreshFromValue(String value) {
        cachedEnabled = parseEnabled(value);
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者模式运行时状态已更新: enabled={}", cachedEnabled);
    }

    public synchronized void disableImmediately() {
        cachedEnabled = false;
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者模式已关闭");
    }

    public synchronized void invalidate() {
        cacheExpiresAtNanos = 0L;
    }

    private boolean loadFromDatabase() {
        try {
            SystemSetting setting = systemSettingMapper.findByKey(SETTING_KEY);
            return setting != null && parseEnabled(setting.getSettingValue());
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
