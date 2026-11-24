package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.CacheConfig;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;

import static com.zjcxph.imgapi.dto.resp.ArchiveLookupResult.Strategy.ARCHIVE_ID_EXACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScanService 搜索缓存测试")
class ScanServiceCacheTest {

    private AnnotationConfigApplicationContext context;
    private ScanMapper scanMapper;
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        scanMapper = context.getBean(ScanMapper.class);
        scanService = context.getBean(ScanService.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("相同病案号和上架号组合只查询数据库一次")
    void cachesArchiveSearchResult() {
        Scan scan = new Scan();
        scan.setId(1);
        scan.setBah("00789508");
        scan.setSjh("00000123");
        when(scanMapper.findByCode("00789508", "789508", "00000123", "123"))
                .thenReturn(List.of(scan));

        List<Scan> first = scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        List<Scan> second = scanService.getImageListByCode("00789508", "789508", "00000123", "123");

        assertThat(first).containsExactly(scan);
        assertThat(second).containsExactly(scan);
        verify(scanMapper, times(1)).findByCode("00789508", "789508", "00000123", "123");
    }

    @Test
    @DisplayName("查询策略与影像结果一起缓存")
    void cachesArchiveLookupMetadata() {
        Scan scan = new Scan();
        scan.setId(1);
        scan.setArchiveId(42L);
        when(scanMapper.resolveArchiveId("00789508", "")).thenReturn(42L);
        when(scanMapper.findActiveByArchiveId(42L)).thenReturn(List.of(scan));

        ArchiveLookupResult first = scanService.getImageLookupByCode("00789508", "789508", "", "");
        ArchiveLookupResult second = scanService.getImageLookupByCode("00789508", "789508", "", "");

        assertThat(first.strategy()).isEqualTo(ARCHIVE_ID_EXACT);
        assertThat(second.strategy()).isEqualTo(ARCHIVE_ID_EXACT);
        assertThat(second.archiveId()).isEqualTo(42L);
        verify(scanMapper, times(1)).resolveArchiveId("00789508", "");
        verify(scanMapper, times(1)).findActiveByArchiveId(42L);
    }

    @Test
    @DisplayName("空查询结果不缓存，避免导入新数据后长期不可见")
    void doesNotCacheEmptyArchiveSearchResult() {
        when(scanMapper.findByCode("00789508", "789508", "00000123", "123"))
                .thenReturn(List.of());

        scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        scanService.getImageListByCode("00789508", "789508", "00000123", "123");

        verify(scanMapper, times(2)).findByCode("00789508", "789508", "00000123", "123");
    }

    @Test
    @DisplayName("修改图片类型后清理组合搜索缓存")
    void evictsArchiveSearchCacheAfterImageTypeUpdate() {
        Scan scan = new Scan();
        scan.setId(1);
        when(scanMapper.findByCode("00789508", "789508", "00000123", "123"))
                .thenReturn(List.of(scan));
        when(context.getBean(com.zjcxph.imgapi.service.ImageTypeService.class).manual(1, 2)).thenReturn(1);

        scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        scanService.updateImageType(1, 2);
        scanService.getImageListByCode("00789508", "789508", "00000123", "123");

        verify(scanMapper, times(2)).findByCode("00789508", "789508", "00000123", "123");
    }

    @Test
    void inFlightIdLookupCannotRepopulateAfterEviction() throws Exception {
        Scan old = new Scan();
        old.setId(1);
        Scan fresh = new Scan();
        fresh.setId(1);
        fresh.setOssUrl("synthetic-object");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scanMapper.findById(1)).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("query not released");
            return old;
        }).thenReturn(fresh);
        var cache = context.getBean(CacheManager.class).getCache(CacheConfig.SCAN_BY_ID);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> scanService.findById(1));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            cache.evict(1);
            release.countDown();
            assertThat(pending.get(5, TimeUnit.SECONDS)).isSameAs(old);
            assertThat(scanService.findById(1)).isSameAs(fresh);
            assertThat(scanService.findById(1)).isSameAs(fresh);
            verify(scanMapper, times(2)).findById(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inFlightArchiveLookupCannotRepopulateAfterClear() throws Exception {
        Scan old = new Scan();
        old.setId(1);
        Scan fresh = new Scan();
        fresh.setId(1);
        fresh.setOssUrl("synthetic-object");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scanMapper.findByCode("1", "1", "2", "2")).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("query not released");
            return List.of(old);
        }).thenReturn(List.of(fresh));
        var cache = context.getBean(CacheManager.class).getCache(CacheConfig.SCAN_BY_CODE);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> scanService.getImageListByCode("1", "1", "2", "2"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            cache.clear();
            release.countDown();
            assertThat(pending.get(5, TimeUnit.SECONDS)).containsExactly(old);
            assertThat(scanService.getImageListByCode("1", "1", "2", "2")).containsExactly(fresh);
            assertThat(scanService.getImageListByCode("1", "1", "2", "2")).containsExactly(fresh);
            verify(scanMapper, times(2)).findByCode("1", "1", "2", "2");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void recordsConcurrentColdMissAndWarmHitBaseline() throws Exception {
        final int workers = 8;
        var scan = new Scan();
        scan.setId(1);
        var started = new CountDownLatch(workers);
        var release = new CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(scanMapper.findById(1)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("cold miss not released");
            return scan;
        });
        var executor = Executors.newFixedThreadPool(workers);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Scan>>();
            for (int i = 0; i < workers; i++) tasks.add(executor.submit(() -> scanService.findById(1)));
            // Measure the current non-sync cache. Future coalescing is allowed to reduce loads.
            started.await(1, TimeUnit.SECONDS);
            release.countDown();
            for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isSameAs(scan);
            int coldLoads = calls.get();
            assertThat(coldLoads).isEqualTo(1);
            tasks.clear();
            for (int i = 0; i < workers; i++) tasks.add(executor.submit(() -> scanService.findById(1)));
            for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isSameAs(scan);
            assertThat(calls.get()).isEqualTo(coldLoads);
            System.out.printf("SYNTHETIC_CACHE_BASELINE concurrency=%d cold_mapper_calls=%d warm_mapper_calls=%d%n",
                    workers, coldLoads, calls.get() - coldLoads);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void newGenerationDoesNotJoinBlockedOldLoad() throws Exception {
        var old = new Scan(); old.setId(1);
        var fresh = new Scan(); fresh.setId(1); fresh.setOssUrl("synthetic-fresh");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(scanMapper.findById(1)).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("not released");
            return old;
        }).thenReturn(fresh);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> scanService.findById(1));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            context.getBean(CacheManager.class).getCache(CacheConfig.SCAN_BY_ID).evict(1);
            assertThat(executor.submit(() -> scanService.findById(1)).get(2, TimeUnit.SECONDS)).isSameAs(fresh);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(old);
            assertThat(scanService.findById(1)).isSameAs(fresh);
            verify(scanMapper, times(2)).findById(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedAndNullLoadsAreNotRetained() {
        var fresh = new Scan(); fresh.setId(1);
        when(scanMapper.findById(1)).thenThrow(new IllegalStateException("synthetic failure"))
                .thenReturn(null).thenReturn(fresh);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scanService.findById(1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(scanService.findById(1)).isNull();
        assertThat(scanService.findById(1)).isSameAs(fresh);
        assertThat(scanService.findById(1)).isSameAs(fresh);
        verify(scanMapper, times(3)).findById(1);
    }

    @Test
    void blockedKeyDoesNotBlockAnotherKey() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var a = new Scan(); a.setId(1);
        var b = new Scan(); b.setId(2);
        when(scanMapper.findById(1)).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("not released");
            return a;
        });
        when(scanMapper.findById(2)).thenReturn(b);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> scanService.findById(1));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.submit(() -> scanService.findById(2)).get(2, TimeUnit.SECONDS)).isSameAs(b);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(a);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Configuration
    @Import(CacheConfig.class)
    static class TestConfig {

        @Bean
        ScanMapper scanMapper() {
            return mock(ScanMapper.class);
        }

        @Bean
        com.zjcxph.imgapi.service.ImageTypeService imageTypes() {
            return mock(com.zjcxph.imgapi.service.ImageTypeService.class);
        }

        @Bean
        ScanService scanService(ScanMapper scanMapper,com.zjcxph.imgapi.service.ImageTypeService imageTypes) {
            return new ScanServiceImpl(scanMapper,imageTypes);
        }
    }
}
