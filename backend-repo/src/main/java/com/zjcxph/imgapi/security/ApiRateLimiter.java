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
     * <p>
     * 检查与计数在同一 synchronized 块内完成，保证原子性，
     * 避免高并发下多线程同时通过检查导致实际通过数超过 MAX_REQUESTS。
     * </p>
     *
     * @param clientIp 客户端 IP
     * @return true 表示允许通过，false 表示已被限流
     */
    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return true;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;

        // computeIfAbsent 保证 LinkedList 只创建一次，后续 synchronized 基于同一对象
        LinkedList<Long> timestamps = requestTimestamps.computeIfAbsent(clientIp, k -> new LinkedList<>());

        synchronized (timestamps) {
            // 清理过期时间戳
            timestamps.removeIf(ts -> ts < cutoff);
            // 原子检查：超限则拒绝
            if (timestamps.size() >= MAX_REQUESTS) {
                logger.warn("API rate limit exceeded for IP: {}", clientIp);
                return false;
            }
            // 记录本次请求
            timestamps.add(now);
            return true;
        }
    }
}
