package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.mapper.ArchiveRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveRecordServiceImplTest {

    @Mock
    private ArchiveRecordMapper archiveRecordMapper;

    @InjectMocks
    private ArchiveRecordServiceImpl archiveRecordService;

    @Test
    void findByCodeNormalizesBusinessCodes() {
        ArchiveRecord expected = new ArchiveRecord();
        expected.setId(10L);
        when(archiveRecordMapper.findByCode("00000123", "00000456")).thenReturn(expected);

        ArchiveRecord result = archiveRecordService.findByCode("123", "456");

        assertSame(expected, result);
        verify(archiveRecordMapper).findByCode("00000123", "00000456");
    }

    @Test
    void findByCodeRejectsMissingCodes() {
        ArchiveRecord result = archiveRecordService.findByCode(" ", null);

        assertNull(result);
        verify(archiveRecordMapper, never()).findByCode(null, null);
    }

    @Test
    void resolveDoesNotCreateArchiveWithoutSjh() {
        archiveRecordService.resolveArchiveId("123", null, true);

        verify(archiveRecordMapper).resolveArchiveId("00000123", null, false);
    }
}
