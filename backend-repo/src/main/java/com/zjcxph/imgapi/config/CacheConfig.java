package com.zjcxph.imgapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.ObjectProvider;
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

    /** 按病案号查询扫描记录的缓存。 */
    public static final String SCAN_BY_BAH = "scanByBah";
    /** 按上架号查询扫描记录的缓存。 */
    public static final String SCAN_BY_CODE = "scanByCode";
    /** 病案影像定位结果缓存（病案号维度）。 */
    public static final String SCAN_LOOKUP_BY_BAH = "scanLookupByBah";
    /** 病案影像定位结果缓存（上架号维度）。 */
    public static final String SCAN_LOOKUP_BY_CODE = "scanLookupByCode";
    /** 单条扫描记录缓存。 */
    public static final String SCAN_BY_ID = "scanById";

    /**
     * 全部依赖 {@code mr_scan} 行内容的查询缓存。
     *
     * <p>任何绕过 {@code ScanServiceImpl} 写路径的数据导入都必须整表清理这批缓存，
     * 否则已查询过的病案号会在 TTL 内继续返回导入前的数据。</p>
     */
    public static final List<String> SCAN_QUERY_CACHES = List.of(
            SCAN_BY_BAH,
            SCAN_BY_CODE,
            SCAN_LOOKUP_BY_BAH,
            SCAN_LOOKUP_BY_CODE,
            SCAN_BY_ID
    );

    private static final int INITIAL_CAPACITY = 100;
    private static final int MAXIMUM_SIZE = 1000;

    @Bean
    public CacheManager cacheManager(ObjectProvider<OssProperties> ossProperties,
                                     ObjectProvider<Ticker> ticker) {
        // OSS properties are startup configuration. Restarting after a change creates a fresh cache.
        // Keep the existing ten-minute budget, but never consume the final 30 seconds of a signature.
        int signatureSeconds = ossProperties.getIfAvailable(OssProperties::new).getUrlExpireSeconds();
        long signatureCacheSeconds = Math.max(0L, Math.min(600L, (long) signatureSeconds - 30L));
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache(SCAN_BY_BAH, 1, TimeUnit.DAYS),
                buildCache(SCAN_BY_CODE, 1, TimeUnit.DAYS),
                buildCache(SCAN_LOOKUP_BY_BAH, 1, TimeUnit.DAYS),
                buildCache(SCAN_LOOKUP_BY_CODE, 1, TimeUnit.DAYS),
                buildCache(SCAN_BY_ID, 10, TimeUnit.MINUTES),
                new CaffeineCache("ossSignedUrl", Caffeine.newBuilder()
                        .initialCapacity(INITIAL_CAPACITY)
                        .maximumSize(MAXIMUM_SIZE)
                        .expireAfterWrite(signatureCacheSeconds, TimeUnit.SECONDS)
                        .ticker(ticker.getIfAvailable(Ticker::systemTicker))
                        .recordStats()
                        .build())
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
