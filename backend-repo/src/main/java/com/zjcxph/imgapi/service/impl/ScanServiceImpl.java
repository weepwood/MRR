package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.pojo.Scan;
import com.zjcxph.imgapi.service.ScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.zjcxph.imgapi.pojo.PathDO;
import com.zjcxph.imgapi.pojo.ScanRequest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ScanServiceImpl implements ScanService {

    @Value("${image.basePath}")
    private String basePath;


    private final ScanMapper scanMapper;

    @Autowired
    public ScanServiceImpl(ScanMapper scanMapper) {
        this.scanMapper = scanMapper;
    }

    @Override
    public List<Scan> getImageListByBAH(String bah) {
        return scanMapper.findBAH(bah);
    }

    @Override
    public Path getImagePath(String bah) {
        List<Scan> baData = scanMapper.findBAH(bah);
        if (baData.isEmpty()) {
            return null;
        }
        // 文件夹地址 YY.MM.DD
        String folderPath = baData.get(0).getFolder();
        // 文件夹的父文件夹,目前截取前 5 位 YY.MM
        // 注意:如果调整文件夹结构,记得修改此处
        String parentFolder = folderPath.substring(0, 5);
        String brxh = baData.get(0).getBrxh();
        String folderName = brxh + "-" + bah;
        return Paths.get(basePath, parentFolder, folderPath, folderName);
        
    }

    @Override
    public List<PathDO> getImagePathList(List<String> ids) {
        return scanMapper.getImagePathList(ids);
    }

    @Override
    public int updateImageType(Integer id, Integer type) {
        return scanMapper.updateImageType(id, type);
    }

    @Override
    public Scan create(Scan scan) {
        if (scanMapper.insert(scan) > 0) {
            return scan;
        }
        return null;
    }

    @Override
    public boolean deleteById(Integer id) {
        return scanMapper.deleteById(id) > 0;
    }

    @Override
    public Scan update(Scan scan) {
        if (scanMapper.update(scan) > 0) {
            return scan;
        }
        return null;
    }

    @Override
    public List<Scan> findAll() {
        return scanMapper.findAll();
    }

    @Override
    public Scan findById(Integer id) {
        return scanMapper.findById(id);
    }

    @Override
    public List<Scan> findByBah(String bah) {
        return scanMapper.findByBah(bah);
    }

    @Override
    public List<Scan> findByBrxh(String brxh) {
        return scanMapper.findByBrxh(brxh);
    }

    @Override
    public List<Scan> findAllWithPagination(int page, int size) {
        int offset = (page - 1) * size;
        return scanMapper.findAllWithPagination(offset, size);
    }

    @Override
    public List<Scan> findByCondition(ScanRequest request) {
        return scanMapper.findByCondition(request);
    }

    @Override
    public List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size) {
        int offset = (page - 1) * size;
        return scanMapper.findByConditionWithPagination(request, offset, size);
    }

    @Override
    public long countByCondition(ScanRequest request) {
        return scanMapper.countByCondition(request);
    }
}
