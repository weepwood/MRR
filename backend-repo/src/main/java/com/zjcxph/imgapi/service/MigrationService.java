package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;

import java.util.List;
import java.util.Map;

public interface MigrationService {

    OssUploadResult uploadSingleScan(Integer scanId);

    OssUploadResult uploadLoadedScan(Scan scan);

    List<OssUploadResult> uploadByBah(String bah);

    MigrationStatisticsDTO getStatistics();

    MigrationReadinessDTO getReadiness(int sampleSize);

    List<Scan> getPendingMigrations(int limit);

    List<Scan> getPendingMigrations(int limit, String folder);

    List<Map<String, Object>> getPendingFolders();

    String getOssSignedUrl(Integer scanId);

    List<ImageMigrationLog> getMigrationLogs(String status, int page, int size);

    long countMigrationLogs(String status);

    void enrichWithPresignedUrl(ImageMigrationLog log);

    MigrationJob createMigrationJob(MigrationJobRequest request);

    MigrationJob getMigrationJob(Long id);

    PageResult<MigrationJob> listMigrationJobs(int page, int size);

    MigrationJob cancelMigrationJob(Long id);

    int retryFailedScans(List<Integer> scanIds);

    boolean hasActiveMigrationJob();
}
