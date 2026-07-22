package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 读取并缓存开发者档案袋兼容模式设置。
 *
 * <p>该模式只用于旧系统无 Token 打开影像档案袋，不再作为全系统认证旁路。
 * 请求必须来自启动配置允许的本机 Nginx，并且真实客户端地址必须命中系统设置中的
 * 单 IP 或 CIDR 网段白名单。</p>
 */
@Service
public class DeveloperModeService {

    public static final String SETTING_KEY = "developerModeEnabled";
    public static final String ALLOWED_SOURCES_SETTING_KEY = "developerModeAllowedSources";
    public static final String ARCHIVE_LEGACY_ACCESS_MODE = "archive-legacy";
    public static final String DEFAULT_ALLOWED_SOURCES = "127.0.0.1\n::1";

    private static final Logger logger = LoggerFactory.getLogger(DeveloperModeService.class);
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "on", "enabled");
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(3);

    private static final Pattern IMAGE_BY_BAH = Pattern.compile("^/api/v1/img/\\d{1,8}$");
    private static final Pattern IMAGE_CONTENT = Pattern.compile("^/api/v1/img/image/[^/]+/[^/]+/[^/]+/[^/]+$");
    private static final Pattern IMAGE_URL = Pattern.compile("^/api/v1/img/url/\\d+$");
    private static final Pattern OSS_IMAGE = Pattern.compile("^/api/v1/img/oss-image/\\d+$");
    private static final Pattern PATIENT_BY_BAH = Pattern.compile("^/api/v1/search/patient/\\d{1,8}$");
    private static final Pattern NUMERIC_ADDRESS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final SystemSettingMapper systemSettingMapper;
    private final boolean startupAllowed;
    private final List<IpRule> trustedProxyRules;

    private volatile boolean cachedDatabaseEnabled;
    private volatile List<IpRule> cachedAllowedSourceRules = List.of();
    private volatile long cacheExpiresAtNanos;

    @Autowired
    public DeveloperModeService(
            SystemSettingMapper systemSettingMapper,
            @Value("${mrr.developer-mode.allowed:false}") boolean startupAllowed,
            @Value("${mrr.developer-mode.trusted-proxy-addresses:127.0.0.1,::1}") String trustedProxyAddresses
    ) {
        this.systemSettingMapper = systemSettingMapper;
        this.startupAllowed = startupAllowed;
        this.trustedProxyRules = List.copyOf(parseConfiguredRules(trustedProxyAddresses, "可信代理地址"));
    }

    DeveloperModeService(
            SystemSettingMapper systemSettingMapper,
            boolean startupAllowed,
            List<String> trustedProxyAddresses
    ) {
        this.systemSettingMapper = systemSettingMapper;
        this.startupAllowed = startupAllowed;
        String configured = String.join(",", trustedProxyAddresses == null ? List.of() : trustedProxyAddresses);
        this.trustedProxyRules = List.copyOf(parseConfiguredRules(configured, "可信代理地址"));
    }

    /**
     * 返回启动配置与数据库开关共同决定的有效状态。
     */
    public boolean isEnabled() {
        if (!startupAllowed) {
            return false;
        }
        ensureRuntimeSettingsLoaded();
        return cachedDatabaseEnabled;
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
     * 公共状态接口使用该方法，只向命中可信客户端白名单的请求返回兼容模式可用。
     */
    public boolean isArchiveLegacyRequestAvailable(HttpServletRequest request) {
        if (!isEnabled() || request == null) {
            return false;
        }

        String proxyAddress = normalizeAddress(request.getRemoteAddr());
        if (!matchesAny(trustedProxyRules, proxyAddress)) {
            return false;
        }

        String clientAddress = resolveClientAddress(request, proxyAddress);
        return matchesAny(cachedAllowedSourceRules, clientAddress);
    }

    /**
     * 校验系统设置中的单 IP/CIDR 列表。空列表合法，但会拒绝全部兼容访问。
     */
    public void validateAllowedSourcesValue(String value) {
        parseAllowedSourceRules(value, true);
    }

    /**
     * 系统设置写入成功后立即刷新数据库开关缓存。
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

    /**
     * 系统设置写入成功后立即刷新客户端 IP/CIDR 白名单。
     */
    public synchronized void refreshAllowedSourcesFromValue(String value) {
        cachedAllowedSourceRules = List.copyOf(parseAllowedSourceRules(value, true));
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者档案袋可信客户端来源已更新: rules={}", cachedAllowedSourceRules.size());
    }

    public synchronized void disableImmediately() {
        cachedDatabaseEnabled = false;
        cacheExpiresAtNanos = System.nanoTime() + CACHE_TTL_NANOS;
        logger.warn("开发者档案袋兼容模式已关闭");
    }

    public synchronized void invalidate() {
        cacheExpiresAtNanos = 0L;
    }

    private void ensureRuntimeSettingsLoaded() {
        long now = System.nanoTime();
        if (now < cacheExpiresAtNanos) {
            return;
        }

        synchronized (this) {
            now = System.nanoTime();
            if (now < cacheExpiresAtNanos) {
                return;
            }

            loadRuntimeSettings();
            cacheExpiresAtNanos = now + CACHE_TTL_NANOS;
        }
    }

    private void loadRuntimeSettings() {
        try {
            SystemSetting enabledSetting = systemSettingMapper.findByKey(SETTING_KEY);
            cachedDatabaseEnabled = enabledSetting != null && parseEnabled(enabledSetting.getSettingValue());

            SystemSetting sourcesSetting = systemSettingMapper.findByKey(ALLOWED_SOURCES_SETTING_KEY);
            String configuredSources = sourcesSetting == null
                    ? DEFAULT_ALLOWED_SOURCES
                    : sourcesSetting.getSettingValue();
            cachedAllowedSourceRules = List.copyOf(parseAllowedSourceRules(configuredSources, false));

            if (cachedDatabaseEnabled && !startupAllowed) {
                logger.warn("数据库中的开发者模式开关已开启，但启动配置未允许，兼容模式保持关闭");
            }
        } catch (Exception exception) {
            cachedDatabaseEnabled = false;
            cachedAllowedSourceRules = List.of();
            logger.error("读取开发者模式设置失败，已按关闭处理", exception);
        }
    }

    private String resolveClientAddress(HttpServletRequest request, String proxyAddress) {
        String forwarded = firstForwardedAddress(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwarded)) {
            return normalizeAddress(forwarded);
        }
        String realIp = normalizeAddress(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return proxyAddress;
    }

    private static String firstForwardedAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int separator = value.indexOf(',');
        return separator >= 0 ? value.substring(0, separator).trim() : value.trim();
    }

    private static boolean matchesAny(List<IpRule> rules, String candidate) {
        if (!StringUtils.hasText(candidate) || rules == null || rules.isEmpty()) {
            return false;
        }
        for (IpRule rule : rules) {
            if (rule.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<IpRule> parseConfiguredRules(String configured, String label) {
        try {
            return parseRules(configured, true);
        } catch (BusinessException exception) {
            throw new IllegalArgumentException(label + "配置无效: " + exception.getMessage(), exception);
        }
    }

    private static List<IpRule> parseAllowedSourceRules(String configured, boolean strict) {
        try {
            return parseRules(configured, strict);
        } catch (BusinessException exception) {
            if (strict) {
                throw exception;
            }
            logger.error("开发者模式可信来源配置无效，已按空白名单处理: {}", exception.getMessage());
            return List.of();
        }
    }

    private static List<IpRule> parseRules(String configured, boolean strict) {
        if (!StringUtils.hasText(configured)) {
            return List.of();
        }

        List<IpRule> rules = new ArrayList<>();
        List<String> values = Arrays.stream(configured.split("[,;\\r\\n]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        for (String value : values) {
            try {
                rules.add(IpRule.parse(value));
            } catch (IllegalArgumentException exception) {
                if (strict) {
                    throw new BusinessException(400,
                            "可信来源格式不正确：" + value + "。请输入单个 IP 或 CIDR 网段，例如 192.168.1.20、192.168.1.0/24");
                }
                logger.warn("忽略无效的开发者模式可信来源规则: {}", value);
            }
        }
        return rules;
    }

    private static String normalizeAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    static boolean parseEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private record IpRule(byte[] networkAddress, int prefixLength) {

        static IpRule parse(String configured) {
            String value = normalizeAddress(configured);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("IP 不能为空");
            }

            String[] parts = value.split("/", -1);
            if (parts.length > 2 || !isNumericAddress(parts[0])) {
                throw new IllegalArgumentException("不是数字 IP 地址");
            }

            byte[] address = parseNumericAddress(parts[0]);
            int maxPrefix = address.length * Byte.SIZE;
            int prefix = maxPrefix;
            if (parts.length == 2) {
                try {
                    prefix = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("CIDR 前缀不是数字", exception);
                }
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("CIDR 前缀超出范围");
                }
            }
            return new IpRule(address, prefix);
        }

        boolean matches(String candidate) {
            String normalized = normalizeAddress(candidate);
            if (!isNumericAddress(normalized)) {
                return false;
            }

            byte[] candidateAddress;
            try {
                candidateAddress = parseNumericAddress(normalized);
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (candidateAddress.length != networkAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (candidateAddress[index] != networkAddress[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidateAddress[fullBytes] & mask) == (networkAddress[fullBytes] & mask);
        }

        private static boolean isNumericAddress(String value) {
            return StringUtils.hasText(value)
                    && NUMERIC_ADDRESS.matcher(value).matches()
                    && (value.contains(".") || value.contains(":"));
        }

        private static byte[] parseNumericAddress(String value) {
            try {
                return InetAddress.getByName(value).getAddress();
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("IP 地址无效", exception);
            }
        }
    }
}
