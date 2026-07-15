package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ArchiveRecordMapper;
import com.zjcxph.imgapi.service.ArchiveRecordService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ArchiveRecordServiceImpl implements ArchiveRecordService {

    private final ArchiveRecordMapper archiveRecordMapper;

    public ArchiveRecordServiceImpl(ArchiveRecordMapper archiveRecordMapper) {
        this.archiveRecordMapper = archiveRecordMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ArchiveRecord findById(Long id) {
        if (id == null || id <= 0) {
            return null;
        }
        return archiveRecordMapper.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchiveRecord findByCode(String bah, String sjh) {
        String normalizedBah = normalizeNullable(bah);
        String normalizedSjh = normalizeNullable(sjh);
        if (normalizedBah == null && normalizedSjh == null) {
            return null;
        }
        return archiveRecordMapper.findByCode(normalizedBah, normalizedSjh);
    }

    @Override
    @Transactional
    public Long resolveArchiveId(String bah, String sjh, boolean createWhenSjhPresent) {
        String normalizedBah = normalizeNullable(bah);
        String normalizedSjh = normalizeNullable(sjh);
        if (normalizedBah == null && normalizedSjh == null) {
            return null;
        }
        return archiveRecordMapper.resolveArchiveId(
                normalizedBah,
                normalizedSjh,
                createWhenSjhPresent && normalizedSjh != null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scan> findScans(Long archiveId) {
        if (archiveId == null || archiveId <= 0) {
            return List.of();
        }
        return archiveRecordMapper.findScans(archiveId);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = MedicalRecordCodeUtils.normalizeOrEmpty(value);
        return normalized.isBlank() ? null : normalized;
    }
}
