package com.zjcxph.imgapi.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * 登录/注册频率限制器 — 基于内存的简单滑动窗口。
 * <p>
 * 同一 username + IP 在 15 分钟内最多 5 次失败登录尝试，
 * 同一 IP 每分钟最多 3 次注册请求。
 * 服务重启后计数归零（可接受）。
 * </p>
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_WINDOW_MS = 15 * 60 * 1000;
    private static final int MAX_LOGIN_KEYS = 20_000;

    private static final int MAX_REGISTER_PER_IP = 3;
    private static final long REGISTER_WINDOW_MS = 60 * 1000;
    private static final int MAX_REGISTER_KEYS = 10_000;

    private final Cache<String, LinkedList<Long>> loginFailures;
    private final Cache<String, LinkedList<Long>> registerAttempts;
    private final LongSupplier currentTimeMillis;

    public LoginRateLimiter() {
        this(Ticker.systemTicker(), System::currentTimeMillis);
    }

    LoginRateLimiter(Ticker ticker, LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
        loginFailures = Caffeine.newBuilder()
                .maximumSize(MAX_LOGIN_KEYS)
                .expireAfterAccess(Duration.ofMinutes(30))
                .ticker(ticker)
                .build();
        registerAttempts = Caffeine.newBuilder()
                .maximumSize(MAX_REGISTER_KEYS)
                .expireAfterAccess(Duration.ofMinutes(5))
                .ticker(ticker)
                .build();
    }

    // ==================== 登录限制 ====================

    /**
     * 检查 username + IP 是否被临时锁定。
     */
    public boolean isLoginBlocked(String attemptKey) {
        return isBlocked(loginFailures, attemptKey, LOGIN_WINDOW_MS, MAX_LOGIN_ATTEMPTS);
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

    // ==================== 注册限制 ====================

    /**
     * 检查 IP 是否被临时限制注册。
     */
    public boolean isRegisterBlocked(String clientIp) {
        return isBlocked(registerAttempts, clientIp, REGISTER_WINDOW_MS, MAX_REGISTER_PER_IP);
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
