package com.zjcxph.imgapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 API 限流器 — 基于内存的滑动窗口。
 * <p>
 * 按 IP 维度限制敏感接口的调用频率，防止滥用和暴力枚举。
 * 服务重启后计数归零（与 LoginRateLimiter 一致，可接受）。
 * </p>
 */
@Component
public class ApiRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(ApiRateLimiter.class);

    private final ConcurrentHashMap<String, LinkedList<Long>> requestTimestamps = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS = 30;
    private static final long WINDOW_MS = 60 * 1000;

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
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            timestamps.removeIf(ts -> ts < cutoff);
            return timestamps.size() >= MAX_REQUESTS;
        }
    }

    /**
     * 记录一次请求。如果超出限制则拒绝记录并返回 false。
     *
     * @param clientIp 客户端 IP
     * @return true 表示允许通过，false 表示已被限流
     */
    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }
        if (isBlocked(clientIp)) {
            logger.warn("API rate limit exceeded for IP: {}", clientIp);
            return false;
        }
        requestTimestamps.compute(clientIp, (k, v) -> {
            if (v == null) {
                v = new LinkedList<>();
            }
            synchronized (v) {
                v.add(System.currentTimeMillis());
            }
            return v;
        });
        return true;
    }
}
