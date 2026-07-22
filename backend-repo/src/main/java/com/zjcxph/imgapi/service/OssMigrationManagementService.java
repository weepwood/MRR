package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.OssMigrationManagementMapper;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OSS 迁移管理页面查询服务。
 */
@Service
public class OssMigrationManagementService {

    private static final Logger logger = LoggerFactory.getLogger(OssMigrationManagementService.class);

    private final OssMigrationManagementMapper mapper;
    private final OssService ossService;

    public OssMigrationManagementService(OssMigrationManagementMapper mapper, OssService ossService) {
        this.mapper = mapper;
        this.ossService = ossService;
    }

    public List<Scan> getPending(String folder, String bah, String sjh, int limit) {
        return mapper.findPending(trimToNull(folder), trimToNull(bah), trimToNull(sjh), limit);
    }

    public List<Scan> getWaitingSjh(String folder, String bah, String sjh, int limit) {
        return mapper.findWaitingSjh(trimToNull(folder), trimToNull(bah), trimToNull(sjh), limit);
    }

    public List<ImageMigrationLog> getLogs(String status, Integer scanId, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        List<ImageMigrationLog> logs = mapper.findLogs(trimToNull(status), scanId, offset, size);
        logs.forEach(this::enrichWithPresignedUrl);
        return logs;
    }

    public long countLogs(String status, Integer scanId) {
        return mapper.countLogs(trimToNull(status), scanId);
    }

    private void enrichWithPresignedUrl(ImageMigrationLog log) {
        if (log == null
                || !"success".equals(log.getMigrationStatus())
                || log.getOssUrl() == null
                || log.getOssUrl().isBlank()) {
            return;
        }
        try {
            log.setOssUrl(ossService.generatePresignedUrl(log.getOssUrl()));
        } catch (Exception exception) {
            logger.warn("Failed to generate OSS migration log URL: logId={}", log.getId(), exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
