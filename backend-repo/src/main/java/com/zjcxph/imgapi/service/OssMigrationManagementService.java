package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.OssMigrationManagementMapper;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OSS 迁移管理页面查询服务。
 */
@Service
public class OssMigrationManagementService {

    private final OssMigrationManagementMapper mapper;
    private final MigrationService migrationService;
    private final com.zjcxph.imgapi.config.ImageDeliveryProperties deliveryProperties;

    public OssMigrationManagementService(OssMigrationManagementMapper mapper, MigrationService migrationService,
                                         com.zjcxph.imgapi.config.ImageDeliveryProperties deliveryProperties) {
        this.mapper = mapper;
        this.migrationService = migrationService;
        this.deliveryProperties=deliveryProperties;
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
        if(log==null)return;
        com.zjcxph.imgapi.utils.MigrationLogPrivacy.sanitize(log);
        if(deliveryProperties.isGateway())log.setOssUrl(null);
        else migrationService.enrichWithPresignedUrl(log);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
