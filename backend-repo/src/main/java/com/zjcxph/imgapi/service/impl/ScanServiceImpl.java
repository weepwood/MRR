package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ScanServiceImpl implements ScanService {

    private final ScanMapper scanMapper;
    private final ImageProperties imageProperties;

    public ScanServiceImpl(ScanMapper scanMapper, ImageProperties imageProperties) {
        this.scanMapper = scanMapper;
        this.imageProperties = imageProperties;
    }

    @Override
    @Cacheable(value = "scanByBah", key = "#bah", unless = "#result == null || #result.isEmpty()")
    public List<Scan> getImageListByBAH(String bah) {
        return scanMapper.findBAH(bah);
    }

    @Override
    public Path getImagePath(String bah) {
        List<Scan> baData = scanMapper.findBAH(bah);
        if (baData.isEmpty()) {
            return null;
        }
        Scan scan = baData.get(0);
        String folderPath = scan.getFolder();
        String brxh = scan.getBrxh();
        if (folderPath == null || folderPath.length() < 5 || brxh == null) {
            return null;
        }
        String parentFolder = folderPath.substring(0, 5);
        String folderName = brxh + "-" + bah;
        return Paths.get(imageProperties.getBasePath(), parentFolder, folderPath, folderName);
        
    }

    @Override
    public java.io.File createZipForBAH(String bah) throws java.io.IOException {
        Path imagePath = getImagePath(bah);
        if (imagePath == null) {
            throw new com.zjcxph.imgapi.exception.BusinessException(404, "未找到该病案号的图片路径");
        }
        
        String fileNameTemp = bah + ".temp";
        String zipPath = "./temp/" + fileNameTemp;
        
        com.zjcxph.imgapi.utils.ZipUtil.zipJpgFiles(imagePath.toString(), zipPath);
        return new java.io.File(zipPath);
    }

    @Override
    public List<PathDO> getImagePathList(List<String> ids) {
        return scanMapper.getImagePathList(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", key = "#id")
    })
    public int updateImageType(Integer id, Integer type) {
        return scanMapper.updateImageType(id, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "scanByBah", allEntries = true)
    public Scan create(Scan scan) {
        if (scanMapper.insert(scan) > 0) {
            return scan;
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", key = "#id")
    })
    public boolean softDeleteById(Integer id) {
        return scanMapper.softDeleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "scanByBah", allEntries = true),
            @CacheEvict(value = "scanById", key = "#scan.id")
    })
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
    @Cacheable(value = "scanById", key = "#id", unless = "#result == null")
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
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return scanMapper.findAllWithPagination(offset, size);
    }

    @Override
    public List<Scan> findByCondition(ScanRequest request) {
        return scanMapper.findByCondition(request);
    }

    @Override
    public List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return scanMapper.findByConditionWithPagination(request, offset, size);
    }

    @Override
    public long countByCondition(ScanRequest request) {
        return scanMapper.countByCondition(request);
    }
}
