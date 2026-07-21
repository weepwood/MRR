package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveLookupResult;
import com.zjcxph.imgapi.dto.resp.CursorPageResult;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;

import java.util.List;

public interface ScanService {
    ArchiveLookupResult getImageLookupByBAH(String normalizedCode, String searchCode);

    ArchiveLookupResult getImageLookupByCode(
            String normalizedBah,
            String bahSearchCode,
            String normalizedSjh,
            String sjhSearchCode
    );

    List<Scan> getImageListByBAH(String normalizedCode, String searchCode);

    List<Scan> getImageListByCode(
            String normalizedBah,
            String bahSearchCode,
            String normalizedSjh,
            String sjhSearchCode
    );

    List<PathDO> getImagePathList(List<String> ids);

    List<Scan> findActiveByIds(List<Integer> ids);

    int updateImageType(Integer id, Integer type);

    Scan create(Scan scan);

    boolean softDeleteById(Integer id);

    Scan update(Scan scan);

    List<Scan> findAll(int limit);

    CursorPageResult<Scan> findAfterId(Integer afterId, int size);

    Scan findById(Integer id);

    List<Scan> findByBah(String bah);

    List<Scan> findByBrxh(String brxh);

    List<Scan> findAllWithPagination(int page, int size);

    List<Scan> findByCondition(ScanRequest request, int limit);

    List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size);

    long countByCondition(ScanRequest request);
}
