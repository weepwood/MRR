package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.resp.ArchiveExportSummary;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.ImageTypeService;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArchiveExportSummaryServiceTest {
    final ScanMapper mapper = mock(ScanMapper.class);
    final ImageStorage storage = mock(ImageStorage.class);
    final ScanServiceImpl scans = new ScanServiceImpl(mapper, mock(ImageTypeService.class));
    final ArchiveExportServiceImpl exporter = new ArchiveExportServiceImpl(scans, storage);

    @BeforeEach
    void absentArchiveMatchesTheNullableDatabaseResult() {
        when(mapper.resolveArchiveId(anyString(), anyString())).thenReturn(null);
        when(mapper.resolveArchiveIdBySearchCode(anyString(), anyString())).thenReturn(null);
    }

    @Test
    void linkedPreflightDoesNotLoadOrResolveAnyImageRows() {
        when(mapper.resolveArchiveId("00123", "")).thenReturn(42L);
        when(mapper.summarizeActiveByArchiveId(42L)).thenReturn(new ArchiveExportSummary(5000, 6000));
        assertThat(exporter.summarizeArchive("00123", "")).isEqualTo(new ArchiveExportSummary(5000, 6000));
        verify(mapper, never()).findActiveByArchiveId(anyLong());
        verify(mapper, never()).findByCode(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(storage);
    }

    @Test
    void compatibleArchiveResolutionUsesTheSameSummaryFastPath() {
        when(mapper.resolveArchiveIdBySearchCode("123", "")).thenReturn(42L);
        when(mapper.summarizeActiveByArchiveId(42L)).thenReturn(new ArchiveExportSummary(2, 20));
        assertThat(exporter.summarizeArchive("00123", "")).isEqualTo(new ArchiveExportSummary(2, 20));
        verify(mapper).resolveArchiveId("00123", "");
        verify(mapper).countArchiveCandidates("123", "");
        verify(mapper, never()).findActiveByArchiveId(anyLong());
        verify(mapper, never()).findByCode(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(storage);
    }

    @Test
    void unlinkedRowsStillUseTheExistingFullFallback() {
        when(mapper.findByCode("00123", "123", "", "")).thenReturn(List.of(row(null, "123", "99")));
        assertThat(exporter.summarizeArchive("00123", "")).isEqualTo(new ArchiveExportSummary(1, 10));
        verify(mapper).findByCode("00123", "123", "", "");
        verifyNoInteractions(storage);
    }

    @Test
    void ambiguousArchiveCandidatesAreNotHiddenByPreflight() {
        when(mapper.countArchiveCandidates("123", "")).thenReturn(2);
        assertThatThrownBy(() -> exporter.summarizeArchive("00123", ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("歧义");
        verify(mapper, never()).summarizeActiveByArchiveId(anyLong());
        verify(mapper, never()).findByCode(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void anEmptyLinkedSummaryStillChecksTheOriginalCrossArchiveFallback() {
        when(mapper.resolveArchiveId("00123", "")).thenReturn(42L);
        when(mapper.summarizeActiveByArchiveId(42L)).thenReturn(new ArchiveExportSummary(0, 0));
        when(mapper.findByCode("00123", "123", "", "")).thenReturn(List.of(row(43L, "123", "99")));
        assertThatThrownBy(() -> exporter.summarizeArchive("00123", ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("歧义");
        verify(mapper).findActiveByArchiveId(42L);
        verify(mapper).findByCode("00123", "123", "", "");
    }

    @Test
    void multipleLegacyShelfCodesRemainAmbiguous() {
        when(mapper.findByCode("00123", "123", "", ""))
                .thenReturn(List.of(row(null, "123", "99"), row(null, "123", "100")));
        assertThatThrownBy(() -> exporter.summarizeArchive("00123", ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("歧义");
    }

    private Scan row(Long archiveId, String bah, String sjh) {
        Scan scan = new Scan();
        scan.setId(1); scan.setArchiveId(archiveId); scan.setBah(bah); scan.setSjh(sjh);
        scan.setFolder("synthetic"); scan.setFilename("synthetic.jpg"); scan.setFileSize(10L);
        return scan;
    }
}
