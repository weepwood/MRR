package com.zjcxph.imgapi.repository;

import com.zjcxph.imgapi.entity.ArchiveExportJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ArchiveExportJobRepository {

    private static final RowMapper<ArchiveExportJob> ROW_MAPPER = new JobRowMapper();

    private final JdbcTemplate jdbcTemplate;

    public ArchiveExportJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(ArchiveExportJob job) {
        jdbcTemplate.update("""
                INSERT INTO app.archive_export_job (
                    id, owner_user_id, owner_username, format, scope, status,
                    bah, sjh, scan_ids, planned_count, processed_count, failed_count,
                    estimated_bytes, output_bytes, source_summary, file_name,
                    cancel_requested, idempotency_key, expires_at, created_at, updated_at
                ) VALUES (
                    CAST(? AS UUID), ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 0, ?, ?,
                    FALSE, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                job.getId(), job.getOwnerUserId(), job.getOwnerUsername(), job.getFormat(),
                job.getScope(), job.getStatus(), job.getBah(), job.getSjh(), job.getScanIds(),
                job.getPlannedCount(), job.getEstimatedBytes(), job.getSourceSummary(),
                job.getFileName(), job.getIdempotencyKey(), timestamp(job.getExpiresAt()));
    }

    public Optional<ArchiveExportJob> findById(String id) {
        return queryOne("SELECT * FROM app.archive_export_job WHERE id = CAST(? AS UUID)", id);
    }

    public Optional<ArchiveExportJob> findByIdempotency(
            Long ownerUserId,
            String ownerUsername,
            String idempotencyKey) {
        if (ownerUserId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryOne("""
                SELECT * FROM app.archive_export_job
                WHERE (
                        owner_user_id = ?
                        OR (owner_user_id IS NULL AND owner_username = ?)
                      )
                  AND idempotency_key = ?
                  AND status <> 'EXPIRED'
                ORDER BY created_at DESC
                LIMIT 1
                """, ownerUserId, ownerUsername, idempotencyKey);
    }

    public List<ArchiveExportJob> findActiveByOwner(
            Long ownerUserId,
            String ownerUsername,
            String format,
            int limit) {
        if (ownerUserId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT * FROM app.archive_export_job
                WHERE (
                        owner_user_id = ?
                        OR (owner_user_id IS NULL AND owner_username = ?)
                      )
                  AND format = ?
                  AND (
                      status IN ('PENDING', 'PROCESSING')
                      OR (status = 'SUCCESS' AND expires_at > CURRENT_TIMESTAMP)
                  )
                ORDER BY
                    CASE WHEN status IN ('PENDING', 'PROCESSING') THEN 0 ELSE 1 END,
                    created_at DESC
                LIMIT ?
                """, ROW_MAPPER, ownerUserId, ownerUsername, format, limit);
    }

    public List<ArchiveExportJob> findRecoverable() {
        return jdbcTemplate.query("""
                SELECT * FROM app.archive_export_job
                WHERE status IN ('PENDING', 'PROCESSING')
                ORDER BY created_at ASC
                """, ROW_MAPPER);
    }

    public List<ArchiveExportJob> findExpired(LocalDateTime now) {
        return jdbcTemplate.query("""
                SELECT * FROM app.archive_export_job
                WHERE expires_at IS NOT NULL
                  AND expires_at <= ?
                  AND status IN ('SUCCESS', 'FAILED', 'CANCELLED')
                ORDER BY expires_at ASC
                LIMIT 200
                """, ROW_MAPPER, timestamp(now));
    }

    public void markProcessing(String id, String fileName, String filePath, LocalDateTime expiresAt) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'PROCESSING', file_name = ?, file_path = ?, expires_at = ?,
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, fileName, filePath, timestamp(expiresAt), id);
    }

    public void resetToPending(String id) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'PENDING', processed_count = 0, failed_count = 0,
                    output_bytes = 0, error_message = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, id);
    }

    public void updateProgress(String id, int processedCount) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET processed_count = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID) AND status = 'PROCESSING'
                """, processedCount, id);
    }

    public void markSuccess(String id, long outputBytes, String sha256, LocalDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'SUCCESS', processed_count = planned_count,
                    output_bytes = ?, sha256 = ?, completed_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, outputBytes, sha256, timestamp(completedAt), id);
    }

    public void markFailed(String id, String errorMessage, LocalDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'FAILED', failed_count = GREATEST(1, planned_count - processed_count),
                    error_message = ?, completed_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, truncate(errorMessage), timestamp(completedAt), id);
    }

    public void markCancelled(String id, LocalDateTime completedAt) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'CANCELLED', cancel_requested = TRUE,
                    completed_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, timestamp(completedAt), id);
    }

    public void requestCancel(String id) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET cancel_requested = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID) AND status IN ('PENDING', 'PROCESSING')
                """, id);
    }

    public boolean isCancelRequested(String id) {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT cancel_requested FROM app.archive_export_job
                WHERE id = CAST(? AS UUID)
                """, Boolean.class, id);
        return Boolean.TRUE.equals(result);
    }

    public void markExpired(String id) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET status = 'EXPIRED', file_path = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID)
                """, id);
    }

    public void recordDownload(String id) {
        jdbcTemplate.update("""
                UPDATE app.archive_export_job
                SET download_count = COALESCE(download_count, 0) + 1,
                    last_downloaded_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS UUID) AND status = 'SUCCESS'
                """, id);
    }

    private Optional<ArchiveExportJob> queryOne(String sql, Object... args) {
        List<ArchiveExportJob> rows = jdbcTemplate.query(sql, ROW_MAPPER, args);
        return rows.stream().findFirst();
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static final class JobRowMapper implements RowMapper<ArchiveExportJob> {
        @Override
        public ArchiveExportJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            ArchiveExportJob job = new ArchiveExportJob();
            job.setId(rs.getString("id"));
            job.setOwnerUserId(nullableLong(rs, "owner_user_id"));
            job.setOwnerUsername(rs.getString("owner_username"));
            job.setFormat(rs.getString("format"));
            job.setScope(rs.getString("scope"));
            job.setStatus(rs.getString("status"));
            job.setBah(rs.getString("bah"));
            job.setSjh(rs.getString("sjh"));
            job.setScanIds(rs.getString("scan_ids"));
            job.setPlannedCount(rs.getInt("planned_count"));
            job.setProcessedCount(rs.getInt("processed_count"));
            job.setFailedCount(rs.getInt("failed_count"));
            job.setEstimatedBytes(rs.getLong("estimated_bytes"));
            job.setOutputBytes(rs.getLong("output_bytes"));
            job.setSourceSummary(rs.getString("source_summary"));
            job.setFileName(rs.getString("file_name"));
            job.setFilePath(rs.getString("file_path"));
            job.setSha256(rs.getString("sha256"));
            job.setCancelRequested(rs.getBoolean("cancel_requested"));
            job.setErrorMessage(rs.getString("error_message"));
            job.setIdempotencyKey(rs.getString("idempotency_key"));
            job.setDownloadCount(nullableInteger(rs, "download_count"));
            job.setLastDownloadedAt(localDateTime(rs, "last_downloaded_at"));
            job.setExpiresAt(localDateTime(rs, "expires_at"));
            job.setCreatedAt(localDateTime(rs, "created_at"));
            job.setStartedAt(localDateTime(rs, "started_at"));
            job.setCompletedAt(localDateTime(rs, "completed_at"));
            job.setUpdatedAt(localDateTime(rs, "updated_at"));
            return job;
        }

        private static Long nullableLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }

        private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }

        private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
            Timestamp value = rs.getTimestamp(column);
            return value == null ? null : value.toLocalDateTime();
        }
    }
}
