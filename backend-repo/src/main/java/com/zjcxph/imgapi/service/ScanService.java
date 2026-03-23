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

    // 新增
    Scan create(Scan scan);

    // 删除
    boolean deleteById(Integer id);

    // 更新
    Scan update(Scan scan);

    // 查询所有
    List<Scan> findAll();

    // 根据 ID 查询
    Scan findById(Integer id);

    // 根据病案号查询
    List<Scan> findByBah(String bah);

    // 根据病人序号查询
    List<Scan> findByBrxh(String brxh);

    // 分页查询
    List<Scan> findAllWithPagination(int page, int size);

    // 根据条件动态查询
    List<Scan> findByCondition(ScanRequest request);
}