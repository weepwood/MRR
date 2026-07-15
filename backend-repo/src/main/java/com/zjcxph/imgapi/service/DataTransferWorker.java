package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.entity.DataTransferFile;
import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据交换后台工作器。
 *
 * <p>CSV 先通过 PostgreSQL COPY 写入 UNLOGGED staging 表，再使用集合 SQL 校验和合并。
 * 工作器按文件提交，任务暂停或服务重启后可以从未完成文件继续。</p>
 */
@Service
public class DataTransferWorker {

    private static final Logger log = LoggerFactory.getLogger(DataTransferWorker.class);

    private static final String STATISTICS_HEADER =
            "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate";
    private static final String SCAN_HEADER =
            "brxh,bah,sjh,filename,btype,pages,openerno,uploaddate,uploadflag,folder";

    private final DataTransferRepository repository;
    private final DataTransferStorageService storageService;
    private final DataTransferProperties properties;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataTransferWorker(
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

    @Async("dataTransferExecutor")
    public void executeAsync(long jobId) {
        try {
            DataTransferJob job = repository.findJob(jobId);
            if (job == null) {
                return;
            }
            if ("IMPORT".equals(job.getDirection())) {
                processImport(job);
            }
            else if ("EXPORT".equals(job.getDirection())) {
                processExport(job);
            }
            else {
                throw new IllegalStateException("Unsupported transfer direction: " + job.getDirection());
            }
        }
        catch (Exception exception) {
            log.error("Data transfer job failed: jobId={}", jobId, exception);
            repository.updateJobStatus(jobId, "FAILED", "任务失败", safeMessage(exception));
        }
    }

    private void processImport(DataTransferJob job) {
        List<DataTransferFile> files = repository.findRunnableFiles(job.getId());
        if (files.isEmpty()) {
            finishImportJob(job.getId());
            return;
        }

        repository.clearJobErrors(job.getId());
        for (DataTransferFile file : files) {
            String status = repository.findJobStatus(job.getId());
            if ("PAUSED".equals(status) || "CANCELLED".equals(status)) {
                return;
            }

            repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "导入文件");
            try {
                processImportFile(job, file);
            }
            catch (Exception exception) {
                log.error("Import file failed: jobId={}, fileId={}", job.getId(), file.getId(), exception);
                repository.updateFileStatus(file.getId(), "FAILED", safeMessage(exception));
            }
            repository.refreshJobTotals(job.getId());
        }
        finishImportJob(job.getId());
    }

    private void processImportFile(DataTransferJob job, DataTransferFile file) throws Exception {
        Path path = storageService.resolveStoredPath(file.getStoredPath());
        validateHeader(path, expectedHeader(job.getEntityType()));

        repository.updateFileStatus(file.getId(), "IMPORTING", null);
        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "COPY 到暂存表");
        clearStaging(job.getEntityType(), job.getId(), file.getId());
        long copiedRows = copyIntoStaging(job.getEntityType(), job.getId(), file.getId(), path);

        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "校验与规范化");
        if ("MR_STATISTICS".equals(job.getEntityType())) {
            validateStatistics(job.getId(), file.getId());
        }
        else {
            validateScans(job.getId(), file.getId());
        }

        RowCounts validation = loadValidationCounts(job.getEntityType(), job.getId(), file.getId());
        saveErrorSamples(job.getEntityType(), job.getId(), file.getId());
        if (validation.invalid() > 0) {
            writeErrorReport(job.getEntityType(), job.getId(), file.getId());
        }

        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "合并业务数据");
        MergeCounts mergeCounts = "MR_STATISTICS".equals(job.getEntityType())
                ? mergeStatistics(job, file, validation.valid())
                : mergeScans(job, file, validation.valid());

        repository.updateFileCounts(
                file.getId(),
                copiedRows,
                validation.valid(),
                validation.invalid(),
                mergeCounts.inserted(),
                mergeCounts.updated(),
                mergeCounts.skipped()
        );
        repository.updateFileStatus(
                file.getId(),
                validation.invalid() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED",
                null
        );
        clearStaging(job.getEntityType(), job.getId(), file.getId());
    }

    private void finishImportJob(long jobId) {
        repository.refreshJobTotals(jobId);
        Long failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                Long.class,
                jobId
        );
        Long invalid = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(invalid_rows), 0) FROM app.data_transfer_file WHERE job_id = ?",
                Long.class,
                jobId
        );
        if (failed != null && failed > 0) {
            repository.updateJobStatus(jobId, "FAILED", "存在失败文件", failed + " 个文件失败，可重试失败文件");
        }
        else if (invalid != null && invalid > 0) {
            repository.updateJobStatus(jobId, "COMPLETED_WITH_ERRORS", "导入完成", invalid + " 行数据未导入");
        }
        else {
            repository.updateJobStatus(jobId, "COMPLETED", "导入完成", null);
        }
    }

    private long copyIntoStaging(String entityType, long jobId, long fileId, Path path)
            throws SQLException, IOException {
        String copySql;
        if ("MR_STATISTICS".equals(entityType)) {
            copySql = """
                    COPY app.stg_mr_statistics_import
                        (bah_raw, cid_raw, openerno_raw, date_raw, type_raw, pages_raw,
                         sjh_raw, patientname_raw, inpatientdepartment_raw, patientid_raw,
                         dischargedate_raw)
                    FROM STDIN WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')
                    """;
        }
        else {
            copySql = """
                    COPY app.stg_mr_scan_import
                        (brxh_raw, bah_raw, sjh_raw, filename_raw, btype_raw, pages_raw,
                         openerno_raw, uploaddate_raw, uploadflag_raw, folder_raw)
                    FROM STDIN WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')
                    """;
        }

        try (Connection connection = dataSource.getConnection();
             InputStream inputStream = openCsvInput(path)) {
            connection.setAutoCommit(false);
            try {
                setSessionValue(connection, "app.data_transfer_job_id", Long.toString(jobId));
                setSessionValue(connection, "app.data_transfer_file_id", Long.toString(fileId));
                CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
                long rows = copyManager.copyIn(copySql, inputStream);
                connection.commit();
                return rows;
            }
            catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void setSessionValue(Connection connection, String name, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            statement.setString(1, name);
            statement.setString(2, value);
            statement.execute();
        }
    }

    private void validateStatistics(long jobId, long fileId) {
        jdbcTemplate.update(
                """
                WITH normalized AS (
                    SELECT
                        row_id,
                        app.normalize_medical_record_code(bah_raw) AS bah_n,
                        app.normalize_medical_record_code(sjh_raw) AS sjh_n,
                        app.try_parse_date(date_raw) AS archive_date_n,
                        app.try_parse_date(dischargedate_raw) AS discharge_date_n,
                        CASE
                            WHEN NULLIF(BTRIM(pages_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(pages_raw) ~ '^\\d+$' AND BTRIM(pages_raw)::NUMERIC <= 2147483647
                                THEN BTRIM(pages_raw)::INTEGER
                            ELSE NULL
                        END AS pages_n,
                        NULLIF(BTRIM(cid_raw), '') AS cid_n,
                        NULLIF(BTRIM(openerno_raw), '') AS opener_n,
                        NULLIF(BTRIM(type_raw), '') AS type_n,
                        NULLIF(BTRIM(patientid_raw), '') AS patient_id_n,
                        NULLIF(BTRIM(patientname_raw), '') AS patient_name_n,
                        NULLIF(BTRIM(inpatientdepartment_raw), '') AS department_n,
                        NULLIF(BTRIM(pages_raw), '') IS NOT NULL AS pages_present,
                        NULLIF(BTRIM(date_raw), '') IS NOT NULL AS date_present,
                        NULLIF(BTRIM(dischargedate_raw), '') IS NOT NULL AS discharge_present
                    FROM app.stg_mr_statistics_import
                    WHERE job_id = ? AND file_id = ?
                ), checked AS (
                    SELECT
                        n.*,
                        md5(concat_ws(chr(31),
                            COALESCE(n.sjh_n, ''), COALESCE(n.bah_n, ''),
                            COALESCE(n.cid_n, ''), COALESCE(n.archive_date_n::TEXT, ''),
                            COALESCE(n.type_n, '')
                        )) AS row_hash,
                        CASE
                            WHEN n.sjh_n IS NULL AND n.bah_n IS NULL THEN 'CODE_REQUIRED'
                            WHEN n.pages_present AND n.pages_n IS NULL THEN 'INVALID_PAGES'
                            WHEN n.pages_n < 0 THEN 'INVALID_PAGES'
                            WHEN n.date_present AND n.archive_date_n IS NULL THEN 'INVALID_DATE'
                            WHEN n.discharge_present AND n.discharge_date_n IS NULL THEN 'INVALID_DISCHARGE_DATE'
                            ELSE NULL
                        END AS validation_error
                    FROM normalized n
                )
                UPDATE app.stg_mr_statistics_import s
                SET
                    bah = c.bah_n,
                    sjh = c.sjh_n,
                    archive_date = c.archive_date_n,
                    discharge_date = c.discharge_date_n,
                    pages = c.pages_n,
                    source_row_hash = c.row_hash,
                    is_valid = c.validation_error IS NULL,
                    error_code = c.validation_error,
                    error_message = CASE c.validation_error
                        WHEN 'CODE_REQUIRED' THEN '病案号和上架号不能同时为空'
                        WHEN 'INVALID_PAGES' THEN '页数必须是非负整数'
                        WHEN 'INVALID_DATE' THEN '日期必须为 YYYY-MM-DD 或 YYYY/MM/DD'
                        WHEN 'INVALID_DISCHARGE_DATE' THEN '出院日期格式不正确'
                        ELSE NULL
                    END
                FROM checked c
                WHERE s.row_id = c.row_id
                """,
                jobId,
                fileId
        );

        markStatisticsSjhConflicts(jobId, fileId);
    }

    private void markStatisticsSjhConflicts(long jobId, long fileId) {
        jdbcTemplate.update(
                """
                WITH conflicts AS (
                    SELECT sjh
                    FROM app.stg_mr_statistics_import
                    WHERE job_id = ? AND file_id = ? AND is_valid AND sjh IS NOT NULL
                    GROUP BY sjh
                    HAVING COUNT(DISTINCT bah) FILTER (WHERE bah IS NOT NULL) > 1
                )
                UPDATE app.stg_mr_statistics_import s
                SET is_valid = FALSE,
                    error_code = 'SJH_BAH_CONFLICT',
                    error_message = '同一上架号在文件中关联了多个病案号'
                FROM conflicts c
                WHERE s.job_id = ? AND s.file_id = ? AND s.sjh = c.sjh
                """,
                jobId,
                fileId,
                jobId,
                fileId
        );
    }

    private void validateScans(long jobId, long fileId) {
        jdbcTemplate.update(
                """
                WITH normalized AS (
                    SELECT
                        row_id,
                        NULLIF(BTRIM(brxh_raw), '') AS brxh_n,
                        app.normalize_medical_record_code(bah_raw) AS bah_n,
                        app.normalize_medical_record_code(sjh_raw) AS sjh_n,
                        NULLIF(BTRIM(filename_raw), '') AS filename_n,
                        NULLIF(BTRIM(folder_raw), '') AS folder_n,
                        NULLIF(BTRIM(openerno_raw), '') AS opener_n,
                        app.try_parse_date(uploaddate_raw) AS upload_date_n,
                        CASE
                            WHEN NULLIF(BTRIM(btype_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(btype_raw) ~ '^-?\\d+$'
                                 AND BTRIM(btype_raw)::NUMERIC BETWEEN -2147483648 AND 2147483647
                                THEN BTRIM(btype_raw)::INTEGER
                            ELSE NULL
                        END AS btype_n,
                        CASE
                            WHEN NULLIF(BTRIM(pages_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(pages_raw) ~ '^\\d+$' AND BTRIM(pages_raw)::NUMERIC <= 2147483647
                                THEN BTRIM(pages_raw)::INTEGER
                            ELSE NULL
                        END AS pages_n,
                        CASE
                            WHEN NULLIF(BTRIM(uploadflag_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(uploadflag_raw) ~ '^-?\\d+$'
                                 AND BTRIM(uploadflag_raw)::NUMERIC BETWEEN -2147483648 AND 2147483647
                                THEN BTRIM(uploadflag_raw)::INTEGER
                            ELSE NULL
                        END AS uploadflag_n,
                        NULLIF(BTRIM(btype_raw), '') IS NOT NULL AS btype_present,
                        NULLIF(BTRIM(pages_raw), '') IS NOT NULL AS pages_present,
                        NULLIF(BTRIM(uploadflag_raw), '') IS NOT NULL AS uploadflag_present,
                        NULLIF(BTRIM(uploaddate_raw), '') IS NOT NULL AS date_present
                    FROM app.stg_mr_scan_import
                    WHERE job_id = ? AND file_id = ?
                ), checked AS (
                    SELECT
                        n.*,
                        md5(concat_ws(chr(31),
                            COALESCE(n.folder_n, ''), COALESCE(n.brxh_n, ''),
                            COALESCE(n.bah_n, ''), COALESCE(n.filename_n, '')
                        )) AS record_key,
                        CASE
                            WHEN n.sjh_n IS NULL AND n.bah_n IS NULL THEN 'CODE_REQUIRED'
                            WHEN n.brxh_n IS NULL THEN 'BRXH_REQUIRED'
                            WHEN n.filename_n IS NULL THEN 'FILENAME_REQUIRED'
                            WHEN n.folder_n IS NULL THEN 'FOLDER_REQUIRED'
                            WHEN n.pages_present AND n.pages_n IS NULL THEN 'INVALID_PAGES'
                            WHEN n.btype_present AND n.btype_n IS NULL THEN 'INVALID_BTYPE'
                            WHEN n.uploadflag_present AND n.uploadflag_n IS NULL THEN 'INVALID_UPLOAD_FLAG'
                            WHEN n.date_present AND n.upload_date_n IS NULL THEN 'INVALID_DATE'
                            WHEN n.sjh_n IS NULL AND n.bah_n ~ '^\\d+$'
                                 AND n.bah_n::NUMERIC >= 10000000 THEN 'SJH_REQUIRED'
                            ELSE NULL
                        END AS validation_error
                    FROM normalized n
                )
                UPDATE app.stg_mr_scan_import s
                SET
                    brxh = c.brxh_n,
                    bah = c.bah_n,
                    sjh = c.sjh_n,
                    btype = c.btype_n,
                    pages = c.pages_n,
                    uploadflag = c.uploadflag_n,
                    upload_date = c.upload_date_n,
                    source_record_key = c.record_key,
                    is_valid = c.validation_error IS NULL,
                    error_code = c.validation_error,
                    error_message = CASE c.validation_error
                        WHEN 'CODE_REQUIRED' THEN '病案号和上架号不能同时为空'
                        WHEN 'BRXH_REQUIRED' THEN '病人序号不能为空'
                        WHEN 'FILENAME_REQUIRED' THEN '文件名不能为空'
                        WHEN 'FOLDER_REQUIRED' THEN '文件夹不能为空'
                        WHEN 'INVALID_PAGES' THEN '页数必须是非负整数'
                        WHEN 'INVALID_BTYPE' THEN '病案类型必须是整数'
                        WHEN 'INVALID_UPLOAD_FLAG' THEN '上传标记必须是整数'
                        WHEN 'INVALID_DATE' THEN '上传日期格式不正确'
                        WHEN 'SJH_REQUIRED' THEN '病案号达到 10000000 时必须提供上架号'
                        ELSE NULL
                    END
                FROM checked c
                WHERE s.row_id = c.row_id
                """,
                jobId,
                fileId
        );

        markScanCodeConflicts(jobId, fileId);
        prepareScanArchives(jobId, fileId);
    }

    private void markScanCodeConflicts(long jobId, long fileId) {
        jdbcTemplate.update(
                """
                WITH conflicts AS (
                    SELECT sjh
                    FROM app.stg_mr_scan_import
                    WHERE job_id = ? AND file_id = ? AND is_valid AND sjh IS NOT NULL
                    GROUP BY sjh
                    HAVING COUNT(DISTINCT bah) FILTER (WHERE bah IS NOT NULL) > 1
                )
                UPDATE app.stg_mr_scan_import s
                SET is_valid = FALSE,
                    error_code = 'SJH_BAH_CONFLICT',
                    error_message = '同一上架号在文件中关联了多个病案号'
                FROM conflicts c
                WHERE s.job_id = ? AND s.file_id = ? AND s.sjh = c.sjh
                """,
                jobId,
                fileId,
                jobId,
                fileId
        );

        jdbcTemplate.update(
                """
                UPDATE app.stg_mr_scan_import s
                SET is_valid = FALSE,
                    error_code = 'SJH_DATABASE_CONFLICT',
                    error_message = '上架号与数据库现有病案号不一致'
                FROM app.mr_archive a
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                  AND s.sjh = a.sjh
                  AND s.bah IS NOT NULL AND a.bah IS NOT NULL AND s.bah <> a.bah
                """,
                jobId,
                fileId
        );
    }

    private void prepareScanArchives(long jobId, long fileId) {
        jdbcTemplate.update(
                """
                INSERT INTO app.mr_archive AS target (sjh, bah)
                SELECT s.sjh, MIN(s.bah)
                FROM app.stg_mr_scan_import s
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid AND s.sjh IS NOT NULL
                GROUP BY s.sjh
                ON CONFLICT (sjh) WHERE sjh IS NOT NULL DO UPDATE
                SET bah = COALESCE(target.bah, EXCLUDED.bah),
                    updated_at = CURRENT_TIMESTAMP
                """,
                jobId,
                fileId
        );

        jdbcTemplate.update(
                """
                UPDATE app.stg_mr_scan_import s
                SET archive_id = a.id
                FROM app.mr_archive a
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                  AND s.sjh IS NOT NULL AND a.sjh = s.sjh
                """,
                jobId,
                fileId
        );

        jdbcTemplate.update(
                """
                WITH unique_bah AS (
                    SELECT bah, MIN(id) AS archive_id
                    FROM app.mr_archive
                    WHERE bah IS NOT NULL
                    GROUP BY bah
                    HAVING COUNT(*) = 1
                )
                UPDATE app.stg_mr_scan_import s
                SET archive_id = u.archive_id
                FROM unique_bah u
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                  AND s.archive_id IS NULL AND s.sjh IS NULL AND s.bah = u.bah
                """,
                jobId,
                fileId
        );

        jdbcTemplate.update(
                """
                UPDATE app.stg_mr_scan_import
                SET is_valid = FALSE,
                    error_code = 'ARCHIVE_NOT_RESOLVED',
                    error_message = '无法唯一关联病案主数据'
                WHERE job_id = ? AND file_id = ? AND is_valid AND archive_id IS NULL
                """,
                jobId,
                fileId
        );
    }

    private MergeCounts mergeStatistics(DataTransferJob job, DataTransferFile file, long validRows) {
        long candidates = queryLong(
                "SELECT COUNT(DISTINCT source_row_hash) FROM app.stg_mr_statistics_import "
                        + "WHERE job_id = ? AND file_id = ? AND is_valid",
                job.getId(), file.getId()
        );
        long existing = queryLong(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT source_row_hash
                    FROM app.stg_mr_statistics_import
                    WHERE job_id = ? AND file_id = ? AND is_valid
                ) s
                JOIN app.mr_statistics t ON t.source_row_hash = s.source_row_hash
                """,
                job.getId(), file.getId()
        );

        String conflictSql = "UPSERT".equals(job.getImportMode())
                ? """
                  ON CONFLICT (source_row_hash) WHERE source_row_hash IS NOT NULL DO UPDATE SET
                    bah = EXCLUDED.bah,
                    cid = EXCLUDED.cid,
                    openerno = EXCLUDED.openerno,
                    date = EXCLUDED.date,
                    type = EXCLUDED.type,
                    pages = EXCLUDED.pages,
                    sjh = EXCLUDED.sjh,
                    patientname = EXCLUDED.patientname,
                    inpatientdepartment = EXCLUDED.inpatientdepartment,
                    patientid = EXCLUDED.patientid,
                    dischargedate = EXCLUDED.dischargedate,
                    import_job_id = EXCLUDED.import_job_id
                  """
                : "ON CONFLICT (source_row_hash) WHERE source_row_hash IS NOT NULL DO NOTHING";

        int affected = jdbcTemplate.update(
                """
                INSERT INTO app.mr_statistics (
                    bah, cid, openerno, date, type, pages, sjh,
                    patientname, inpatientdepartment, patientid, dischargedate,
                    import_job_id, source_row_hash
                )
                SELECT DISTINCT ON (s.source_row_hash)
                    s.bah,
                    NULLIF(BTRIM(s.cid_raw), ''),
                    NULLIF(BTRIM(s.openerno_raw), ''),
                    CASE WHEN s.archive_date IS NULL THEN NULL ELSE TO_CHAR(s.archive_date, 'YYYY-MM-DD') END,
                    NULLIF(BTRIM(s.type_raw), ''),
                    s.pages,
                    s.sjh,
                    NULLIF(BTRIM(s.patientname_raw), ''),
                    NULLIF(BTRIM(s.inpatientdepartment_raw), ''),
                    NULLIF(BTRIM(s.patientid_raw), ''),
                    CASE WHEN s.discharge_date IS NULL THEN NULL ELSE TO_CHAR(s.discharge_date, 'YYYY-MM-DD') END,
                    ?,
                    s.source_row_hash
                FROM app.stg_mr_statistics_import s
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                ORDER BY s.source_row_hash, s.row_id DESC
                """ + conflictSql,
                job.getId(),
                job.getId(),
                file.getId()
        );

        long duplicateRows = Math.max(0, validRows - candidates);
        if ("UPSERT".equals(job.getImportMode())) {
            return new MergeCounts(Math.max(0, candidates - existing), existing, duplicateRows);
        }
        return new MergeCounts(affected, 0, Math.max(0, validRows - affected));
    }

    private MergeCounts mergeScans(DataTransferJob job, DataTransferFile file, long validRows) {
        long candidates = queryLong(
                "SELECT COUNT(DISTINCT source_record_key) FROM app.stg_mr_scan_import "
                        + "WHERE job_id = ? AND file_id = ? AND is_valid",
                job.getId(), file.getId()
        );
        long existing = queryLong(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT source_record_key
                    FROM app.stg_mr_scan_import
                    WHERE job_id = ? AND file_id = ? AND is_valid
                ) s
                JOIN app.mr_scan t ON t.source_record_key = s.source_record_key
                """,
                job.getId(), file.getId()
        );

        String conflictSql = "UPSERT".equals(job.getImportMode())
                ? """
                  ON CONFLICT (source_record_key) WHERE source_record_key IS NOT NULL DO UPDATE SET
                    archive_id = EXCLUDED.archive_id,
                    brxh = EXCLUDED.brxh,
                    bah = EXCLUDED.bah,
                    sjh = EXCLUDED.sjh,
                    filename = EXCLUDED.filename,
                    btype = EXCLUDED.btype,
                    pages = EXCLUDED.pages,
                    openerno = EXCLUDED.openerno,
                    uploaddate = EXCLUDED.uploaddate,
                    uploadflag = EXCLUDED.uploadflag,
                    folder = EXCLUDED.folder,
                    import_job_id = EXCLUDED.import_job_id
                  """
                : "ON CONFLICT (source_record_key) WHERE source_record_key IS NOT NULL DO NOTHING";

        int affected = jdbcTemplate.update(
                """
                INSERT INTO app.mr_scan (
                    archive_id, brxh, bah, sjh, filename, btype, pages,
                    openerno, uploaddate, uploadflag, folder,
                    import_job_id, source_record_key
                )
                SELECT DISTINCT ON (s.source_record_key)
                    s.archive_id,
                    s.brxh,
                    s.bah,
                    s.sjh,
                    NULLIF(BTRIM(s.filename_raw), ''),
                    s.btype,
                    s.pages,
                    NULLIF(BTRIM(s.openerno_raw), ''),
                    CASE WHEN s.upload_date IS NULL THEN NULL ELSE TO_CHAR(s.upload_date, 'YYYY-MM-DD') END,
                    s.uploadflag,
                    NULLIF(BTRIM(s.folder_raw), ''),
                    ?,
                    s.source_record_key
                FROM app.stg_mr_scan_import s
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                ORDER BY s.source_record_key, s.row_id DESC
                """ + conflictSql,
                job.getId(),
                job.getId(),
                file.getId()
        );

        long duplicateRows = Math.max(0, validRows - candidates);
        if ("UPSERT".equals(job.getImportMode())) {
            return new MergeCounts(Math.max(0, candidates - existing), existing, duplicateRows);
        }
        return new MergeCounts(affected, 0, Math.max(0, validRows - affected));
    }

    private RowCounts loadValidationCounts(String entityType, long jobId, long fileId) {
        String table = stagingTable(entityType);
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*), COUNT(*) FILTER (WHERE is_valid), COUNT(*) FILTER (WHERE NOT is_valid) "
                        + "FROM " + table + " WHERE job_id = ? AND file_id = ?",
                (rs, rowNum) -> new RowCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                jobId,
                fileId
        );
    }

    private void saveErrorSamples(String entityType, long jobId, long fileId) {
        String rawJson = "MR_STATISTICS".equals(entityType)
                ? "jsonb_build_object('bah', bah_raw, 'cid', cid_raw, 'openerno', openerno_raw, "
                  + "'date', date_raw, 'type', type_raw, 'pages', pages_raw, 'sjh', sjh_raw, "
                  + "'patientname', patientname_raw, 'inpatientdepartment', inpatientdepartment_raw, "
                  + "'patientid', patientid_raw, 'dischargedate', dischargedate_raw)"
                : "jsonb_build_object('brxh', brxh_raw, 'bah', bah_raw, 'sjh', sjh_raw, "
                  + "'filename', filename_raw, 'btype', btype_raw, 'pages', pages_raw, "
                  + "'openerno', openerno_raw, 'uploaddate', uploaddate_raw, "
                  + "'uploadflag', uploadflag_raw, 'folder', folder_raw)";
        String table = stagingTable(entityType);
        jdbcTemplate.update(
                "INSERT INTO app.data_transfer_error "
                        + "(job_id, file_id, source_row_no, error_code, error_message, raw_data) "
                        + "SELECT ?, ?, ROW_NUMBER() OVER (ORDER BY row_id) + 1, error_code, error_message, "
                        + rawJson + " FROM " + table
                        + " WHERE job_id = ? AND file_id = ? AND NOT is_valid "
                        + "ORDER BY row_id LIMIT ?",
                jobId,
                fileId,
                jobId,
                fileId,
                properties.getMaxErrorSamples()
        );
    }

    private void writeErrorReport(String entityType, long jobId, long fileId) throws Exception {
        String table = stagingTable(entityType);
        String columns = "MR_STATISTICS".equals(entityType)
                ? "bah_raw AS bah, cid_raw AS cid, openerno_raw AS openerno, date_raw AS date, "
                  + "type_raw AS type, pages_raw AS pages, sjh_raw AS sjh, patientname_raw AS patientname, "
                  + "inpatientdepartment_raw AS inpatientdepartment, patientid_raw AS patientid, "
                  + "dischargedate_raw AS dischargedate"
                : "brxh_raw AS brxh, bah_raw AS bah, sjh_raw AS sjh, filename_raw AS filename, "
                  + "btype_raw AS btype, pages_raw AS pages, openerno_raw AS openerno, "
                  + "uploaddate_raw AS uploaddate, uploadflag_raw AS uploadflag, folder_raw AS folder";
        String copySql = "COPY (SELECT ROW_NUMBER() OVER (ORDER BY row_id) + 1 AS source_row_no, "
                + "error_code, error_message, " + columns
                + " FROM " + table
                + " WHERE job_id = " + jobId + " AND file_id = " + fileId + " AND NOT is_valid"
                + " ORDER BY row_id) TO STDOUT WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')";

        Path report = storageService.createErrorReportPath(jobId, fileId);
        try (Connection connection = dataSource.getConnection();
             OutputStream fileOutput = Files.newOutputStream(report);
             GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput, 1024 * 1024)) {
            connection.unwrap(PGConnection.class).getCopyAPI().copyOut(copySql, gzipOutput);
            gzipOutput.finish();
        }
    }

    private void processExport(DataTransferJob job) throws Exception {
        JsonNode options = objectMapper.readTree(job.getOptions() == null ? "{}" : job.getOptions());
        long startId = options.path("startId").isNumber() ? options.path("startId").asLong() : 0L;
        Long requestedEnd = options.path("endId").isNumber() ? options.path("endId").asLong() : null;
        int rowsPerPart = options.path("rowsPerPart").asInt(properties.getExportRowsPerPart());
        rowsPerPart = Math.max(10_000, Math.min(rowsPerPart, 2_000_000));

        String table = "MR_STATISTICS".equals(job.getEntityType()) ? "app.mr_statistics" : "app.mr_scan";
        long maximumId = requestedEnd == null
                ? queryLong("SELECT COALESCE(MAX(id), 0) FROM " + table)
                : requestedEnd;
        long totalRows = queryLong(
                "SELECT COUNT(*) FROM " + table + " WHERE id > ? AND id <= ?",
                startId,
                maximumId
        );
        int totalFiles = totalRows == 0 ? 0 : (int) Math.ceil(totalRows / (double) rowsPerPart);
        repository.setJobFileCount(job.getId(), totalFiles);
        repository.markJobStarted(job.getId(), "EXPORTING", "生成导出文件");

        if (totalRows == 0) {
            repository.updateJobStatus(job.getId(), "COMPLETED", "没有符合条件的数据", null);
            return;
        }

        long cursor = startId;
        int sequence = 1;
        while (cursor < maximumId) {
            String status = repository.findJobStatus(job.getId());
            if ("PAUSED".equals(status) || "CANCELLED".equals(status)) {
                return;
            }

            ExportRange range = findExportRange(table, cursor, maximumId, rowsPerPart);
            if (range == null || range.count() == 0) {
                break;
            }
            repository.updateJobCurrentFile(job.getId(), sequence, "导出 CSV 分卷");
            exportPart(job, sequence, cursor, range);
            cursor = range.lastId();
            sequence++;
            repository.refreshJobTotals(job.getId());
        }

        repository.updateJobStatus(job.getId(), "COMPLETED", "导出完成", null);
    }

    private ExportRange findExportRange(String table, long cursor, long maximumId, int limit) {
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

    private void exportPart(DataTransferJob job, int sequence, long cursor, ExportRange range) throws Exception {
        String prefix = "MR_STATISTICS".equals(job.getEntityType()) ? "mr-statistics" : "mr-scan";
        String filename = String.format(Locale.ROOT, "%s-part-%04d.csv.gz", prefix, sequence);
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

        String selectColumns = "MR_STATISTICS".equals(job.getEntityType())
                ? "bah, cid, openerno, date, type, pages, sjh, patientname, "
                  + "inpatientdepartment, patientid, dischargedate"
                : "brxh, bah, sjh, filename, btype, pages, openerno, uploaddate, uploadflag, folder";
        String table = "MR_STATISTICS".equals(job.getEntityType()) ? "app.mr_statistics" : "app.mr_scan";
        String copySql = "COPY (SELECT " + selectColumns + " FROM " + table
                + " WHERE id > " + cursor + " AND id <= " + range.lastId()
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
                    range.count(),
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

    private void clearStaging(String entityType, long jobId, long fileId) {
        jdbcTemplate.update(
                "DELETE FROM " + stagingTable(entityType) + " WHERE job_id = ? AND file_id = ?",
                jobId,
                fileId
        );
    }

    private String stagingTable(String entityType) {
        return "MR_STATISTICS".equals(entityType)
                ? "app.stg_mr_statistics_import"
                : "app.stg_mr_scan_import";
    }

    private String expectedHeader(String entityType) {
        return "MR_STATISTICS".equals(entityType) ? STATISTICS_HEADER : SCAN_HEADER;
    }

    private void validateHeader(Path path, String expected) throws IOException {
        try (InputStream inputStream = openCsvInput(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("CSV 文件为空");
            }
            String normalized = normalizeHeader(header);
            if (!expected.equals(normalized)) {
                throw new IllegalArgumentException(
                        "CSV 表头不匹配。期望：" + expected + "；实际：" + normalized
                );
            }
        }
    }

    private String normalizeHeader(String header) {
        String value = header.replace("\uFEFF", "").trim();
        List<String> columns = new ArrayList<>();
        Arrays.stream(value.split(",", -1)).forEach(column -> {
            String normalized = column.trim();
            if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
                normalized = normalized.substring(1, normalized.length() - 1).replace("\"\"", "\"");
            }
            columns.add(normalized.toLowerCase(Locale.ROOT));
        });
        return String.join(",", columns);
    }

    private InputStream openCsvInput(Path path) throws IOException {
        InputStream raw = Files.newInputStream(path);
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return new GZIPInputStream(raw, 1024 * 1024);
        }
        return raw;
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

    private record RowCounts(long total, long valid, long invalid) {
    }

    private record MergeCounts(long inserted, long updated, long skipped) {
    }

    private record ExportRange(long firstId, long lastId, long count) {
    }
}
