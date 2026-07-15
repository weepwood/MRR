package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.ArchiveRecord;
import com.zjcxph.imgapi.entity.Scan;

import java.util.List;

public interface ArchiveRecordService {
    ArchiveRecord findById(Long id);

    ArchiveRecord findByCode(String bah, String sjh);

    Long resolveArchiveId(String bah, String sjh, boolean createWhenSjhPresent);

    List<Scan> findScans(Long archiveId);
}
