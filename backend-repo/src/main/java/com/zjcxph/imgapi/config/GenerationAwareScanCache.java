package com.zjcxph.imgapi.config;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.function.Predicate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.springframework.cache.Cache.ValueRetrievalException;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;

/**
 * Preserves the configured Caffeine cache and its keys while fencing stale
 * puts from Spring Cacheable lookups across invalidation.
 * The database call never holds this monitor.
 */
public final class GenerationAwareScanCache extends CaffeineCache {
    private long generation;
    private static final int MAX_PENDING = 1000;
    private record LoadKey(long generation, Object key) {}
    private final Map<LoadKey, CompletableFuture<Object>> pending = new HashMap<>();

    public GenerationAwareScanCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache) {
        super(name, nativeCache);
    }

    /** Each Cacheable invocation gets a view of the same native cache. */
    public synchronized Cache lookupView() {
        long observedGeneration = generation;
        return new CaffeineCache(getName(), getNativeCache()) {
            @Override
            public <T> T get(Object key, Callable<T> loader) {
                return load(key, loader);
            }

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

    @SuppressWarnings("unchecked")
    private <T> T load(Object key, Callable<T> loader) {
        LoadKey loadKey;
        CompletableFuture<Object> flight;
        boolean leader;
        synchronized (this) {
            Object cached = getNativeCache().getIfPresent(key);
            if (cached != null) return (T) fromStoreValue(cached);
            loadKey = new LoadKey(generation, key);
            flight = pending.get(loadKey);
            leader = flight == null;
            if (leader && pending.size() < MAX_PENDING) {
                flight = new CompletableFuture<>();
                pending.put(loadKey, flight);
            }
        }
        if (!leader) {
            try {
                return (T) flight.get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ValueRetrievalException(key, loader, interrupted);
            } catch (ExecutionException failure) {
                throw new ValueRetrievalException(key, loader, failure.getCause());
            }
        }
        // Above the bounded registry, preserve normal query semantics without tracking new keys.
        try {
            T value = loader.call();
            synchronized (this) {
                if (generation == loadKey.generation() && cacheable(value)) {
                    super.put(key, value);
                }
                if (flight != null) {
                    flight.complete(value);
                    pending.remove(loadKey, flight);
                }
            }
            return value;
        } catch (Throwable failure) {
            synchronized (this) {
                if (flight != null) {
                    flight.completeExceptionally(failure);
                    pending.remove(loadKey, flight);
                }
            }
            throw new ValueRetrievalException(key, loader, failure);
        }
    }

    private boolean cacheable(Object value) {
        return value != null
                && (!(value instanceof List<?> list) || !list.isEmpty())
                && (!(value instanceof ArchiveLookupResult lookup) || lookup.resultCount() != 0);
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
