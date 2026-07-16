package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.dto.resp.CursorPageResult;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScanServiceImpl implements ScanService {

    private static final int MAX_LEGACY_QUERY_LIMIT = 1000;

    private final ScanMapper scanMapper;

    public ScanServiceImpl(ScanMapper scanMapper) {
        this.scanMapper = scanMapper;
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
            // archive_id 由数据库触发器解析，重新读取以返回数据库最终状态。
            return scanMapper.findById(scan.getId());
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
            // 数据库可能根据新的 BAH/SJH 更新 archive_id，返回持久化后的完整记录。
            return scanMapper.findById(scan.getId());
        }
        return null;
    }

    @Override
    public List<Scan> findAll(int limit) {
        return scanMapper.findAll(normalizeLegacyLimit(limit));
    }

    @Override
    public CursorPageResult<Scan> findAfterId(Integer afterId, int size) {
        PaginationUtils.validatePageParams(1, size);
        int safeAfterId = afterId == null ? 0 : Math.max(0, afterId);
        List<Scan> fetched = scanMapper.findAfterId(safeAfterId, size + 1);
        boolean hasMore = fetched.size() > size;
        List<Scan> page = hasMore ? List.copyOf(fetched.subList(0, size)) : fetched;
        Long nextCursorId = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getId().longValue()
                : null;
        return CursorPageResult.of(page, nextCursorId, hasMore, size);
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
    public List<Scan> findByCondition(ScanRequest request, int limit) {
        ScanRequest prepared = prepareSearchRequest(request);
        return scanMapper.findByCondition(prepared, normalizeLegacyLimit(limit));
    }

    @Override
    public List<Scan> findByConditionWithPagination(ScanRequest request, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        ScanRequest prepared = prepareSearchRequest(request);
        int offset = PaginationUtils.calculateOffset(page, size);
        return scanMapper.findByConditionWithPagination(prepared, offset, size);
    }

    @Override
    public long countByCondition(ScanRequest request) {
        return scanMapper.countByCondition(prepareSearchRequest(request));
    }

    private int normalizeLegacyLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LEGACY_QUERY_LIMIT);
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

    private ScanRequest prepareSearchRequest(ScanRequest request) {
        ScanRequest prepared = request == null ? new ScanRequest() : request;
        if (prepared.getBah() != null) {
            prepared.setBah(MedicalRecordCodeUtils.toSearchTerm(prepared.getBah()));
        }
        if (prepared.getSjh() != null) {
            prepared.setSjh(MedicalRecordCodeUtils.toSearchTerm(prepared.getSjh()));
        }
        return prepared;
    }
}
