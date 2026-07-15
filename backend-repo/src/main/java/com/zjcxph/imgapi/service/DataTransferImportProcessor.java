package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.config.DataTransferProperties;
import com.zjcxph.imgapi.entity.DataTransferFile;
import com.zjcxph.imgapi.entity.DataTransferJob;
import com.zjcxph.imgapi.repository.DataTransferRepository;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
public class DataTransferImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataTransferImportProcessor.class);

    private static final List<CsvLayout> LAYOUTS = List.of(
            new CsvLayout(
                    "STATISTICS_FULL_EN",
                    "MR_STATISTICS",
                    "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate",
                    "bah_raw, cid_raw, openerno_raw, date_raw, type_raw, pages_raw, sjh_raw, "
                            + "patientname_raw, inpatientdepartment_raw, patientid_raw, dischargedate_raw"
            ),
            new CsvLayout(
                    "STATISTICS_FULL_ZH",
                    "MR_STATISTICS",
                    "上架号,病案号,设备id,操作人,页数,日期,类型,姓名,科室,病人id,出院日期",
                    "sjh_raw, bah_raw, cid_raw, openerno_raw, pages_raw, date_raw, type_raw, "
                            + "patientname_raw, inpatientdepartment_raw, patientid_raw, dischargedate_raw"
            ),
            new CsvLayout(
                    "STATISTICS_FULL_ZH_ALT",
                    "MR_STATISTICS",
                    "病案号,设备id,操作人,日期,类型,页数,上架号,姓名,科室,病人id,出院日期",
                    "bah_raw, cid_raw, openerno_raw, date_raw, type_raw, pages_raw, sjh_raw, "
                            + "patientname_raw, inpatientdepartment_raw, patientid_raw, dischargedate_raw"
            ),
            new CsvLayout(
                    "STATISTICS_LEGACY_EN",
                    "MR_STATISTICS",
                    "sjh,bah,cid,openerno,pages,date,type",
                    "sjh_raw, bah_raw, cid_raw, openerno_raw, pages_raw, date_raw, type_raw"
            ),
            new CsvLayout(
                    "STATISTICS_LEGACY_ZH",
                    "MR_STATISTICS",
                    "上架号,病案号,设备id,操作人,页数,日期,类型",
                    "sjh_raw, bah_raw, cid_raw, openerno_raw, pages_raw, date_raw, type_raw"
            ),
            new CsvLayout(
                    "SCAN_FULL_EN",
                    "MR_SCAN",
                    "brxh,bah,sjh,filename,btype,pages,openerno,uploaddate,uploadflag,folder,file_size",
                    "brxh_raw, bah_raw, sjh_raw, filename_raw, btype_raw, pages_raw, openerno_raw, "
                            + "uploaddate_raw, uploadflag_raw, folder_raw, file_size_raw"
            ),
            new CsvLayout(
                    "SCAN_FULL_EN_NO_SIZE",
                    "MR_SCAN",
                    "brxh,bah,sjh,filename,btype,pages,openerno,uploaddate,uploadflag,folder",
                    "brxh_raw, bah_raw, sjh_raw, filename_raw, btype_raw, pages_raw, openerno_raw, "
                            + "uploaddate_raw, uploadflag_raw, folder_raw"
            ),
            new CsvLayout(
                    "SCAN_LEGACY_EN",
                    "MR_SCAN",
                    "sjh,bah,brxh,folder,filename,btype,file_size",
                    "sjh_raw, bah_raw, brxh_raw, folder_raw, filename_raw, btype_raw, file_size_raw"
            ),
            new CsvLayout(
                    "SCAN_LEGACY_ZH",
                    "MR_SCAN",
                    "上架号,病案号,病人序号,文件夹,文件名,病案类型,文件大小",
                    "sjh_raw, bah_raw, brxh_raw, folder_raw, filename_raw, btype_raw, file_size_raw"
            )
    );

    private final DataTransferRepository repository;
    private final DataTransferStorageService storageService;
    private final DataTransferProperties properties;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public DataTransferImportProcessor(
            DataTransferRepository repository,
            DataTransferStorageService storageService,
            DataTransferProperties properties,
            DataSource dataSource,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.properties = properties;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void process(DataTransferJob job) {
        List<DataTransferFile> files = repository.findRunnableFiles(job.getId());
        if (files.isEmpty()) {
            finishJob(job.getId());
            return;
        }

        for (DataTransferFile file : files) {
            String status = repository.findJobStatus(job.getId());
            if ("PAUSED".equals(status) || "CANCELLED".equals(status)) {
                return;
            }

            repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "导入文件");
            jdbcTemplate.update("DELETE FROM app.data_transfer_error WHERE file_id = ?", file.getId());
            try {
                processFile(job, file);
            }
            catch (Exception exception) {
                log.error("Import file failed: jobId={}, fileId={}", job.getId(), file.getId(), exception);
                repository.updateFileStatus(file.getId(), "FAILED", safeMessage(exception));
            }
            repository.refreshJobTotals(job.getId());
        }
        finishJob(job.getId());
    }

    private void processFile(DataTransferJob job, DataTransferFile file) throws Exception {
        Path path = storageService.resolveStoredPath(file.getStoredPath());
        CsvLayout layout = resolveLayout(job.getEntityType(), path);

        repository.updateFileStatus(file.getId(), "IMPORTING", null);
        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "COPY 到暂存表（" + layout.name() + "）");
        clearStaging(job.getEntityType(), job.getId(), file.getId());
        long copiedRows = copyIntoStaging(layout, job.getId(), file.getId(), path);

        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "校验与规范化");
        if ("MR_STATISTICS".equals(job.getEntityType())) {
            validateStatistics(job.getId(), file.getId());
        }
        else {
            validateScans(job.getId(), file.getId());
        }

        RowCounts counts = loadValidationCounts(job.getEntityType(), job.getId(), file.getId());
        saveErrorSamples(job.getEntityType(), job.getId(), file.getId());
        if (counts.invalid() > 0) {
            writeErrorReport(job.getEntityType(), job.getId(), file.getId());
        }

        repository.updateJobCurrentFile(job.getId(), file.getSequenceNo(), "合并业务数据");
        MergeCounts merged = "MR_STATISTICS".equals(job.getEntityType())
                ? mergeStatistics(job, file, counts.valid())
                : mergeScans(job, file, counts.valid());

        repository.updateFileCounts(
                file.getId(), copiedRows, counts.valid(), counts.invalid(),
                merged.inserted(), merged.updated(), merged.skipped()
        );
        repository.updateFileStatus(
                file.getId(),
                counts.invalid() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED",
                null
        );
        clearStaging(job.getEntityType(), job.getId(), file.getId());
    }

    private CsvLayout resolveLayout(String entityType, Path path) throws IOException {
        String actual;
        try (InputStream inputStream = openCsvInput(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("CSV 文件为空");
            }
            actual = normalizeHeader(header);
        }

        return LAYOUTS.stream()
                .filter(layout -> layout.entityType().equals(entityType))
                .filter(layout -> layout.header().equals(actual))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的 CSV 表头：" + actual + "。请下载标准模板，或使用系统支持的历史列顺序。"
                ));
    }

    private long copyIntoStaging(CsvLayout layout, long jobId, long fileId, Path path) throws Exception {
        String table = stagingTable(layout.entityType());
        String copySql = "COPY " + table + " (" + layout.copyColumns() + ") "
                + "FROM STDIN WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')";

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

    private void setSessionValue(Connection connection, String name, String value) throws Exception {
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
                            WHEN BTRIM(pages_raw) ~ '^[0-9]+$'
                                 AND BTRIM(pages_raw)::NUMERIC <= 2147483647
                                THEN BTRIM(pages_raw)::INTEGER
                            ELSE NULL
                        END AS pages_n,
                        NULLIF(BTRIM(cid_raw), '') AS cid_n,
                        NULLIF(BTRIM(type_raw), '') AS type_n,
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
        markSjhConflicts("app.stg_mr_statistics_import", jobId, fileId);
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
                        app.try_parse_date(uploaddate_raw) AS upload_date_n,
                        CASE
                            WHEN NULLIF(BTRIM(btype_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(btype_raw) ~ '^-?[0-9]+$'
                                 AND BTRIM(btype_raw)::NUMERIC BETWEEN -2147483648 AND 2147483647
                                THEN BTRIM(btype_raw)::INTEGER
                            ELSE NULL
                        END AS btype_n,
                        CASE
                            WHEN NULLIF(BTRIM(pages_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(pages_raw) ~ '^[0-9]+$'
                                 AND BTRIM(pages_raw)::NUMERIC <= 2147483647
                                THEN BTRIM(pages_raw)::INTEGER
                            ELSE NULL
                        END AS pages_n,
                        CASE
                            WHEN NULLIF(BTRIM(uploadflag_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(uploadflag_raw) ~ '^-?[0-9]+$'
                                 AND BTRIM(uploadflag_raw)::NUMERIC BETWEEN -2147483648 AND 2147483647
                                THEN BTRIM(uploadflag_raw)::INTEGER
                            ELSE NULL
                        END AS uploadflag_n,
                        CASE
                            WHEN NULLIF(BTRIM(file_size_raw), '') IS NULL THEN NULL
                            WHEN BTRIM(file_size_raw) ~ '^[0-9]+$'
                                 AND BTRIM(file_size_raw)::NUMERIC <= 9223372036854775807
                                THEN BTRIM(file_size_raw)::BIGINT
                            ELSE NULL
                        END AS file_size_n,
                        NULLIF(BTRIM(btype_raw), '') IS NOT NULL AS btype_present,
                        NULLIF(BTRIM(pages_raw), '') IS NOT NULL AS pages_present,
                        NULLIF(BTRIM(uploadflag_raw), '') IS NOT NULL AS uploadflag_present,
                        NULLIF(BTRIM(file_size_raw), '') IS NOT NULL AS file_size_present,
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
                            WHEN n.file_size_present AND n.file_size_n IS NULL THEN 'INVALID_FILE_SIZE'
                            WHEN n.date_present AND n.upload_date_n IS NULL THEN 'INVALID_DATE'
                            WHEN n.sjh_n IS NULL AND n.bah_n ~ '^[0-9]+$'
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
                    file_size = c.file_size_n,
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
                        WHEN 'INVALID_FILE_SIZE' THEN '文件大小必须是非负整数'
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
        markSjhConflicts("app.stg_mr_scan_import", jobId, fileId);
        markDatabaseCodeConflicts(jobId, fileId);
        prepareScanArchives(jobId, fileId);
    }

    private void markSjhConflicts(String table, long jobId, long fileId) {
        jdbcTemplate.update(
                "WITH conflicts AS (SELECT sjh FROM " + table
                        + " WHERE job_id = ? AND file_id = ? AND is_valid AND sjh IS NOT NULL"
                        + " GROUP BY sjh HAVING COUNT(DISTINCT bah) FILTER (WHERE bah IS NOT NULL) > 1)"
                        + " UPDATE " + table + " s SET is_valid = FALSE,"
                        + " error_code = 'SJH_BAH_CONFLICT',"
                        + " error_message = '同一上架号在文件中关联了多个病案号'"
                        + " FROM conflicts c WHERE s.job_id = ? AND s.file_id = ? AND s.sjh = c.sjh",
                jobId,
                fileId,
                jobId,
                fileId
        );
    }

    private void markDatabaseCodeConflicts(long jobId, long fileId) {
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
                "SELECT COUNT(*) FROM (SELECT DISTINCT source_row_hash FROM app.stg_mr_statistics_import "
                        + "WHERE job_id = ? AND file_id = ? AND is_valid) s "
                        + "JOIN app.mr_statistics t ON t.source_row_hash = s.source_row_hash",
                job.getId(), file.getId()
        );

        String conflict = "UPSERT".equals(job.getImportMode())
                ? """
                  ON CONFLICT (source_row_hash) WHERE source_row_hash IS NOT NULL DO UPDATE SET
                    bah = EXCLUDED.bah, cid = EXCLUDED.cid, openerno = EXCLUDED.openerno,
                    date = EXCLUDED.date, type = EXCLUDED.type, pages = EXCLUDED.pages,
                    sjh = EXCLUDED.sjh, patientname = EXCLUDED.patientname,
                    inpatientdepartment = EXCLUDED.inpatientdepartment,
                    patientid = EXCLUDED.patientid, dischargedate = EXCLUDED.dischargedate,
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
                    s.bah, NULLIF(BTRIM(s.cid_raw), ''), NULLIF(BTRIM(s.openerno_raw), ''),
                    CASE WHEN s.archive_date IS NULL THEN NULL ELSE TO_CHAR(s.archive_date, 'YYYY-MM-DD') END,
                    NULLIF(BTRIM(s.type_raw), ''), s.pages, s.sjh,
                    NULLIF(BTRIM(s.patientname_raw), ''),
                    NULLIF(BTRIM(s.inpatientdepartment_raw), ''),
                    NULLIF(BTRIM(s.patientid_raw), ''),
                    CASE WHEN s.discharge_date IS NULL THEN NULL ELSE TO_CHAR(s.discharge_date, 'YYYY-MM-DD') END,
                    ?, s.source_row_hash
                FROM app.stg_mr_statistics_import s
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                ORDER BY s.source_row_hash, s.row_id DESC
                """ + conflict,
                job.getId(), job.getId(), file.getId()
        );

        if ("UPSERT".equals(job.getImportMode())) {
            return new MergeCounts(Math.max(0, candidates - existing), existing, Math.max(0, validRows - candidates));
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
                "SELECT COUNT(*) FROM (SELECT DISTINCT source_record_key FROM app.stg_mr_scan_import "
                        + "WHERE job_id = ? AND file_id = ? AND is_valid) s "
                        + "JOIN app.mr_scan t ON t.source_record_key = s.source_record_key",
                job.getId(), file.getId()
        );

        String conflict = "UPSERT".equals(job.getImportMode())
                ? """
                  ON CONFLICT (source_record_key) WHERE source_record_key IS NOT NULL DO UPDATE SET
                    archive_id = EXCLUDED.archive_id, brxh = EXCLUDED.brxh,
                    bah = EXCLUDED.bah, sjh = EXCLUDED.sjh, filename = EXCLUDED.filename,
                    btype = EXCLUDED.btype, pages = EXCLUDED.pages, openerno = EXCLUDED.openerno,
                    uploaddate = EXCLUDED.uploaddate, uploadflag = EXCLUDED.uploadflag,
                    folder = EXCLUDED.folder, file_size = EXCLUDED.file_size,
                    import_job_id = EXCLUDED.import_job_id
                  """
                : "ON CONFLICT (source_record_key) WHERE source_record_key IS NOT NULL DO NOTHING";

        int affected = jdbcTemplate.update(
                """
                INSERT INTO app.mr_scan (
                    archive_id, brxh, bah, sjh, filename, btype, pages,
                    openerno, uploaddate, uploadflag, folder, file_size,
                    import_job_id, source_record_key
                )
                SELECT DISTINCT ON (s.source_record_key)
                    s.archive_id, s.brxh, s.bah, s.sjh,
                    NULLIF(BTRIM(s.filename_raw), ''), s.btype, s.pages,
                    NULLIF(BTRIM(s.openerno_raw), ''),
                    CASE WHEN s.upload_date IS NULL THEN NULL ELSE TO_CHAR(s.upload_date, 'YYYY-MM-DD') END,
                    s.uploadflag, NULLIF(BTRIM(s.folder_raw), ''), s.file_size,
                    ?, s.source_record_key
                FROM app.stg_mr_scan_import s
                WHERE s.job_id = ? AND s.file_id = ? AND s.is_valid
                ORDER BY s.source_record_key, s.row_id DESC
                """ + conflict,
                job.getId(), job.getId(), file.getId()
        );

        if ("UPSERT".equals(job.getImportMode())) {
            return new MergeCounts(Math.max(0, candidates - existing), existing, Math.max(0, validRows - candidates));
        }
        return new MergeCounts(affected, 0, Math.max(0, validRows - affected));
    }

    private RowCounts loadValidationCounts(String entityType, long jobId, long fileId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*), COUNT(*) FILTER (WHERE is_valid), "
                        + "COUNT(*) FILTER (WHERE NOT is_valid) FROM " + stagingTable(entityType)
                        + " WHERE job_id = ? AND file_id = ?",
                (rs, rowNum) -> new RowCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
                jobId,
                fileId
        );
    }

    private void saveErrorSamples(String entityType, long jobId, long fileId) {
        String table = stagingTable(entityType);
        String rawJson = rawJsonExpression(entityType);
        jdbcTemplate.update(
                "INSERT INTO app.data_transfer_error "
                        + "(job_id, file_id, source_row_no, error_code, error_message, raw_data) "
                        + "SELECT ?, ?, source_row_no, error_code, error_message, raw_data FROM ("
                        + "SELECT ROW_NUMBER() OVER (ORDER BY row_id) + 1 AS source_row_no, row_id, "
                        + "is_valid, error_code, error_message, " + rawJson + " AS raw_data "
                        + "FROM " + table + " WHERE job_id = ? AND file_id = ?) numbered "
                        + "WHERE NOT is_valid ORDER BY row_id LIMIT ?",
                jobId,
                fileId,
                jobId,
                fileId,
                properties.getMaxErrorSamples()
        );
    }

    private void writeErrorReport(String entityType, long jobId, long fileId) throws Exception {
        String table = stagingTable(entityType);
        String columns = rawCsvColumns(entityType);
        String copySql = "COPY (SELECT source_row_no, error_code, error_message, " + columns
                + " FROM (SELECT ROW_NUMBER() OVER (ORDER BY row_id) + 1 AS source_row_no, *"
                + " FROM " + table + " WHERE job_id = " + jobId + " AND file_id = " + fileId
                + ") numbered WHERE NOT is_valid ORDER BY row_id)"
                + " TO STDOUT WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')";
        Path report = storageService.createErrorReportPath(jobId, fileId);
        try (Connection connection = dataSource.getConnection();
             OutputStream outputStream = Files.newOutputStream(report);
             GZIPOutputStream gzip = new GZIPOutputStream(outputStream, 1024 * 1024)) {
            connection.unwrap(PGConnection.class).getCopyAPI().copyOut(copySql, gzip);
            gzip.finish();
        }
    }

    private String rawJsonExpression(String entityType) {
        if ("MR_STATISTICS".equals(entityType)) {
            return "jsonb_build_object('bah', bah_raw, 'cid', cid_raw, 'openerno', openerno_raw, "
                    + "'date', date_raw, 'type', type_raw, 'pages', pages_raw, 'sjh', sjh_raw, "
                    + "'patientname', patientname_raw, 'inpatientdepartment', inpatientdepartment_raw, "
                    + "'patientid', patientid_raw, 'dischargedate', dischargedate_raw)";
        }
        return "jsonb_build_object('brxh', brxh_raw, 'bah', bah_raw, 'sjh', sjh_raw, "
                + "'filename', filename_raw, 'btype', btype_raw, 'pages', pages_raw, "
                + "'openerno', openerno_raw, 'uploaddate', uploaddate_raw, "
                + "'uploadflag', uploadflag_raw, 'folder', folder_raw, 'file_size', file_size_raw)";
    }

    private String rawCsvColumns(String entityType) {
        if ("MR_STATISTICS".equals(entityType)) {
            return "bah_raw AS bah, cid_raw AS cid, openerno_raw AS openerno, date_raw AS date, "
                    + "type_raw AS type, pages_raw AS pages, sjh_raw AS sjh, "
                    + "patientname_raw AS patientname, inpatientdepartment_raw AS inpatientdepartment, "
                    + "patientid_raw AS patientid, dischargedate_raw AS dischargedate";
        }
        return "brxh_raw AS brxh, bah_raw AS bah, sjh_raw AS sjh, filename_raw AS filename, "
                + "btype_raw AS btype, pages_raw AS pages, openerno_raw AS openerno, "
                + "uploaddate_raw AS uploaddate, uploadflag_raw AS uploadflag, folder_raw AS folder, "
                + "file_size_raw AS file_size";
    }

    private void finishJob(long jobId) {
        repository.refreshJobTotals(jobId);
        long failed = queryLong(
                "SELECT COUNT(*) FROM app.data_transfer_file WHERE job_id = ? AND status = 'FAILED'",
                jobId
        );
        long invalid = queryLong(
                "SELECT COALESCE(SUM(invalid_rows), 0) FROM app.data_transfer_file WHERE job_id = ?",
                jobId
        );
        if (failed > 0) {
            repository.updateJobStatus(jobId, "FAILED", "存在失败文件", failed + " 个文件失败，可重试失败文件");
        }
        else if (invalid > 0) {
            repository.updateJobStatus(jobId, "COMPLETED_WITH_ERRORS", "导入完成", invalid + " 行数据未导入");
        }
        else {
            repository.updateJobStatus(jobId, "COMPLETED", "导入完成", null);
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

    private InputStream openCsvInput(Path path) throws IOException {
        InputStream raw = Files.newInputStream(path);
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            return new GZIPInputStream(raw, 1024 * 1024);
        }
        return raw;
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

    private record CsvLayout(String name, String entityType, String header, String copyColumns) {
    }

    private record RowCounts(long total, long valid, long invalid) {
    }

    private record MergeCounts(long inserted, long updated, long skipped) {
    }
}
