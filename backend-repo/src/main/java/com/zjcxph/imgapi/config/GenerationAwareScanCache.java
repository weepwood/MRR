package com.zjcxph.imgapi.config;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.function.Predicate;

/**
 * Preserves the configured Caffeine cache and its keys while fencing stale
 * puts from non-synchronized Spring Cacheable lookups across invalidation.
 * The database call never holds this monitor.
 */
public final class GenerationAwareScanCache extends CaffeineCache {
    private long generation;

    public GenerationAwareScanCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache) {
        super(name, nativeCache);
    }

    /** Each Cacheable invocation gets a view of the same native cache. */
    public synchronized Cache lookupView() {
        long observedGeneration = generation;
        return new CaffeineCache(getName(), getNativeCache()) {
            @Override
            public void put(Object key, Object value) {
                synchronized (GenerationAwareScanCache.this) {
                    if (observedGeneration == generation) {
                        super.put(key, value);
                    }
                }
            }
        };
    }

    /** Fence all pending puts, but remove only already cached matching results. */
    public synchronized void invalidateMatching(Predicate<Object> affected) {
        generation++;
        var entries = getNativeCache().asMap();
        entries.forEach((key, value) -> {
            if (affected.test(value)) {
                entries.remove(key, value);
            }
        });
    }

    @Override
    public synchronized void evict(Object key) {
        generation++;
        super.evict(key);
    }

    @Override
    public synchronized boolean evictIfPresent(Object key) {
        generation++;
        return super.evictIfPresent(key);
    }

    @Override
    public synchronized void clear() {
        generation++;
        super.clear();
    }

    @Override
    public synchronized boolean invalidate() {
        generation++;
        return super.invalidate();
    }
}
