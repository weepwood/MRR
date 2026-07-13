package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.ArchiveBoxRecordRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxGroupDTO;
import com.zjcxph.imgapi.dto.resp.ArchiveBoxSummaryDTO;
import com.zjcxph.imgapi.entity.ArchiveBoxRecord;

import java.util.List;

public interface ArchiveBoxRecordService {

    ArchiveBoxRecord create(ArchiveBoxRecordRequest request);

    ArchiveBoxRecord update(Long id, ArchiveBoxRecordRequest request);

    boolean delete(Long id);

    ArchiveBoxRecord findById(Long id);

    List<ArchiveBoxRecord> findPage(
            int page,
            int size,
            String keyword,
            String bah,
            String sjh,
            String boxNo,
            String status,
            String sortBy,
            String sortOrder
    );

    long countPage(String keyword, String bah, String sjh, String boxNo, String status);

    List<ArchiveBoxRecord> findByRecordCode(String code);

    List<ArchiveBoxRecord> findByBoxNo(String boxNo);

    ArchiveBoxSummaryDTO getSummary();

    List<ArchiveBoxGroupDTO> findBoxGroups(int page, int size, String keyword);

    long countBoxGroups(String keyword);
}
