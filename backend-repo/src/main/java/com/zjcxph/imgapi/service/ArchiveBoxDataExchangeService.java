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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ArchiveBoxDataExchangeService {

    public static final String DATASET = "MR_ARCHIVE_BOX_RECORD";
    public static final List<String> TEMPLATE_HEADERS = List.of(
            "bah",
            "sjh",
            "box_no",
            "expected_box_no",
            "status",
            "remark"
    );

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int MAX_ROWS = 100_000;
    private static final int JDBC_BATCH_SIZE = 1_000;
    private static final String STATUS_MISSING = "MISSING";
    private static final Set<String> REQUIRED_HEADERS = new LinkedHashSet<>(TEMPLATE_HEADERS);
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            "NORMAL",
            STATUS_MISSING,
            "MISPLACED",
            "CONFLICT",
            "OTHER"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TabularImportFileReader fileReader;

    public ArchiveBoxDataExchangeService(
            JdbcTemplate jdbcTemplate,
            TabularImportFileReader fileReader
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileReader = fileReader;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataExchangeImportResult importArchiveBoxes(MultipartFile file, boolean dryRun) throws IOException {
        TabularImportFileReader.ParsedTable parsed = fileReader.read(file, MAX_FILE_SIZE, MAX_ROWS);
        DataExchangeImportSupport.ErrorCollector errors = new DataExchangeImportSupport.ErrorCollector();
        List<String> headers = DataExchangeImportSupport.validateHeaders(
                parsed.headers(),
                REQUIRED_HEADERS,
                Set.of(),
                "档案装箱模板",
                errors
        );
        if (errors.hasErrors()) {
            return result(file, parsed.encoding(), dryRun, false, 0, 0, 0, 0, 0, errors);
        }

        LinkedHashMap<String, ArchiveBoxImportRow> uniqueRows = new LinkedHashMap<>();
        Map<String, String> recordFingerprints = new LinkedHashMap<>();
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
            ArchiveBoxImportRow row = normalizeRow(sourceRow.rowNumber(), values, errors);
            if (row == null) {
                continue;
            }

            String fingerprint = row.fingerprint();
            String recordKey = row.recordKey();
            String existingFingerprint = recordFingerprints.putIfAbsent(recordKey, fingerprint);
            if (existingFingerprint != null && !existingFingerprint.equals(fingerprint)) {
                errors.add(
                        sourceRow.rowNumber(),
                        row.sjh() == null ? "bah" : "sjh",
                        "同一病案在文件中存在多组不同装箱数据",
                        row.sjh() == null ? row.bah() : row.sjh()
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
                fileDuplicateRows,
                errors
        );
    }

    private ArchiveBoxImportRow normalizeRow(
            int rowNumber,
            Map<String, String> values,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        int before = errors.size();
        String rawBah = DataExchangeImportSupport.normalizeText(values.get("bah"));
        String rawSjh = DataExchangeImportSupport.normalizeText(values.get("sjh"));
        String bah = DataExchangeImportSupport.normalizeMedicalRecordCode(rawBah);
        String sjh = DataExchangeImportSupport.normalizeMedicalRecordCode(rawSjh);
        String boxNo = DataExchangeImportSupport.normalizeText(values.get("box_no"));
        String expectedBoxNo = DataExchangeImportSupport.normalizeText(values.get("expected_box_no"));
        String status = normalizeStatus(values.get("status"));
        String remark = DataExchangeImportSupport.normalizeText(values.get("remark"));

        DataExchangeImportSupport.validateIdentifier(rowNumber, "bah", rawBah, errors);
        DataExchangeImportSupport.validateIdentifier(rowNumber, "sjh", rawSjh, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "box_no", boxNo, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "expected_box_no", expectedBoxNo, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "remark", remark, errors);

        if (bah == null && sjh == null) {
            errors.add(rowNumber, "bah/sjh", "病案号与上架号不能同时为空", "");
        }
        if (sjh == null && DataExchangeImportSupport.isHighNumericBah(rawBah)) {
            errors.add(rowNumber, "sjh", "病案号达到 10000000 时必须同时提供上架号", rawBah);
        }
        if (!SUPPORTED_STATUSES.contains(status)) {
            errors.add(rowNumber, "status", "不支持的装箱状态", status);
        }
        if (!STATUS_MISSING.equals(status) && boxNo == null) {
            errors.add(rowNumber, "box_no", "除 MISSING 外，实际箱号不能为空", "");
        }

        if (errors.size() > before) {
            return null;
        }
        return new ArchiveBoxImportRow(
                0,
                bah,
                sjh,
                boxNo,
                expectedBoxNo,
                status,
                remark,
                rowNumber
        );
    }

    private String normalizeStatus(String value) {
        String normalized = DataExchangeImportSupport.normalizeText(value);
        if (normalized == null) {
            return "NORMAL";
        }
        return switch (normalized) {
            case "正常" -> "NORMAL";
            case "缺失" -> STATUS_MISSING;
            case "存放在其他箱子" -> "MISPLACED";
            case "正常(箱号冲突)", "正常（箱号冲突）" -> "CONFLICT";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private ImportOutcome stageAndImport(
            List<ArchiveBoxImportRow> rows,
            boolean dryRun,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        if (rows.isEmpty()) {
            return new ImportOutcome(0, 0);
        }

        jdbcTemplate.execute("select pg_advisory_xact_lock(hashtext('mrr.archive.box.import'))");
        jdbcTemplate.execute("drop table if exists pg_temp.mrr_archive_box_import_stage");
        jdbcTemplate.execute("""
                create temporary table mrr_archive_box_import_stage (
                    seq integer not null,
                    source_row integer not null,
                    bah text,
                    sjh text,
                    box_no text,
                    expected_box_no text,
                    status text,
                    remark text,
                    archive_id bigint
                ) on commit drop
                """);

        jdbcTemplate.batchUpdate(
                """
                        insert into pg_temp.mrr_archive_box_import_stage
                        (seq, source_row, bah, sjh, box_no, expected_box_no, status, remark)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                JDBC_BATCH_SIZE,
                this::bindRow
        );
        resolveExistingArchives();

        jdbcTemplate.query("""
                select source_row, bah
                from pg_temp.mrr_archive_box_import_stage
                where sjh is null and archive_id is null
                order by seq
                """, (RowCallbackHandler) resultSet -> errors.add(
                resultSet.getInt("source_row"),
                "bah",
                "病案号无法唯一关联到病案主档，请先导入统计数据或补充上架号",
                resultSet.getString("bah")
        ));
        jdbcTemplate.query("""
                select s.source_row, s.sjh
                from pg_temp.mrr_archive_box_import_stage s
                where s.sjh is not null
                  and (
                      select count(*)
                      from mr_archive_box_record t
                      where t.sjh = s.sjh
                  ) > 1
                order by s.seq
                """, (RowCallbackHandler) resultSet -> errors.add(
                resultSet.getInt("source_row"),
                "sjh",
                "数据库中同一上架号存在多条装箱记录，请先处理历史冲突",
                resultSet.getString("sjh")
        ));
        if (errors.hasErrors()) {
            return new ImportOutcome(0, 0);
        }

        int updatedRows = queryCount("""
                select count(*)
                from pg_temp.mrr_archive_box_import_stage s
                where exists (
                    select 1
                    from mr_archive_box_record t
                    where (s.sjh is not null and t.sjh = s.sjh)
                       or (s.archive_id is not null and t.archive_id = s.archive_id)
                )
                """);
        int insertedRows = rows.size() - updatedRows;

        if (!dryRun) {
            jdbcTemplate.update("""
                    insert into mr_archive as target (sjh, bah)
                    select distinct s.sjh, s.bah
                    from pg_temp.mrr_archive_box_import_stage s
                    where s.sjh is not null
                    on conflict (sjh) where sjh is not null do update
                    set bah = coalesce(target.bah, excluded.bah),
                        updated_at = current_timestamp
                    """);
            resolveExistingArchives();

            jdbcTemplate.update("""
                    update mr_archive_box_record t
                    set archive_id = s.archive_id,
                        bah = s.bah,
                        sjh = s.sjh,
                        box_no = s.box_no,
                        expected_box_no = s.expected_box_no,
                        status = s.status,
                        remark = s.remark,
                        updated_at = current_timestamp
                    from pg_temp.mrr_archive_box_import_stage s
                    where s.sjh is not null
                      and t.sjh = s.sjh
                    """);
            jdbcTemplate.update("""
                    update mr_archive_box_record t
                    set bah = s.bah,
                        sjh = s.sjh,
                        box_no = s.box_no,
                        expected_box_no = s.expected_box_no,
                        status = s.status,
                        remark = s.remark,
                        updated_at = current_timestamp
                    from pg_temp.mrr_archive_box_import_stage s
                    where s.archive_id is not null
                      and t.archive_id = s.archive_id
                    """);
            jdbcTemplate.update("""
                    insert into mr_archive_box_record
                    (archive_id, bah, sjh, box_no, expected_box_no, status, remark)
                    select s.archive_id,
                           s.bah,
                           s.sjh,
                           s.box_no,
                           s.expected_box_no,
                           s.status,
                           s.remark
                    from pg_temp.mrr_archive_box_import_stage s
                    where s.archive_id is not null
                      and not exists (
                          select 1
                          from mr_archive_box_record t
                          where t.archive_id = s.archive_id
                             or (s.sjh is not null and t.sjh = s.sjh)
                      )
                    order by s.seq
                    """);
        }

        return new ImportOutcome(insertedRows, updatedRows);
    }

    private void resolveExistingArchives() {
        jdbcTemplate.update("""
                update pg_temp.mrr_archive_box_import_stage s
                set archive_id = case
                    when s.sjh is not null then (
                        select a.id from mr_archive a where a.sjh = s.sjh
                    )
                    else app.resolve_archive_id(s.bah, null, false)
                end
                """);
    }

    private void bindRow(PreparedStatement statement, ArchiveBoxImportRow row) throws SQLException {
        statement.setInt(1, row.sequence());
        statement.setInt(2, row.sourceRow());
        statement.setString(3, row.bah());
        statement.setString(4, row.sjh());
        statement.setString(5, row.boxNo());
        statement.setString(6, row.expectedBoxNo());
        statement.setString(7, row.status());
        statement.setString(8, row.remark());
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

    private record ImportOutcome(int insertedRows, int updatedRows) {
    }

    private record ArchiveBoxImportRow(
            int sequence,
            String bah,
            String sjh,
            String boxNo,
            String expectedBoxNo,
            String status,
            String remark,
            int sourceRow
    ) {
        ArchiveBoxImportRow withSequence(int sequence) {
            return new ArchiveBoxImportRow(
                    sequence,
                    bah,
                    sjh,
                    boxNo,
                    expectedBoxNo,
                    status,
                    remark,
                    sourceRow
            );
        }

        String recordKey() {
            return sjh == null ? "BAH:" + DataExchangeImportSupport.value(bah) : "SJH:" + sjh;
        }

        String fingerprint() {
            return String.join("\u0001", Arrays.asList(
                    DataExchangeImportSupport.value(bah),
                    DataExchangeImportSupport.value(sjh),
                    DataExchangeImportSupport.value(boxNo),
                    DataExchangeImportSupport.value(expectedBoxNo),
                    DataExchangeImportSupport.value(status),
                    DataExchangeImportSupport.value(remark)
            ));
        }
    }
}
