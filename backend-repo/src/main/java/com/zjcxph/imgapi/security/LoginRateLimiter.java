package com.zjcxph.imgapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录/注册频率限制器 — 基于内存的简单滑动窗口。
 * <p>
 * 同一 username 在 15 分钟内最多 5 次失败登录尝试，
 * 同一 IP 每分钟最多 3 次注册请求。
 * 服务重启后计数归零（可接受）。
 * </p>
 */
@Component
public class LoginRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(LoginRateLimiter.class);

    /**
     * 登录失败记录：username → 失败时间戳列表
     */
    private final ConcurrentHashMap<String, LinkedList<Long>> loginFailures = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_WINDOW_MS = 15 * 60 * 1000; // 15 分钟

    /**
     * 注册记录：IP → 注册时间戳列表
     */
    private final ConcurrentHashMap<String, LinkedList<Long>> registerAttempts = new ConcurrentHashMap<>();
    private static final int MAX_REGISTER_PER_IP = 3;
    private static final long REGISTER_WINDOW_MS = 60 * 1000; // 1 分钟

    // ==================== 登录限制 ====================

    /**
     * 检查 username 是否被临时锁定。
     */
    public boolean isLoginBlocked(String username) {
        LinkedList<Long> timestamps = loginFailures.get(username);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            long cutoff = System.currentTimeMillis() - LOGIN_WINDOW_MS;
            timestamps.removeIf(ts -> ts < cutoff);
            return timestamps.size() >= MAX_LOGIN_ATTEMPTS;
        }
    }

    /**
     * 记录一次登录失败。
     */
    public void recordLoginFailure(String username) {
        loginFailures.compute(username, (k, v) -> {
            if (v == null) {
                v = new LinkedList<>();
            }
            synchronized (v) {
                v.add(System.currentTimeMillis());
            }
            return v;
        });
        logger.warn("Login failure recorded for user: {}", username);
    }

    /**
     * 登录成功后清除该 username 的失败记录。
     */
    public void resetLoginFailures(String username) {
        loginFailures.remove(username);
    }

    // ==================== 注册限制 ====================

    /**
     * 检查 IP 是否被临时限制注册。
     */
    public boolean isRegisterBlocked(String clientIp) {
        LinkedList<Long> timestamps = registerAttempts.get(clientIp);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            long cutoff = System.currentTimeMillis() - REGISTER_WINDOW_MS;
            timestamps.removeIf(ts -> ts < cutoff);
            return timestamps.size() >= MAX_REGISTER_PER_IP;
        }
    }

    /**
     * 记录一次注册（成功或失败都算，防止扫描用户名）。
     */
    public void recordRegisterAttempt(String clientIp) {
        registerAttempts.compute(clientIp, (k, v) -> {
            if (v == null) {
                v = new LinkedList<>();
            }
            synchronized (v) {
                v.add(System.currentTimeMillis());
            }
            return v;
        });
    }
}
