package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;

import java.util.List;

public interface MigrationService {

    /**
     * Upload a single scan record's image to OSS.
     */
    OssUploadResult uploadSingleScan(Integer scanId);

    /**
     * Upload all images under a given BAH to OSS.
     */
    List<OssUploadResult> uploadByBah(String bah);

    /**
     * Get migration statistics.
     */
    MigrationStatisticsDTO getStatistics();

    /**
     * Get pending migration records.
     */
    List<com.zjcxph.imgapi.entity.Scan> getPendingMigrations(int limit);

    /**
     * Get migration logs with optional status filter and pagination.
     */
    List<ImageMigrationLog> getMigrationLogs(String status, int page, int size);

    /**
     * Count migration logs with optional status filter.
     */
    long countMigrationLogs(String status);
}
