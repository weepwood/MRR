package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.service.importer.DataExchangeImportSupport;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScanDataExchangeService {

    public static final String DATASET = "MR_SCAN";
    public static final List<String> TEMPLATE_HEADERS = List.of(
            "sjh",
            "bah",
            "brxh",
            "folder",
            "filename",
            "btype",
            "filesize"
    );

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final int MAX_ROWS = 100_000;
    private static final int JDBC_BATCH_SIZE = 1_000;
    private static final Set<String> REQUIRED_HEADERS = new LinkedHashSet<>(TEMPLATE_HEADERS);

    private final JdbcTemplate jdbcTemplate;
    private final TabularImportFileReader fileReader;

    public ScanDataExchangeService(
            JdbcTemplate jdbcTemplate,
            TabularImportFileReader fileReader
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileReader = fileReader;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataExchangeImportResult importScans(MultipartFile file, boolean dryRun) throws IOException {
        TabularImportFileReader.ParsedTable parsed = fileReader.read(file, MAX_FILE_SIZE, MAX_ROWS);
        DataExchangeImportSupport.ErrorCollector errors = new DataExchangeImportSupport.ErrorCollector();
        List<String> headers = DataExchangeImportSupport.validateHeaders(
                parsed.headers(),
                REQUIRED_HEADERS,
                Set.of(),
                "扫描记录模板",
                errors
        );
        if (errors.hasErrors()) {
            return result(file, parsed.encoding(), dryRun, false, 0, 0, 0, 0, 0, errors);
        }

        LinkedHashMap<String, ScanImportRow> uniqueRows = new LinkedHashMap<>();
        Map<String, String> pathFingerprints = new LinkedHashMap<>();
        int totalRows = 0;
        int fileDuplicateRows = 0;

        for (TabularImportFileReader.SourceRow sourceRow : parsed.rows()) {
            if (DataExchangeImportSupport.isBlankRow(sourceRow.values())) {
                continue;
            }
            totalRows++;
            Map<String, String> values = DataExchangeImportSupport.toValueMap(
                    headers,
                    sourceRow.values(),
                    Set.of()
            );
            ScanImportRow row = normalizeRow(sourceRow.rowNumber(), values, errors);
            if (row == null) {
                continue;
            }

            String fingerprint = row.fingerprint();
            String pathKey = row.pathKey();
            String existingFingerprint = pathFingerprints.putIfAbsent(pathKey, fingerprint);
            if (existingFingerprint != null && !existingFingerprint.equals(fingerprint)) {
                errors.add(
                        sourceRow.rowNumber(),
                        "folder/filename",
                        "同一目录和文件名在文件中对应多组不同扫描数据",
                        row.folder() + "/" + row.filename()
                );
                continue;
            }

            if (uniqueRows.containsKey(fingerprint)) {
                fileDuplicateRows++;
            } else {
                uniqueRows.put(fingerprint, row.withSequence(uniqueRows.size() + 1));
            }
        }

        if (errors.hasErrors()) {
            return result(
                    file,
                    parsed.encoding(),
                    dryRun,
                    false,
                    totalRows,
                    uniqueRows.size(),
                    0,
                    0,
                    fileDuplicateRows,
                    errors
            );
        }

        ImportOutcome outcome = stageAndImport(new ArrayList<>(uniqueRows.values()), dryRun, errors);
        if (errors.hasErrors()) {
            return result(
                    file,
                    parsed.encoding(),
                    dryRun,
                    false,
                    totalRows,
                    uniqueRows.size(),
                    0,
                    0,
                    fileDuplicateRows,
                    errors
            );
        }
        return result(
                file,
                parsed.encoding(),
                dryRun,
                true,
                totalRows,
                uniqueRows.size(),
                outcome.insertedRows(),
                outcome.updatedRows(),
                fileDuplicateRows + outcome.databaseDuplicateRows(),
                errors
        );
    }

    private ScanImportRow normalizeRow(
            int rowNumber,
            Map<String, String> values,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        int before = errors.size();
        String sjh = DataExchangeImportSupport.normalizeMedicalRecordCode(values.get("sjh"));
        String bah = DataExchangeImportSupport.normalizeMedicalRecordCode(values.get("bah"));
        String brxh = DataExchangeImportSupport.normalizeText(values.get("brxh"));
        String folder = DataExchangeImportSupport.normalizeText(values.get("folder"));
        String filename = DataExchangeImportSupport.normalizeText(values.get("filename"));
        Integer btype = DataExchangeImportSupport.parseNonNegativeInteger(
                rowNumber,
                "btype",
                values.get("btype"),
                errors
        );
        Long fileSize = DataExchangeImportSupport.parseNonNegativeLong(
                rowNumber,
                "filesize",
                values.get("filesize"),
                errors
        );

        DataExchangeImportSupport.validateIdentifier(rowNumber, "sjh", sjh, errors);
        DataExchangeImportSupport.validateIdentifier(rowNumber, "bah", bah, errors);
        DataExchangeImportSupport.validateIdentifier(rowNumber, "brxh", brxh, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "folder", folder, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "filename", filename, errors);

        if (bah == null && sjh == null) {
            errors.add(rowNumber, "bah/sjh", "病案号与上架号不能同时为空", "");
        }
        if (sjh == null && DataExchangeImportSupport.isHighNumericBah(bah)) {
            errors.add(rowNumber, "sjh", "病案号达到 10000000 时必须同时提供上架号", bah);
        }
        if (folder == null) {
            errors.add(rowNumber, "folder", "原始图片目录不能为空", "");
        }
        if (filename == null) {
            errors.add(rowNumber, "filename", "图片文件名不能为空", "");
        }
        if (btype != null && btype > 15) {
            errors.add(rowNumber, "btype", "图片类型必须位于 0 到 15", String.valueOf(btype));
        }

        if (errors.size() > before) {
            return null;
        }
        return new ScanImportRow(
                0,
                rowNumber,
                sjh,
                bah,
                brxh,
                folder,
                filename,
                btype == null ? 0 : btype,
                fileSize
        );
    }

    private ImportOutcome stageAndImport(
            List<ScanImportRow> rows,
            boolean dryRun,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        if (rows.isEmpty()) {
            return new ImportOutcome(0, 0, 0);
        }

        jdbcTemplate.execute("select pg_advisory_xact_lock(hashtext('mrr.scan.import'))");
        jdbcTemplate.execute("drop table if exists pg_temp.mrr_scan_import_stage");
        jdbcTemplate.execute("""
                create temporary table mrr_scan_import_stage (
                    seq integer not null,
                    source_row integer not null,
                    sjh text,
                    bah text,
                    brxh text,
                    folder text not null,
                    filename text not null,
                    btype integer not null,
                    file_size bigint,
                    archive_id bigint
                ) on commit drop
                """);

        jdbcTemplate.batchUpdate(
                """
                        insert into pg_temp.mrr_scan_import_stage
                        (seq, source_row, sjh, bah, brxh, folder, filename, btype, file_size)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                JDBC_BATCH_SIZE,
                this::bindRow
        );
        resolveExistingArchives();

        jdbcTemplate.query("""
                select s.source_row, s.folder, s.filename
                from pg_temp.mrr_scan_import_stage s
                where (
                    select count(*)
                    from mr_scan t
                    where t.folder = s.folder
                      and t.filename = s.filename
                ) > 1
                order by s.seq
                """, (RowCallbackHandler) resultSet -> errors.add(
                resultSet.getInt("source_row"),
                "folder/filename",
                "数据库中同一目录和文件名存在多条扫描记录，请先处理历史冲突",
                resultSet.getString("folder") + "/" + resultSet.getString("filename")
        ));

        jdbcTemplate.query("""
                select source_row, bah
                from pg_temp.mrr_scan_import_stage
                where sjh is null and archive_id is null
                order by seq
                """, (RowCallbackHandler) resultSet -> errors.add(
                resultSet.getInt("source_row"),
                "bah",
                "病案号无法唯一关联到病案主档，请先导入统计数据或补充上架号",
                resultSet.getString("bah")
        ));
        if (errors.hasErrors()) {
            return new ImportOutcome(0, 0, 0);
        }

        int databaseDuplicates = queryCount("""
                select count(*)
                from pg_temp.mrr_scan_import_stage s
                where exists (
                    select 1
                    from mr_scan t
                    where t.folder = s.folder
                      and t.filename = s.filename
                      and t.sjh is not distinct from s.sjh
                      and t.bah is not distinct from s.bah
                      and t.brxh is not distinct from s.brxh
                      and t.btype is not distinct from s.btype
                      and t.file_size is not distinct from s.file_size
                      and t.archive_id is not distinct from s.archive_id
                )
                """);
        int existingPaths = queryCount("""
                select count(*)
                from pg_temp.mrr_scan_import_stage s
                where exists (
                    select 1
                    from mr_scan t
                    where t.folder = s.folder
                      and t.filename = s.filename
                )
                """);
        int updatedRows = existingPaths - databaseDuplicates;
        int insertedRows = rows.size() - existingPaths;

        if (!dryRun) {
            jdbcTemplate.update("""
                    insert into mr_archive as target (sjh, bah)
                    select s.sjh, max(s.bah)
                    from pg_temp.mrr_scan_import_stage s
                    where s.sjh is not null
                    group by s.sjh
                    on conflict (sjh) where sjh is not null do update
                    set bah = coalesce(target.bah, excluded.bah),
                        updated_at = current_timestamp
                    """);
            resolveExistingArchives();

            Integer unresolved = jdbcTemplate.queryForObject("""
                    select count(*)
                    from pg_temp.mrr_scan_import_stage
                    where archive_id is null
                    """, Integer.class);
            if (unresolved != null && unresolved > 0) {
                throw new IllegalStateException("扫描记录导入时无法建立病案主档关联");
            }

            jdbcTemplate.update("""
                    update mr_scan t
                    set sjh = s.sjh,
                        bah = s.bah,
                        brxh = s.brxh,
                        btype = s.btype,
                        file_size = s.file_size,
                        archive_id = s.archive_id
                    from pg_temp.mrr_scan_import_stage s
                    where t.folder = s.folder
                      and t.filename = s.filename
                      and (
                          t.sjh is distinct from s.sjh
                          or t.bah is distinct from s.bah
                          or t.brxh is distinct from s.brxh
                          or t.btype is distinct from s.btype
                          or t.file_size is distinct from s.file_size
                          or t.archive_id is distinct from s.archive_id
                      )
                    """);

            jdbcTemplate.update("""
                    insert into mr_scan
                    (sjh, bah, brxh, folder, filename, btype, file_size,
                     uploadflag, migration_status, archive_id)
                    select s.sjh,
                           s.bah,
                           s.brxh,
                           s.folder,
                           s.filename,
                           s.btype,
                           s.file_size,
                           1,
                           'not_migrated',
                           s.archive_id
                    from pg_temp.mrr_scan_import_stage s
                    where not exists (
                        select 1
                        from mr_scan t
                        where t.folder = s.folder
                          and t.filename = s.filename
                    )
                    order by s.seq
                    """);
        }

        return new ImportOutcome(insertedRows, updatedRows, databaseDuplicates);
    }

    private void resolveExistingArchives() {
        jdbcTemplate.update("""
                update pg_temp.mrr_scan_import_stage s
                set archive_id = case
                    when s.sjh is not null then (
                        select a.id from mr_archive a where a.sjh = s.sjh
                    )
                    else app.resolve_archive_id(s.bah, null, false)
                end
                """);
    }

    private void bindRow(PreparedStatement statement, ScanImportRow row) throws SQLException {
        statement.setInt(1, row.sequence());
        statement.setInt(2, row.sourceRow());
        statement.setString(3, row.sjh());
        statement.setString(4, row.bah());
        statement.setString(5, row.brxh());
        statement.setString(6, row.folder());
        statement.setString(7, row.filename());
        statement.setInt(8, row.btype());
        statement.setObject(9, row.fileSize());
    }

    private int queryCount(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private DataExchangeImportResult result(
            MultipartFile file,
            String encoding,
            boolean dryRun,
            boolean canImport,
            int totalRows,
            int validRows,
            int insertedRows,
            int updatedRows,
            int duplicateRows,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        return new DataExchangeImportResult(
                DATASET,
                fileReader.safeFileName(file == null ? null : file.getOriginalFilename()),
                encoding,
                dryRun,
                canImport,
                totalRows,
                validRows,
                insertedRows,
                updatedRows,
                duplicateRows,
                errors.errorRowCount(),
                errors.truncated(),
                List.copyOf(errors.errors())
        );
    }

    private record ImportOutcome(int insertedRows, int updatedRows, int databaseDuplicateRows) {
    }

    private record ScanImportRow(
            int sequence,
            int sourceRow,
            String sjh,
            String bah,
            String brxh,
            String folder,
            String filename,
            int btype,
            Long fileSize
    ) {
        ScanImportRow withSequence(int sequence) {
            return new ScanImportRow(
                    sequence,
                    sourceRow,
                    sjh,
                    bah,
                    brxh,
                    folder,
                    filename,
                    btype,
                    fileSize
            );
        }

        String pathKey() {
            return DataExchangeImportSupport.value(folder)
                    + "\u0001"
                    + DataExchangeImportSupport.value(filename);
        }

        String fingerprint() {
            return String.join("\u0001", Arrays.asList(
                    DataExchangeImportSupport.value(sjh),
                    DataExchangeImportSupport.value(bah),
                    DataExchangeImportSupport.value(brxh),
                    DataExchangeImportSupport.value(folder),
                    DataExchangeImportSupport.value(filename),
                    DataExchangeImportSupport.value(btype),
                    DataExchangeImportSupport.value(fileSize)
            ));
        }
    }
}
