package com.zjcxph.imgapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT Token 内存黑名单 — 用于登出后使 Token 失效。
 * <p>
 * 条目在 Token 自然过期后由定时任务清理（每小时一次），
 * 服务重启后黑名单会丢失（Token 最长存活 24h，可接受）。
 * </p>
 */
@Component
public class TokenBlacklist {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklist.class);
    private final ConcurrentHashMap<String, Long> revokedTokens = new ConcurrentHashMap<>();

    /**
     * 将 Token 加入黑名单。
     * @param jti Token 唯一标识
     * @param expiryTimestamp Token 过期时间戳（毫秒）
     */
    public void revoke(String jti, long expiryTimestamp) {
        revokedTokens.put(jti, expiryTimestamp);
        logger.info("Token revoked: jti={}", jti);
    }

    /**
     * 检查 Token 是否已被撤销。
     */
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Long expiry = revokedTokens.get(jti);
        if (expiry == null) {
            return false;
        }
        // 已过期的条目虽然还在 map 里，但 token 本身已无法解析，返回 false
        if (System.currentTimeMillis() > expiry) {
            revokedTokens.remove(jti);
            return false;
        }
        return true;
    }

    /**
     * 每小时清理已过期的黑名单条目。
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = revokedTokens.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Token blacklist cleanup: removed {} expired entries", removed);
        }
    }
}
