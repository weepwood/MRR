package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.dto.req.ScanRequest;

import java.nio.file.Path;
import java.util.List;

public interface ScanService {
    List<Scan> getImageListByBAH(String normalizedCode, String searchCode);

    List<Scan> getImageListByCode(
            String normalizedBah,
            String bahSearchCode,
            String normalizedSjh,
            String sjhSearchCode
    );

    Path getImagePath(String bah);

    java.io.File createZipForBAH(String bah) throws java.io.IOException;

    java.io.File createZipForCode(String bah, String sjh) throws java.io.IOException;

    List<PathDO> getImagePathList(List<String> ids);

    int updateImageType(Integer id, Integer type);

    Scan create(Scan scan);

    boolean softDeleteById(Integer id);

    Scan update(Scan scan);

    List<Scan> findAll();

    Scan findById(Integer id);

    List<Scan> findByBah(String bah);

    List<Scan> findByBrxh(String brxh);

    List<Scan> findAllWithPagination(int page, int size);

    List<Scan> findByCondition(ScanRequest request);

    List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size);

    long countByCondition(ScanRequest request);
}
