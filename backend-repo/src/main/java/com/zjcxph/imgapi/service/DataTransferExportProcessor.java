package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * CSV 分卷导出处理器。
 *
 * <p>使用主键游标和 PostgreSQL COPY TO STDOUT，避免 OFFSET 在数千万行数据上逐渐变慢。
 * 已完成分卷会保留，任务重试时从最后一个完成分卷的 last_record_id 继续。</p>
 */
@Service
public class DataTransferExportProcessor {

    private final DataTransferRepository repository;
    private final DataTransferStorageService storageService;
    private final DataTransferProperties properties;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataTransferExportProcessor(
            DataTransferRepository repository,
            DataTransferStorageService storageService,
            DataTransferProperties properties,
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.properties = properties;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void process(DataTransferJob job) throws Exception {
        ExportSpec spec = resolveSpec(job);

        // 服务重启或前一次执行异常留下的失败分卷不包含可用结果，先清理记录和临时文件。
        cleanupFailedFiles(job.getId());

        long maximumId = spec.endId() == null
                ? queryLong("SELECT COALESCE(MAX(id), 0) FROM " + spec.table())
                : spec.endId();
        long totalRows = queryLong(
                "SELECT COUNT(*) FROM " + spec.table() + " WHERE id > ? AND id <= ?",
                spec.startId(),
                maximumId
        );
        int totalFiles = totalRows == 0
                ? 0
                : (int) Math.ceil(totalRows / (double) spec.rowsPerPart());

        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job
                SET total_files = ?, status = 'EXPORTING', current_stage = '准备分卷导出',
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    heartbeat_at = CURRENT_TIMESTAMP, error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                totalFiles,
                job.getId()
        );

        if (totalRows == 0) {
            repository.updateJobStatus(job.getId(), "COMPLETED", "没有符合条件的数据", null);
            return;
        }

        ResumePoint resumePoint = findResumePoint(job.getId(), spec.startId());
        long cursor = resumePoint.lastRecordId();
        int sequence = resumePoint.nextSequence();

        while (cursor < maximumId) {
            String status = repository.findJobStatus(job.getId());
            if ("PAUSED".equals(status) || "CANCELLED".equals(status)) {
                return;
            }

            ExportRange range = findRange(spec.table(), cursor, maximumId, spec.rowsPerPart());
            if (range == null || range.rowCount() == 0) {
                break;
            }

            repository.updateJobCurrentFile(job.getId(), sequence, "导出 CSV 分卷");
            exportPart(job, spec, sequence, cursor, range);
            cursor = range.lastId();
            sequence++;
            repository.refreshJobTotals(job.getId());
        }

        repository.refreshJobTotals(job.getId());
        long failed = queryLong(
                "SELECT COUNT(*) FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                job.getId()
        );
        if (failed > 0) {
            repository.updateJobStatus(
                    job.getId(),
                    "FAILED",
                    "存在失败分卷",
                    failed + " 个导出分卷失败，可重试任务"
            );
        }
        else {
            repository.updateJobStatus(job.getId(), "COMPLETED", "导出完成", null);
        }
    }

    private ExportSpec resolveSpec(DataTransferJob job) throws Exception {
        JsonNode options = objectMapper.readTree(job.getOptions() == null ? "{}" : job.getOptions());
        long startId = options.path("startId").isIntegralNumber()
                ? Math.max(0L, options.path("startId").asLong())
                : 0L;
        Long endId = options.path("endId").isIntegralNumber()
                ? options.path("endId").asLong()
                : null;
        if (endId != null && endId <= startId) {
            throw new IllegalArgumentException("结束 ID 必须大于起始 ID");
        }

        int rowsPerPart = options.path("rowsPerPart").isIntegralNumber()
                ? options.path("rowsPerPart").asInt()
                : properties.getExportRowsPerPart();
        rowsPerPart = Math.max(10_000, Math.min(rowsPerPart, 2_000_000));

        if ("MR_STATISTICS".equals(job.getEntityType())) {
            return new ExportSpec(
                    "app.mr_statistics",
                    "mr-statistics",
                    "bah, cid, openerno, date, type, pages, sjh, patientname, "
                            + "inpatientdepartment, patientid, dischargedate",
                    startId,
                    endId,
                    rowsPerPart
            );
        }
        if ("MR_SCAN".equals(job.getEntityType())) {
            return new ExportSpec(
                    "app.mr_scan",
                    "mr-scan",
                    "brxh, bah, sjh, filename, btype, pages, openerno, uploaddate, "
                            + "uploadflag, folder, file_size",
                    startId,
                    endId,
                    rowsPerPart
            );
        }
        throw new IllegalArgumentException("不支持的数据类型：" + job.getEntityType());
    }

    private void cleanupFailedFiles(long jobId) {
        repository.findFiles(jobId).stream()
                .filter(file -> "FAILED".equals(file.getStatus()))
                .forEach(file -> {
                    try {
                        if (file.getStoredPath() != null && !file.getStoredPath().isBlank()) {
                            Path path = storageService.resolveStoredPath(file.getStoredPath());
                            Files.deleteIfExists(path);
                        }
                    }
                    catch (Exception ignored) {
                        // 数据库记录仍会删除；不可用临时文件由后续保留策略清理。
                    }
                });
        jdbcTemplate.update(
                "DELETE FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                jobId
        );
    }

    private ResumePoint findResumePoint(long jobId, long configuredStartId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(MAX(last_record_id), ?) AS last_record_id,
                    COALESCE(MAX(sequence_no), 0) + 1 AS next_sequence
                FROM app.data_transfer_file
                WHERE job_id = ? AND status = 'COMPLETED'
                """,
                (rs, rowNum) -> new ResumePoint(rs.getLong(1), rs.getInt(2)),
                configuredStartId,
                jobId
        );
    }

    private ExportRange findRange(String table, long cursor, long maximumId, int limit) {
        return jdbcTemplate.queryForObject(
                "SELECT MIN(id), MAX(id), COUNT(*) FROM (SELECT id FROM " + table
                        + " WHERE id > ? AND id <= ? ORDER BY id LIMIT ?) selected",
                (rs, rowNum) -> {
                    long count = rs.getLong(3);
                    if (count == 0) {
                        return null;
                    }
                    return new ExportRange(rs.getLong(1), rs.getLong(2), count);
                },
                cursor,
                maximumId,
                limit
        );
    }

    private void exportPart(
            DataTransferJob job,
            ExportSpec spec,
            int sequence,
            long cursor,
            ExportRange range
    ) throws Exception {
        String filename = String.format(
                Locale.ROOT,
                "%s-part-%04d.csv.gz",
                spec.filePrefix(),
                sequence
        );
        Path outputPath = storageService.createOutputPath(job.getId(), filename);
        long fileId = repository.createFile(
                job.getId(),
                sequence,
                filename,
                outputPath.toString(),
                filename,
                0,
                null,
                "EXPORTING"
        );

        String copySql = "COPY (SELECT " + spec.selectColumns()
                + " FROM " + spec.table()
                + " WHERE id > " + cursor
                + " AND id <= " + range.lastId()
                + " ORDER BY id) TO STDOUT WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')";

        try {
            try (Connection connection = dataSource.getConnection();
                 OutputStream fileOutput = Files.newOutputStream(outputPath);
                 GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput, 1024 * 1024)) {
                connection.unwrap(PGConnection.class).getCopyAPI().copyOut(copySql, gzipOutput);
                gzipOutput.finish();
            }
            repository.updateExportFile(
                    fileId,
                    range.rowCount(),
                    range.firstId(),
                    range.lastId(),
                    Files.size(outputPath),
                    storageService.sha256(outputPath)
            );
        }
        catch (Exception exception) {
            repository.updateFileStatus(fileId, "FAILED", safeMessage(exception));
            throw exception;
        }
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private record ExportSpec(
            String table,
            String filePrefix,
            String selectColumns,
            long startId,
            Long endId,
            int rowsPerPart
    ) {
    }

    private record ResumePoint(long lastRecordId, int nextSequence) {
    }

    private record ExportRange(long firstId, long lastId, long rowCount) {
    }
}
