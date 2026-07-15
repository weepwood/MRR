package com.zjcxph.imgapi.repository;

import com.zjcxph.imgapi.entity.DataTransferError;
import com.zjcxph.imgapi.entity.DataTransferFile;
import com.zjcxph.imgapi.entity.DataTransferJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Repository
public class DataTransferRepository {

    private static final RowMapper<DataTransferJob> JOB_ROW_MAPPER = (rs, rowNum) -> {
        DataTransferJob job = new DataTransferJob();
        job.setId(rs.getLong("id"));
        job.setDirection(rs.getString("direction"));
        job.setEntityType(rs.getString("entity_type"));
        job.setStatus(rs.getString("status"));
        job.setImportMode(rs.getString("import_mode"));
        job.setSourceType(rs.getString("source_type"));
        job.setTotalFiles(rs.getInt("total_files"));
        job.setCompletedFiles(rs.getInt("completed_files"));
        job.setTotalRows(rs.getLong("total_rows"));
        job.setProcessedRows(rs.getLong("processed_rows"));
        job.setValidRows(rs.getLong("valid_rows"));
        job.setInvalidRows(rs.getLong("invalid_rows"));
        job.setInsertedRows(rs.getLong("inserted_rows"));
        job.setUpdatedRows(rs.getLong("updated_rows"));
        job.setSkippedRows(rs.getLong("skipped_rows"));
        job.setProgress(rs.getBigDecimal("progress"));
        job.setCurrentStage(rs.getString("current_stage"));
        job.setCurrentFileNo((Integer) rs.getObject("current_file_no"));
        try {
            job.setOptions(rs.getString("options"));
        }
        catch (Exception ignored) {
            job.setOptions("{}");
        }
        job.setErrorMessage(rs.getString("error_message"));
        job.setCreatedBy(rs.getString("created_by"));
        job.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        job.setStartedAt(rs.getObject("started_at", OffsetDateTime.class));
        job.setCompletedAt(rs.getObject("completed_at", OffsetDateTime.class));
        job.setHeartbeatAt(rs.getObject("heartbeat_at", OffsetDateTime.class));
        job.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        try {
            job.setFailedFiles(rs.getLong("failed_files"));
        }
        catch (Exception ignored) {
            job.setFailedFiles(0L);
        }
        return job;
    };

    private static final RowMapper<DataTransferFile> FILE_ROW_MAPPER = (rs, rowNum) -> {
        DataTransferFile file = new DataTransferFile();
        file.setId(rs.getLong("id"));
        file.setJobId(rs.getLong("job_id"));
        file.setSequenceNo(rs.getInt("sequence_no"));
        file.setOriginalFilename(rs.getString("original_filename"));
        file.setStoredPath(rs.getString("stored_path"));
        file.setDownloadName(rs.getString("download_name"));
        file.setFileSize(rs.getLong("file_size"));
        file.setSha256(rs.getString("sha256"));
        file.setStatus(rs.getString("status"));
        file.setTotalRows(rs.getLong("total_rows"));
        file.setProcessedRows(rs.getLong("processed_rows"));
        file.setValidRows(rs.getLong("valid_rows"));
        file.setInvalidRows(rs.getLong("invalid_rows"));
        file.setInsertedRows(rs.getLong("inserted_rows"));
        file.setUpdatedRows(rs.getLong("updated_rows"));
        file.setSkippedRows(rs.getLong("skipped_rows"));
        file.setFirstRecordId((Long) rs.getObject("first_record_id"));
        file.setLastRecordId((Long) rs.getObject("last_record_id"));
        file.setErrorMessage(rs.getString("error_message"));
        file.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        file.setStartedAt(rs.getObject("started_at", OffsetDateTime.class));
        file.setCompletedAt(rs.getObject("completed_at", OffsetDateTime.class));
        file.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        return file;
    };

    private static final RowMapper<DataTransferError> ERROR_ROW_MAPPER = (rs, rowNum) -> {
        DataTransferError error = new DataTransferError();
        error.setId(rs.getLong("id"));
        error.setJobId(rs.getLong("job_id"));
        error.setFileId((Long) rs.getObject("file_id"));
        error.setSourceRowNo((Long) rs.getObject("source_row_no"));
        error.setFieldName(rs.getString("field_name"));
        error.setErrorCode(rs.getString("error_code"));
        error.setErrorMessage(rs.getString("error_message"));
        error.setRawData(rs.getString("raw_data"));
        error.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        return error;
    };

    private final JdbcTemplate jdbcTemplate;

    public DataTransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createJob(String direction, String entityType, String importMode,
                          String sourceType, String options, String createdBy) {
        String sql = """
                INSERT INTO app.data_transfer_job
                    (direction, entity_type, status, import_mode, source_type, options, created_by)
                VALUES (?, ?, 'CREATED', ?, ?, CAST(? AS jsonb), ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, direction);
            statement.setString(2, entityType);
            statement.setString(3, importMode);
            statement.setString(4, sourceType);
            statement.setString(5, options == null ? "{}" : options);
            statement.setString(6, createdBy);
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public long createFile(long jobId, int sequenceNo, String originalFilename,
                           String storedPath, String downloadName, long fileSize,
                           String sha256, String status) {
        String sql = """
                INSERT INTO app.data_transfer_file
                    (job_id, sequence_no, original_filename, stored_path, download_name,
                     file_size, sha256, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, jobId);
            statement.setInt(2, sequenceNo);
            statement.setString(3, originalFilename);
            statement.setString(4, storedPath);
            statement.setString(5, downloadName);
            statement.setLong(6, fileSize);
            statement.setString(7, sha256);
            statement.setString(8, status);
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public DataTransferJob findJob(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM app.v_data_transfer_job_summary WHERE id = ?",
                JOB_ROW_MAPPER,
                id
        ).stream().findFirst().orElse(null);
    }

    public List<DataTransferJob> findJobs(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM app.v_data_transfer_job_summary ORDER BY created_at DESC LIMIT ?",
                JOB_ROW_MAPPER,
                limit
        );
    }

    public DataTransferFile findFile(long id) {
        return jdbcTemplate.query(
                "SELECT * FROM app.data_transfer_file WHERE id = ?",
                FILE_ROW_MAPPER,
                id
        ).stream().findFirst().orElse(null);
    }

    public List<DataTransferFile> findFiles(long jobId) {
        return jdbcTemplate.query(
                "SELECT * FROM app.data_transfer_file WHERE job_id = ? ORDER BY sequence_no",
                FILE_ROW_MAPPER,
                jobId
        );
    }

    public List<DataTransferFile> findRunnableFiles(long jobId) {
        return jdbcTemplate.query(
                """
                SELECT * FROM app.data_transfer_file
                WHERE job_id = ? AND status IN ('UPLOADED', 'FAILED')
                ORDER BY sequence_no
                """,
                FILE_ROW_MAPPER,
                jobId
        );
    }

    public List<DataTransferError> findErrors(long jobId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM app.data_transfer_error WHERE job_id = ? ORDER BY id LIMIT ?",
                ERROR_ROW_MAPPER,
                jobId,
                limit
        );
    }

    public void setJobFileCount(long jobId, int totalFiles) {
        jdbcTemplate.update(
                "UPDATE app.data_transfer_job SET total_files = ?, status = 'UPLOADED', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                totalFiles,
                jobId
        );
    }

    public void markJobStarted(long jobId, String status, String stage) {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job
                SET status = ?, current_stage = ?, started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    heartbeat_at = CURRENT_TIMESTAMP, error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                status,
                stage,
                jobId
        );
    }

    public void updateJobStatus(long jobId, String status, String stage, String errorMessage) {
        boolean terminal = List.of("COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED", "CANCELLED").contains(status);
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job
                SET status = ?, current_stage = ?, error_message = ?,
                    heartbeat_at = CURRENT_TIMESTAMP,
                    completed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                status,
                stage,
                errorMessage,
                terminal,
                jobId
        );
    }

    public void updateJobCurrentFile(long jobId, int sequenceNo, String stage) {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job
                SET current_file_no = ?, current_stage = ?, heartbeat_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                sequenceNo,
                stage,
                jobId
        );
    }

    public void updateFileStatus(long fileId, String status, String errorMessage) {
        boolean started = List.of("VALIDATING", "IMPORTING", "EXPORTING").contains(status);
        boolean terminal = List.of("COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED", "SKIPPED").contains(status);
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_file
                SET status = ?, error_message = ?,
                    started_at = CASE WHEN ? THEN COALESCE(started_at, CURRENT_TIMESTAMP) ELSE started_at END,
                    completed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                status,
                errorMessage,
                started,
                terminal,
                fileId
        );
    }

    public void updateFileCounts(long fileId, long total, long valid, long invalid,
                                 long inserted, long updated, long skipped) {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_file
                SET total_rows = ?, processed_rows = ?, valid_rows = ?, invalid_rows = ?,
                    inserted_rows = ?, updated_rows = ?, skipped_rows = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                total,
                total,
                valid,
                invalid,
                inserted,
                updated,
                skipped,
                fileId
        );
    }

    public void updateExportFile(long fileId, long rowCount, long firstId, long lastId,
                                 long fileSize, String sha256) {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_file
                SET total_rows = ?, processed_rows = ?, valid_rows = ?,
                    first_record_id = ?, last_record_id = ?, file_size = ?, sha256 = ?,
                    status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                rowCount,
                rowCount,
                rowCount,
                firstId,
                lastId,
                fileSize,
                sha256,
                fileId
        );
    }

    public void refreshJobTotals(long jobId) {
        jdbcTemplate.update(
                """
                UPDATE app.data_transfer_job j
                SET
                    completed_files = totals.completed_files,
                    total_rows = totals.total_rows,
                    processed_rows = totals.processed_rows,
                    valid_rows = totals.valid_rows,
                    invalid_rows = totals.invalid_rows,
                    inserted_rows = totals.inserted_rows,
                    updated_rows = totals.updated_rows,
                    skipped_rows = totals.skipped_rows,
                    progress = CASE
                        WHEN j.total_files = 0 THEN 0
                        ELSE ROUND(totals.completed_files * 100.0 / j.total_files, 2)
                    END,
                    heartbeat_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                FROM (
                    SELECT
                        COUNT(*) FILTER (WHERE status IN ('COMPLETED', 'COMPLETED_WITH_ERRORS', 'SKIPPED'))::INTEGER AS completed_files,
                        COALESCE(SUM(total_rows), 0) AS total_rows,
                        COALESCE(SUM(processed_rows), 0) AS processed_rows,
                        COALESCE(SUM(valid_rows), 0) AS valid_rows,
                        COALESCE(SUM(invalid_rows), 0) AS invalid_rows,
                        COALESCE(SUM(inserted_rows), 0) AS inserted_rows,
                        COALESCE(SUM(updated_rows), 0) AS updated_rows,
                        COALESCE(SUM(skipped_rows), 0) AS skipped_rows
                    FROM app.data_transfer_file
                    WHERE job_id = ?
                ) totals
                WHERE j.id = ?
                """,
                jobId,
                jobId
        );
    }

    public String findJobStatus(long jobId) {
        return jdbcTemplate.query(
                "SELECT status FROM app.data_transfer_job WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                jobId
        );
    }

    public void clearJobErrors(long jobId) {
        jdbcTemplate.update("DELETE FROM app.data_transfer_error WHERE job_id = ?", jobId);
    }

    public JdbcTemplate jdbcTemplate() {
        return jdbcTemplate;
    }
}
