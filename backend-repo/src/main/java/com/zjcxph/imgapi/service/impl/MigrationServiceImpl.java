package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageMigrationLogMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class MigrationServiceImpl implements MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationServiceImpl.class);

    private final OssService ossService;
    private final ScanMapper scanMapper;
    private final ImageMigrationLogMapper migrationLogMapper;
    private final ImageProperties imageProperties;

    public MigrationServiceImpl(OssService ossService, ScanMapper scanMapper,
                                 ImageMigrationLogMapper migrationLogMapper,
                                 ImageProperties imageProperties) {
        this.ossService = ossService;
        this.scanMapper = scanMapper;
        this.migrationLogMapper = migrationLogMapper;
        this.imageProperties = imageProperties;
    }

    @Override
    public OssUploadResult uploadSingleScan(Integer scanId) {
        Scan scan = scanMapper.findById(scanId);
        if (scan == null) {
            return new OssUploadResult(scanId, "failed", "Scan record not found: " + scanId);
        }

        // Skip if already migrated
        if (scan.getOssUrl() != null && !scan.getOssUrl().isBlank()) {
            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(scan.getOssUrl());
            result.setStatus("skipped");
            result.setErrorMessage("Already migrated");
            return result;
        }

        // Build local file path
        String localPath = buildLocalPath(scan);
        if (localPath == null) {
            return new OssUploadResult(scanId, "failed", "Cannot build local path for scan: " + scanId);
        }

        File localFile = new File(localPath);
        if (!localFile.exists()) {
            logMigration(scanId, localPath, null, "failed", "Local file not found: " + localPath, null, null);
            return new OssUploadResult(scanId, "failed", "Local file not found: " + localPath);
        }

        try {
            // Calculate MD5
            String md5 = ossService.calculateMd5(localPath);
            long fileSize = ossService.getFileSize(localPath);

            // Build OSS key
            String ossKey = buildOssKey(scan);

            // Check if already exists in OSS
            if (ossService.doesObjectExist(ossKey)) {
                logger.info("File already exists in OSS, updating DB only: {}", ossKey);
            } else {
                // Upload to OSS
                ossService.uploadFile(localPath, ossKey);
            }

            // Generate the OSS object key (stored in DB, not the signed URL)
            String ossUrl = ossKey;

            // Update mr_scan table
            scanMapper.updateOssInfo(scanId, ossUrl, fileSize, md5, "migrated");

            // Log migration
            logMigration(scanId, localPath, ossUrl, "success", null, fileSize, md5);

            // Build result
            OssUploadResult result = new OssUploadResult();
            result.setScanId(scanId);
            result.setOssUrl(ossService.generatePresignedUrl(ossKey));
            result.setFileSize(fileSize);
            result.setChecksumMd5(md5);
            result.setStatus("success");
            return result;

        } catch (Exception e) {
            logger.error("Failed to upload scan {} to OSS", scanId, e);
            logMigration(scanId, localPath, null, "failed", e.getMessage(), null, null);
            return new OssUploadResult(scanId, "failed", e.getMessage());
        }
    }

    @Override
    public List<OssUploadResult> uploadByBah(String bah) {
        List<Scan> scans = scanMapper.findByBah(bah);
        List<OssUploadResult> results = new ArrayList<>();

        if (scans == null || scans.isEmpty()) {
            results.add(new OssUploadResult(null, "failed", "No scan records found for BAH: " + bah));
            return results;
        }

        logger.info("Starting batch upload for BAH={}, total {} records", bah, scans.size());

        for (Scan scan : scans) {
            if (scan.getUploadFlag() == null || scan.getUploadFlag() == 0) {
                continue; // skip deleted/disabled records
            }
            OssUploadResult result = uploadSingleScan(scan.getId());
            results.add(result);
        }

        long successCount = results.stream().filter(r -> "success".equals(r.getStatus())).count();
        logger.info("Batch upload for BAH={} completed: {}/{} succeeded", bah, successCount, results.size());

        return results;
    }

    @Override
    public MigrationStatisticsDTO getStatistics() {
        MigrationStatisticsDTO stats = new MigrationStatisticsDTO();

        long total = scanMapper.countTotalUploadedScans();
        long migrated = scanMapper.countByMigrationStatus("migrated");
        long verified = scanMapper.countByMigrationStatus("verified");
        long notMigrated = scanMapper.countByMigrationStatus("not_migrated");

        stats.setTotalCount(total);
        stats.setMigratedCount(migrated + verified);
        stats.setPendingCount(total - migrated - verified);
        stats.setFailedCount(0); // failed records revert to not_migrated

        if (total > 0) {
            stats.setPercentage(Math.round((migrated + verified) * 10000.0 / total) / 100.0);
        }

        // Count failed from migration logs
        try {
            long failedLogs = migrationLogMapper.countWithFilter("failed");
            stats.setFailedCount(failedLogs);
        } catch (Exception e) {
            logger.warn("Failed to count failed migration logs", e);
        }

        return stats;
    }

    @Override
    public List<Scan> getPendingMigrations(int limit) {
        return scanMapper.findPendingMigration(limit);
    }

    @Override
    public List<ImageMigrationLog> getMigrationLogs(String status, int page, int size) {
        int offset = (page - 1) * size;
        return migrationLogMapper.findWithPagination(status, offset, size);
    }

    @Override
    public long countMigrationLogs(String status) {
        return migrationLogMapper.countWithFilter(status);
    }

    // ==================== Private helpers ====================

    private String buildLocalPath(Scan scan) {
        String folder = scan.getFolder();
        String brxh = scan.getBrxh();
        String bah = scan.getBah();
        String filename = scan.getFilename();

        if (folder == null || folder.length() < 5 || brxh == null || bah == null || filename == null) {
            logger.warn("Incomplete scan data for path building: id={}", scan.getId());
            return null;
        }

        String parentFolder = folder.substring(0, 5);
        String folderName = brxh + "-" + bah;
        Path path = Paths.get(imageProperties.getBasePath(), parentFolder, folder, folderName, filename);
        return path.toString();
    }

    private String buildOssKey(Scan scan) {
        String folder = scan.getFolder();
        String parentFolder = folder.substring(0, 5);
        String folderName = scan.getBrxh() + "-" + scan.getBah();
        return String.format("medical-records/%s/%s/%s/%s",
                parentFolder, folder, folderName, scan.getFilename());
    }

    private void logMigration(Integer scanId, String localPath, String ossUrl,
                               String status, String errorMessage, Long fileSize, String md5) {
        try {
            ImageMigrationLog log = new ImageMigrationLog();
            log.setScanId(scanId);
            log.setLocalPath(localPath);
            log.setOssUrl(ossUrl);
            log.setMigrationStatus(status);
            log.setErrorMessage(errorMessage);
            log.setFileSize(fileSize);
            log.setChecksumMd5(md5);
            if ("success".equals(status)) {
                log.setMigratedAt(new Date());
            }
            migrationLogMapper.insert(log);
        } catch (Exception e) {
            logger.error("Failed to insert migration log for scanId={}", scanId, e);
        }
    }
}
