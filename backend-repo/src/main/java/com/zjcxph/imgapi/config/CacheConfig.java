package com.zjcxph.imgapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 缓存配置类
 * 配置本地缓存以提升热点数据访问性能
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置缓存管理器
     * 定义不同缓存的过期策略
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 设置Caffeine配置
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)           // 初始容量
                .maximumSize(1000)              // 最大缓存条目数
                .expireAfterWrite(10, TimeUnit.MINUTES)  // 写入后10分钟过期
                .recordStats());                // 记录统计信息

        // 定义缓存名称
        cacheManager.setCacheNames(java.util.List.of(
                "scanByBah",           // 病案号查询缓存
                "scanByCode",          // 病案号/上架号组合查询缓存
                "scanById",            // ID查询缓存
                "ossSignedUrl"         // OSS签名URL缓存
        ));

        return cacheManager;
    }
}
