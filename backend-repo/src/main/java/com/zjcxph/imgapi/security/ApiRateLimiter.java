package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 API 限流器 — 基于内存的滑动窗口。
 * <p>
 * 按 IP 维度限制敏感接口的调用频率，防止滥用和暴力枚举。
 * 阈值与窗口长度由系统设置维护（默认 60 秒内 30 次），管理员可在后台调整，保存后立即生效。
 * 服务重启后计数归零（与 LoginRateLimiter 一致，可接受）。
 * </p>
 */
@Component
public class ApiRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimiter.class);

    /** 抽取到设置注册表之前的常量，作为设置读取不可用时的兜底。 */
    private static final int DEFAULT_MAX_REQUESTS = 30;
    private static final long DEFAULT_WINDOW_MS = 60 * 1000L;

    private final ConcurrentHashMap<String, LinkedList<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private final RuntimeSettingStore settingStore;

    public ApiRateLimiter(RuntimeSettingStore settingStore) {
        this.settingStore = settingStore;
    }

    /** 限流上限（次）。 */
    public int maxRequests() {
        return settingStore == null
                ? DEFAULT_MAX_REQUESTS
                : settingStore.intValue(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS);
    }

    /** 限流窗口（毫秒）。 */
    public long windowMillis() {
        if (settingStore == null) {
            return DEFAULT_WINDOW_MS;
        }
        return settingStore.intValue(SettingRegistry.API_RATE_LIMIT_WINDOW_SECONDS) * 1000L;
    }

    /**
     * 检查指定 IP 在当前窗口内是否超出限制。
     *
     * @param clientIp 客户端 IP
     * @return true 表示已被限流（应拒绝请求）
     */
    public boolean isBlocked(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        LinkedList<Long> timestamps = requestTimestamps.get(clientIp);
        if (timestamps == null) {
            return false;
        }
        synchronized (timestamps) {
            long cutoff = System.currentTimeMillis() - windowMillis();
            timestamps.removeIf(ts -> ts < cutoff);
            return timestamps.size() >= maxRequests();
        }
    }

    /**
     * 记录一次请求。如果超出限制则拒绝记录并返回 false。
     * <p>
     * 检查与计数在同一 synchronized 块内完成，保证原子性，
     * 避免高并发下多线程同时通过检查导致实际通过数超过上限。
     * </p>
     *
     * @param clientIp 客户端 IP
     * @return true 表示允许通过，false 表示已被限流
     */
    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }
        int maximumRequests = maxRequests();
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis();

        // computeIfAbsent 保证 LinkedList 只创建一次，后续 synchronized 基于同一对象
        LinkedList<Long> timestamps = requestTimestamps.computeIfAbsent(clientIp, k -> new LinkedList<>());

        synchronized (timestamps) {
            // 清理过期时间戳
            timestamps.removeIf(ts -> ts < cutoff);
            // 原子检查：超限则拒绝
            if (timestamps.size() >= maximumRequests) {
                logger.warn("API rate limit exceeded for IP: {}", clientIp);
                return false;
            }
            // 记录本次请求
            timestamps.add(now);
            return true;
        }
    }
}
