package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ScanService 病案主档快速查询测试")
class ScanServiceArchiveLookupTest {

    private ScanMapper scanMapper;
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        scanMapper = mock(ScanMapper.class);
        scanService = new ScanServiceImpl(scanMapper);
    }

    @Test
    @DisplayName("解析到 archive_id 且存在影像时只走快速路径")
    void usesArchiveIdFastPathWhenLinkedScansExist() {
        Scan scan = scan(1, 42L, "fast.jpg");
        when(scanMapper.resolveArchiveId("00789508", "")).thenReturn(42L);
        when(scanMapper.findActiveByArchiveId(42L)).thenReturn(List.of(scan));

        List<Scan> result = scanService.getImageListByCode("00789508", "789508", "", "");

        assertThat(result).containsExactly(scan);
        verify(scanMapper).resolveArchiveId("00789508", "");
        verify(scanMapper, never()).resolveArchiveIdBySearchCode("789508", "");
        verify(scanMapper).findActiveByArchiveId(42L);
        verify(scanMapper, never()).findByCode("00789508", "789508", "", "");
    }

    @Test
    @DisplayName("旧病案号图片接口同样优先使用 archive_id")
    void usesArchiveIdFastPathForBahEndpoint() {
        Scan scan = scan(2, 42L, "bah-endpoint.jpg");
        when(scanMapper.resolveArchiveId("00789508", "")).thenReturn(42L);
        when(scanMapper.findActiveByArchiveId(42L)).thenReturn(List.of(scan));

        List<Scan> result = scanService.getImageListByBAH("00789508", "789508");

        assertThat(result).containsExactly(scan);
        verify(scanMapper).findActiveByArchiveId(42L);
        verify(scanMapper, never()).findBAH("00789508", "789508");
    }

    @Test
    @DisplayName("主档编号补零格式不一致时仍可进入快速路径")
    void usesCompatibilityResolverForMixedCodeFormats() {
        Scan scan = scan(3, 42L, "mixed-format.jpg");
        when(scanMapper.resolveArchiveId("00000123", "")).thenReturn(null);
        when(scanMapper.resolveArchiveIdBySearchCode("123", "")).thenReturn(42L);
        when(scanMapper.findActiveByArchiveId(42L)).thenReturn(List.of(scan));

        List<Scan> result = scanService.getImageListByCode("00000123", "123", "", "");

        assertThat(result).containsExactly(scan);
        verify(scanMapper).resolveArchiveIdBySearchCode("123", "");
        verify(scanMapper).findActiveByArchiveId(42L);
        verify(scanMapper, never()).findByCode("00000123", "123", "", "");
    }

    @Test
    @DisplayName("主档不存在时回退原有病案号和上架号查询")
    void fallsBackWhenArchiveCannotBeResolved() {
        Scan scan = scan(4, null, "legacy.jpg");
        when(scanMapper.resolveArchiveId("00789508", "")).thenReturn(null);
        when(scanMapper.resolveArchiveIdBySearchCode("789508", "")).thenReturn(null);
        when(scanMapper.findByCode("00789508", "789508", "", ""))
                .thenReturn(List.of(scan));

        List<Scan> result = scanService.getImageListByCode("00789508", "789508", "", "");

        assertThat(result).containsExactly(scan);
        verify(scanMapper).resolveArchiveIdBySearchCode("789508", "");
        verify(scanMapper, never()).findActiveByArchiveId(anyLong());
        verify(scanMapper).findByCode("00789508", "789508", "", "");
    }

    @Test
    @DisplayName("主档存在但尚未关联影像时回退原有查询")
    void fallsBackWhenArchiveHasNoLinkedScans() {
        Scan scan = scan(5, null, "unlinked.jpg");
        when(scanMapper.resolveArchiveId("", "00000123")).thenReturn(42L);
        when(scanMapper.findActiveByArchiveId(42L)).thenReturn(List.of());
        when(scanMapper.findByCode("", "", "00000123", "123"))
                .thenReturn(List.of(scan));

        List<Scan> result = scanService.getImageListByCode("", "", "00000123", "123");

        assertThat(result).containsExactly(scan);
        verify(scanMapper).findActiveByArchiveId(42L);
        verify(scanMapper).findByCode("", "", "00000123", "123");
    }

    @Test
    @DisplayName("快速路径数据库异常时不使用旧查询掩盖故障")
    void doesNotFallbackWhenArchiveLookupFails() {
        when(scanMapper.resolveArchiveId("00789508", ""))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() ->
                scanService.getImageListByCode("00789508", "789508", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(scanMapper, never()).resolveArchiveIdBySearchCode("789508", "");
        verify(scanMapper, never()).findByCode("00789508", "789508", "", "");
    }

    private Scan scan(Integer id, Long archiveId, String filename) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setArchiveId(archiveId);
        scan.setFilename(filename);
        scan.setUploadFlag(1);
        return scan;
    }
}
