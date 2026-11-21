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
 * 对敏感 API 按 IP 执行原子滑动窗口限流。
 *
 * <p>与 LoginRateLimiter 一样，使用有界、空闲过期的 Caffeine 保存本地计数。
 * 访问过期时间为 20 分钟，长于系统设置允许的最大 10 分钟窗口；
 * 一旦超过容量，最久未使用的 IP 状态可能被淘汰，不能以此代替网关级防护。</p>
 */
@Component
public class ApiRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimiter.class);
    private static final int DEFAULT_MAX_REQUESTS = 30;
    private static final long DEFAULT_WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_IPS = 10_000;
    private static final Duration IP_IDLE_EXPIRATION = Duration.ofMinutes(20);

    private final Cache<String, LinkedList<Long>> requestTimestamps;
    private final RuntimeSettingStore settingStore;
    private final LongSupplier currentTimeMillis;

    @Autowired
    public ApiRateLimiter(RuntimeSettingStore settingStore) {
        this(settingStore, Ticker.systemTicker(), System::currentTimeMillis);
    }

    ApiRateLimiter(RuntimeSettingStore settingStore, Ticker ticker, LongSupplier currentTimeMillis) {
        this.settingStore = settingStore;
        this.currentTimeMillis = currentTimeMillis;
        this.requestTimestamps = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_IPS)
                .expireAfterAccess(IP_IDLE_EXPIRATION)
                .ticker(ticker)
                .build();
    }

    /** 当前限流上限（次），随运行设置即时生效。 */
    public int maxRequests() {
        return settingStore == null
                ? DEFAULT_MAX_REQUESTS
                : settingStore.intValue(SettingRegistry.API_RATE_LIMIT_MAX_REQUESTS);
    }

    /** 当前滑动窗口（毫秒），随运行设置即时生效。 */
    public long windowMillis() {
        return settingStore == null
                ? DEFAULT_WINDOW_MS
                : settingStore.intValue(SettingRegistry.API_RATE_LIMIT_WINDOW_SECONDS) * 1000L;
    }

    /** 不记录请求，仅判断指定 IP 当前是否已经触发限制。 */
    public boolean isBlocked(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        long cutoff = currentTimeMillis.getAsLong() - windowMillis();
        int maximumRequests = maxRequests();
        AtomicBoolean blocked = new AtomicBoolean(false);

        requestTimestamps.asMap().computeIfPresent(clientIp, (key, timestamps) -> {
            timestamps.removeIf(timestamp -> timestamp < cutoff);
            blocked.set(timestamps.size() >= maximumRequests);
            return timestamps.isEmpty() ? null : timestamps;
        });
        return blocked.get();
    }

    /**
     * 原子检查并计入一次请求。一个 IP 的所有修改都在同一个 Map.compute 内完成，
     * 避免并发检查和记录之间发生竞态。
     */
    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }

        int maximumRequests = maxRequests();
        long now = currentTimeMillis.getAsLong();
        long cutoff = now - windowMillis();
        AtomicBoolean allowed = new AtomicBoolean(false);

        requestTimestamps.asMap().compute(clientIp, (key, existing) -> {
            LinkedList<Long> timestamps = existing == null ? new LinkedList<>() : existing;
            timestamps.removeIf(timestamp -> timestamp < cutoff);
            if (timestamps.size() < maximumRequests) {
                timestamps.add(now);
                allowed.set(true);
            }
            return timestamps;
        });

        if (!allowed.get()) {
            logger.warn("API rate limit exceeded for IP: {}", clientIp);
        }
        return allowed.get();
    }

    // 测试与运维验证使用，不通过外部 API 暴露客户端 IP。
    long trackedClientCount() {
        requestTimestamps.cleanUp();
        return requestTimestamps.estimatedSize();
    }
}
