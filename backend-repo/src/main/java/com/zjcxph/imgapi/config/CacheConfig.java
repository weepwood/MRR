package com.zjcxph.imgapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置类。
 *
 * <p>档案搜索结果适合较长时间复用；按 ID 查询和 OSS 签名 URL 继续使用较短缓存，
 * 避免签名 URL 在缓存中存活时间超过其有效期。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final int INITIAL_CAPACITY = 100;
    private static final int MAXIMUM_SIZE = 1000;

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache("scanByBah", 1, TimeUnit.DAYS),
                buildCache("scanByCode", 1, TimeUnit.DAYS),
                buildCache("scanById", 10, TimeUnit.MINUTES),
                buildCache("ossSignedUrl", 10, TimeUnit.MINUTES)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit timeUnit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .initialCapacity(INITIAL_CAPACITY)
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(duration, timeUnit)
                .recordStats()
                .build());
    }
}
