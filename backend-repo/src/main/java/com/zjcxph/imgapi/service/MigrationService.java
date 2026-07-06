package com.zjcxph.imgapi.service;

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

    List<OssUploadResult> uploadByBah(String bah);

    MigrationStatisticsDTO getStatistics();

    List<Scan> getPendingMigrations(int limit);

    List<Scan> getPendingMigrations(int limit, String folder);

    List<Map<String, Object>> getPendingFolders();

    String getOssSignedUrl(Integer scanId);

    List<ImageMigrationLog> getMigrationLogs(String status, int page, int size);

    long countMigrationLogs(String status);

    void enrichWithPresignedUrl(ImageMigrationLog log);

    MigrationJob createMigrationJob();

    MigrationJob getMigrationJob(Long id);

    PageResult<MigrationJob> listMigrationJobs(int page, int size);
}
