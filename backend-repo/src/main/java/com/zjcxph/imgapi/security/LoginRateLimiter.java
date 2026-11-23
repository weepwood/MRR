package com.zjcxph.imgapi.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * 登录/注册频率限制器 — 基于内存的简单滑动窗口。
 * <p>
 * 阈值与窗口长度由系统设置维护（{@link SettingRegistry} 中的 {@code security} 分组），
 * 默认同一 username + IP 在 15 分钟内最多 5 次失败登录尝试，
 * 同一 IP 每分钟最多 3 次注册请求。管理员可在后台调整，保存后立即生效。
 * 服务重启后计数归零（可接受）。
 * </p>
 * <p>
 * 另按账号单独计数：攻击者轮换来源 IP 时，“username + IP”维度永远达不到阈值，
 * 因此同一账号在同一窗口内累计失败达到 {@value #ACCOUNT_ATTEMPT_MULTIPLIER} 倍登录上限时
 * 也会被临时限制。倍数留出余量，避免共用终端上的正常输错把账号锁死。
 * </p>
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    /** 失败登录记录的默认上限，与抽取到设置注册表前的常量保持一致。 */
    private static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    private static final long DEFAULT_LOGIN_WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_LOGIN_KEYS = 20_000;
    /** 账号维度阈值 = 登录上限 × 该倍数。 */
    static final int ACCOUNT_ATTEMPT_MULTIPLIER = 4;
    private static final int MAX_ACCOUNT_KEYS = 20_000;
    /**
     * 空闲过期必须长于系统设置允许的最大锁定窗口（60 分钟），否则较长窗口下的失败记录
     * 会在窗口结束前被缓存淘汰，限制提前失效。
     */
    private static final Duration LOGIN_STATE_IDLE_EXPIRATION = Duration.ofMinutes(70);

    private static final int DEFAULT_MAX_REGISTER_PER_IP = 3;
    private static final long DEFAULT_REGISTER_WINDOW_MS = 60 * 1000L;
    private static final int MAX_REGISTER_KEYS = 10_000;
    /** 长于注册统计窗口允许的最大值（600 秒）。 */
    private static final Duration REGISTER_STATE_IDLE_EXPIRATION = Duration.ofMinutes(15);

    private final Cache<String, LinkedList<Long>> loginFailures;
    private final Cache<String, LinkedList<Long>> accountFailures;
    private final Cache<String, LinkedList<Long>> registerAttempts;
    private final LongSupplier currentTimeMillis;
    private final RuntimeSettingStore settingStore;

    @Autowired
    public LoginRateLimiter(RuntimeSettingStore settingStore) {
        this(Ticker.systemTicker(), System::currentTimeMillis, settingStore);
    }

    /**
     * 测试用构造：不接入系统设置，恒用默认阈值，保证既有单测行为不变。
     */
    LoginRateLimiter(Ticker ticker, LongSupplier currentTimeMillis) {
        this(ticker, currentTimeMillis, null);
    }

    LoginRateLimiter(Ticker ticker, LongSupplier currentTimeMillis, RuntimeSettingStore settingStore) {
        this.currentTimeMillis = currentTimeMillis;
        this.settingStore = settingStore;
        loginFailures = Caffeine.newBuilder()
                .maximumSize(MAX_LOGIN_KEYS)
                .expireAfterAccess(LOGIN_STATE_IDLE_EXPIRATION)
                .ticker(ticker)
                .build();
        accountFailures = Caffeine.newBuilder()
                .maximumSize(MAX_ACCOUNT_KEYS)
                .expireAfterAccess(LOGIN_STATE_IDLE_EXPIRATION)
                .ticker(ticker)
                .build();
        registerAttempts = Caffeine.newBuilder()
                .maximumSize(MAX_REGISTER_KEYS)
                .expireAfterAccess(REGISTER_STATE_IDLE_EXPIRATION)
                .ticker(ticker)
                .build();
    }

    // ==================== 运行阈值 ====================

    /** 登录失败上限（次）。 */
    public int maxLoginAttempts() {
        return settingStore == null
                ? DEFAULT_MAX_LOGIN_ATTEMPTS
                : settingStore.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS);
    }

    /** 登录锁定窗口（毫秒）。 */
    public long loginWindowMillis() {
        if (settingStore == null) {
            return DEFAULT_LOGIN_WINDOW_MS;
        }
        return settingStore.intValue(SettingRegistry.LOGIN_LOCK_WINDOW_MINUTES) * 60_000L;
    }

    /** 登录锁定窗口（分钟），用于向用户提示重试时间。 */
    public int loginLockWindowMinutes() {
        return (int) (loginWindowMillis() / 60_000L);
    }

    /** 同一 IP 的注册次数上限。 */
    public int maxRegisterPerIp() {
        return settingStore == null
                ? DEFAULT_MAX_REGISTER_PER_IP
                : settingStore.intValue(SettingRegistry.REGISTER_MAX_ATTEMPTS_PER_IP);
    }

    /** 注册统计窗口（毫秒）。 */
    public long registerWindowMillis() {
        if (settingStore == null) {
            return DEFAULT_REGISTER_WINDOW_MS;
        }
        return settingStore.intValue(SettingRegistry.REGISTER_WINDOW_SECONDS) * 1000L;
    }

    // ==================== 登录限制 ====================

    /**
     * 检查 username + IP 是否被临时锁定。
     */
    public boolean isLoginBlocked(String attemptKey) {
        return isBlocked(loginFailures, attemptKey, loginWindowMillis(), maxLoginAttempts());
    }

    /**
     * 记录一次登录失败。
     */
    public void recordLoginFailure(String attemptKey) {
        recordAttempt(loginFailures, attemptKey);
        logger.warn("Login failure recorded for attempt key: {}", attemptKey);
    }

    /**
     * 登录成功后清除该 username + IP 的失败记录。
     */
    public void resetLoginFailures(String attemptKey) {
        loginFailures.invalidate(attemptKey);
    }

    // ==================== 账号维度限制 ====================

    /** 账号维度失败上限（次）。 */
    public int maxAccountAttempts() {
        return maxLoginAttempts() * ACCOUNT_ATTEMPT_MULTIPLIER;
    }

    /**
     * 检查账号是否因跨来源累计失败被临时限制。
     */
    public boolean isAccountBlocked(String accountKey) {
        return isBlocked(accountFailures, accountKey, loginWindowMillis(), maxAccountAttempts());
    }

    /**
     * 记录一次账号维度的登录失败。
     */
    public void recordAccountFailure(String accountKey) {
        recordAttempt(accountFailures, accountKey);
    }

    /**
     * 登录成功后清除账号维度的失败记录。
     */
    public void resetAccountFailures(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) {
            return;
        }
        accountFailures.invalidate(accountKey);
    }

    // ==================== 注册限制 ====================

    /**
     * 检查 IP 是否被临时限制注册。
     */
    public boolean isRegisterBlocked(String clientIp) {
        return isBlocked(registerAttempts, clientIp, registerWindowMillis(), maxRegisterPerIp());
    }

    /**
     * 记录一次注册（成功或失败都算，防止扫描用户名）。
     */
    public void recordRegisterAttempt(String clientIp) {
        recordAttempt(registerAttempts, clientIp);
    }

    private void recordAttempt(Cache<String, LinkedList<Long>> cache, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        cache.asMap().compute(key, (ignored, timestamps) -> {
            LinkedList<Long> values = timestamps == null ? new LinkedList<>() : timestamps;
            synchronized (values) {
                values.add(currentTimeMillis.getAsLong());
            }
            return values;
        });
    }

    private boolean isBlocked(Cache<String, LinkedList<Long>> cache,
                              String key,
                              long windowMillis,
                              int maximumAttempts) {
        if (key == null || key.isBlank()) {
            return false;
        }

        AtomicBoolean blocked = new AtomicBoolean(false);
        long cutoff = currentTimeMillis.getAsLong() - windowMillis;
        cache.asMap().computeIfPresent(key, (ignored, timestamps) -> {
            synchronized (timestamps) {
                timestamps.removeIf(timestamp -> timestamp < cutoff);
                blocked.set(timestamps.size() >= maximumAttempts);
                return timestamps.isEmpty() ? null : timestamps;
            }
        });
        return blocked.get();
    }
}
