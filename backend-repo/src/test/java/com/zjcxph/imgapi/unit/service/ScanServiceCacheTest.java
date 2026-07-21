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
        when(scanMapper.updateImageType(1, 2)).thenReturn(1);

        scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        scanService.getImageListByCode("00789508", "789508", "00000123", "123");
        scanService.updateImageType(1, 2);
        scanService.getImageListByCode("00789508", "789508", "00000123", "123");

        verify(scanMapper, times(2)).findByCode("00789508", "789508", "00000123", "123");
    }

    @Configuration
    @Import(CacheConfig.class)
    static class TestConfig {

        @Bean
        ScanMapper scanMapper() {
            return mock(ScanMapper.class);
        }

        @Bean
        ScanService scanService(ScanMapper scanMapper) {
            return new ScanServiceImpl(scanMapper);
        }
    }
}
