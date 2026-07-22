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

/**
 * 开发者模式下的业务权限旁路。
 *
 * <p>该服务只补齐业务权限，不跳过登录认证。请求仍必须携带有效 Token，
 * 因此数据库写入、审计日志和用户关联始终使用真实账号。</p>
 */
@Service
public class DeveloperApiAccessService {

    public static final String SETTING_KEY = "developerModeApiAccessEnabled";
    public static final String API_PERMISSION_BYPASS_MODE = "api-permission-bypass";

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

    public boolean isPermissionBypassAllowed(HttpServletRequest request) {
        if (request == null || !isEnabled()) {
            return false;
        }
        String path = normalizeRequestPath(request);
        if (!StringUtils.hasText(path) || !path.startsWith("/api/")) {
            return false;
        }
        return developerModeService.isArchiveLegacyRequestAvailable(request);
    }

    public synchronized void refreshFromValue(String value) {
        cachedEnabled = parseEnabled(value);
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者 API 权限旁路状态已更新: enabled={}", cachedEnabled);
    }

    public synchronized void disableImmediately() {
        cachedEnabled = false;
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者 API 权限旁路已关闭");
    }

    public synchronized void invalidate() {
        cacheExpiresAtNanos = 0L;
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
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
                logger.error("读取开发者 API 权限旁路设置失败，已按关闭处理", exception);
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
