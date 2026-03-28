package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.pojo.PathDO;
import com.zjcxph.imgapi.pojo.Scan;
import com.zjcxph.imgapi.pojo.ScanRequest;

import java.nio.file.Path;
import java.util.List;

public interface ScanService {
    List<Scan> getImageListByBAH(String bah);

    Path getImagePath(String bah);

    List<PathDO> getImagePathList(List<String> ids);

    int updateImageType(Integer id, Integer type);

    Scan create(Scan scan);

    boolean deleteById(Integer id);

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
