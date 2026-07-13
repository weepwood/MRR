package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
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
    @Cacheable(value = "scanByBah", key = "#normalizedCode + ':' + #searchCode", unless = "#result == null || #result.isEmpty()")
    public List<Scan> getImageListByBAH(String normalizedCode, String searchCode) {
        return scanMapper.findBAH(normalizedCode, searchCode);
    }

    @Override
    public List<Scan> getImageListByCode(
            String normalizedBah,
            String bahSearchCode,
            String normalizedSjh,
            String sjhSearchCode
    ) {
        return scanMapper.findByCode(normalizedBah, bahSearchCode, normalizedSjh, sjhSearchCode);
    }

    @Override
    public Path getImagePath(String bah) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String searchCode = MedicalRecordCodeUtils.toSearchTerm(bah);
        List<Scan> baData = scanMapper.findBAH(normalizedBah, searchCode);
        return baData.isEmpty() ? null : resolveImagePath(baData.get(0));
    }

    @Override
    public java.io.File createZipForBAH(String bah) throws java.io.IOException {
        return createZipForCode(bah, "");
    }

    @Override
    public java.io.File createZipForCode(String bah, String sjh) throws java.io.IOException {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        List<Scan> matches = scanMapper.findByCode(
                normalizedBah,
                MedicalRecordCodeUtils.toSearchTerm(bah),
                normalizedSjh,
                MedicalRecordCodeUtils.toSearchTerm(sjh)
        );
        Path imagePath = matches.isEmpty() ? null : resolveImagePath(matches.get(0));
        if (imagePath == null) {
            throw new com.zjcxph.imgapi.exception.BusinessException(404, "未找到匹配档案的图片路径");
        }

        String archiveCode = normalizedBah + (normalizedSjh.isEmpty() ? "" : "-" + normalizedSjh);
        String zipPath = "./temp/" + archiveCode + ".temp";
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
        normalizeStoredCodes(scan);
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
        normalizeStoredCodes(scan);
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
        return scanMapper.findByBah(
                MedicalRecordCodeUtils.normalizeOrEmpty(bah),
                MedicalRecordCodeUtils.toSearchTerm(bah)
        );
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
        normalizeSearchCodes(request);
        return scanMapper.findByCondition(request);
    }

    @Override
    public List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        normalizeSearchCodes(request);
        int offset = PaginationUtils.calculateOffset(page, size);
        return scanMapper.findByConditionWithPagination(request, offset, size);
    }

    @Override
    public long countByCondition(ScanRequest request) {
        normalizeSearchCodes(request);
        return scanMapper.countByCondition(request);
    }

    private Path resolveImagePath(Scan scan) {
        if (scan == null) {
            return null;
        }
        String folderPath = scan.getFolder();
        String brxh = scan.getBrxh();
        String storedBah = scan.getBah();
        if (folderPath == null || folderPath.length() < 5 || brxh == null || storedBah == null) {
            return null;
        }
        String parentFolder = folderPath.substring(0, 5);
        String folderName = brxh + "-" + storedBah;
        return Paths.get(imageProperties.getBasePath(), parentFolder, folderPath, folderName);
    }

    private void normalizeStoredCodes(Scan scan) {
        if (scan == null) {
            return;
        }
        if (scan.getBah() != null) {
            scan.setBah(MedicalRecordCodeUtils.normalize(scan.getBah()));
        }
        if (scan.getSjh() != null) {
            scan.setSjh(MedicalRecordCodeUtils.normalize(scan.getSjh()));
        }
    }

    private void normalizeSearchCodes(ScanRequest request) {
        if (request == null) {
            return;
        }
        if (request.getBah() != null) {
            request.setBah(MedicalRecordCodeUtils.toSearchTerm(request.getBah()));
        }
        if (request.getSjh() != null) {
            request.setSjh(MedicalRecordCodeUtils.toSearchTerm(request.getSjh()));
        }
    }
}
