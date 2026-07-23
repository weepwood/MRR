package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.service.importer.DataExchangeImportSupport;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StatisticsDataExchangeService {

    public static final String DATASET = "MR_STATISTICS";
    public static final List<String> TEMPLATE_HEADERS = List.of(
            "bah",
            "cid",
            "openerno",
            "date",
            "type",
            "pages",
            "sjh",
            "patientname",
            "inpatientdepartment",
            "patientid",
            "dischargedate"
    );

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;
    private static final int MAX_ROWS = 100_000;
    private static final int JDBC_BATCH_SIZE = 1_000;
    private static final Set<String> REQUIRED_HEADERS = new LinkedHashSet<>(TEMPLATE_HEADERS);
    private static final Set<String> IGNORED_HEADERS = Set.of("brxh");

    private final JdbcTemplate jdbcTemplate;
    private final TabularImportFileReader fileReader;

    public StatisticsDataExchangeService(
            JdbcTemplate jdbcTemplate,
            TabularImportFileReader fileReader
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileReader = fileReader;
    }

    @Transactional(rollbackFor = Exception.class)
    public DataExchangeImportResult importStatistics(MultipartFile file, boolean dryRun) throws IOException {
        TabularImportFileReader.ParsedTable parsed = fileReader.read(file, MAX_FILE_SIZE, MAX_ROWS);
        DataExchangeImportSupport.ErrorCollector errors = new DataExchangeImportSupport.ErrorCollector();
        List<String> headers = DataExchangeImportSupport.validateHeaders(
                parsed.headers(),
                REQUIRED_HEADERS,
                IGNORED_HEADERS,
                "统计数据模板",
                errors
        );
        if (errors.hasErrors()) {
            return result(file, parsed.encoding(), dryRun, false, 0, 0, 0, 0, 0, errors);
        }

        LinkedHashMap<String, StatisticsImportRow> uniqueRows = new LinkedHashMap<>();
        Map<String, String> sjhFingerprints = new LinkedHashMap<>();
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
                    IGNORED_HEADERS
            );
            StatisticsImportRow row = normalizeRow(sourceRow.rowNumber(), values, errors);
            if (row == null) {
                continue;
            }

            String fingerprint = row.fingerprint();
            if (row.sjh() != null) {
                String existingFingerprint = sjhFingerprints.putIfAbsent(row.sjh(), fingerprint);
                if (existingFingerprint != null && !existingFingerprint.equals(fingerprint)) {
                    errors.add(
                            sourceRow.rowNumber(),
                            "sjh",
                            "同一上架号在文件中对应多组不同数据",
                            row.sjh()
                    );
                    continue;
                }
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

        ImportOutcome outcome = stageAndImport(new ArrayList<>(uniqueRows.values()), dryRun);
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

    private StatisticsImportRow normalizeRow(
            int rowNumber,
            Map<String, String> values,
            DataExchangeImportSupport.ErrorCollector errors
    ) {
        int before = errors.size();
        String bah = DataExchangeImportSupport.normalizeText(values.get("bah"));
        String cid = DataExchangeImportSupport.normalizeText(values.get("cid"));
        String openerNo = DataExchangeImportSupport.normalizeText(values.get("openerno"));
        String type = DataExchangeImportSupport.normalizeText(values.get("type"));
        String sjh = DataExchangeImportSupport.normalizeText(values.get("sjh"));
        String patientName = DataExchangeImportSupport.normalizeText(values.get("patientname"));
        String inpatientDepartment = DataExchangeImportSupport.normalizeText(values.get("inpatientdepartment"));
        String patientId = DataExchangeImportSupport.normalizeText(values.get("patientid"));

        DataExchangeImportSupport.validateIdentifier(rowNumber, "bah", bah, errors);
        DataExchangeImportSupport.validateIdentifier(rowNumber, "sjh", sjh, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "cid", cid, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "openerno", openerNo, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "type", type, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "patientname", patientName, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "inpatientdepartment", inpatientDepartment, errors);
        DataExchangeImportSupport.validateLength(rowNumber, "patientid", patientId, errors);

        LocalDate archiveDate = DataExchangeImportSupport.parseDate(
                rowNumber,
                "date",
                values.get("date"),
                errors
        );
        LocalDate dischargeDate = DataExchangeImportSupport.parseDate(
                rowNumber,
                "dischargedate",
                values.get("dischargedate"),
                errors
        );
        Integer pages = DataExchangeImportSupport.parseNonNegativeInteger(
                rowNumber,
                "pages",
                values.get("pages"),
                errors
        );

        if (bah == null && sjh == null) {
            errors.add(rowNumber, "bah/sjh", "病案号与上架号不能同时为空", "");
        }
        if (sjh == null && DataExchangeImportSupport.isHighNumericBah(bah)) {
            errors.add(rowNumber, "sjh", "病案号达到 10000000 时必须同时提供上架号", bah);
        }

        if (errors.size() > before) {
            return null;
        }
        return new StatisticsImportRow(
                0,
                bah,
                cid,
                openerNo,
                archiveDate,
                type == null ? "未扫描" : type,
                pages,
                sjh,
                patientName,
                inpatientDepartment,
                patientId,
                dischargeDate
        );
    }

    private ImportOutcome stageAndImport(List<StatisticsImportRow> rows, boolean dryRun) {
        if (rows.isEmpty()) {
            return new ImportOutcome(0, 0, 0);
        }

        jdbcTemplate.execute("select pg_advisory_xact_lock(hashtext('mrr.statistics.import'))");
        jdbcTemplate.execute("drop table if exists pg_temp.mrr_statistics_import_stage");
        jdbcTemplate.execute("""
                create temporary table mrr_statistics_import_stage (
                    seq integer not null,
                    bah text,
                    cid text,
                    openerno text,
                    archive_date date,
                    archive_type text,
                    pages integer,
                    sjh text,
                    patientname text,
                    inpatientdepartment text,
                    patientid text,
                    dischargedate date
                ) on commit drop
                """);

        jdbcTemplate.batchUpdate(
                """
                        insert into pg_temp.mrr_statistics_import_stage
                        (seq, bah, cid, openerno, archive_date, archive_type, pages, sjh,
                         patientname, inpatientdepartment, patientid, dischargedate)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                JDBC_BATCH_SIZE,
                this::bindRow
        );
        jdbcTemplate.update("""
                update pg_temp.mrr_statistics_import_stage
                set bah = app.normalize_medical_record_code(bah),
                    sjh = app.normalize_medical_record_code(sjh)
                """);

        int updatedRows = queryCount("""
                select count(*)
                from pg_temp.mrr_statistics_import_stage s
                where s.sjh is not null
                  and exists (
                      select 1 from mr_statistics t where t.sjh = s.sjh
                  )
                """);
        int databaseDuplicates = queryCount("""
                select count(*)
                from pg_temp.mrr_statistics_import_stage s
                where s.sjh is null
                  and exists (
                      select 1
                      from mr_statistics t
                      where t.sjh is null
                        and t.bah is not distinct from s.bah
                        and t.cid is not distinct from s.cid
                        and t.openerno is not distinct from s.openerno
                        and app.try_parse_date(t.date) is not distinct from s.archive_date
                        and coalesce(nullif(btrim(t.type), ''), '未扫描') is not distinct from s.archive_type
                        and t.pages is not distinct from s.pages
                        and t.patientname is not distinct from s.patientname
                        and t.inpatientdepartment is not distinct from s.inpatientdepartment
                        and t.patientid is not distinct from s.patientid
                        and app.try_parse_date(t.dischargedate) is not distinct from s.dischargedate
                  )
                """);
        int insertedRows = rows.size() - updatedRows - databaseDuplicates;

        if (!dryRun) {
            jdbcTemplate.update("""
                    update mr_statistics t
                    set bah = s.bah,
                        cid = s.cid,
                        openerno = s.openerno,
                        date = case when s.archive_date is null then null else to_char(s.archive_date, 'YYYY-MM-DD') end,
                        type = s.archive_type,
                        pages = s.pages,
                        patientname = s.patientname,
                        inpatientdepartment = s.inpatientdepartment,
                        patientid = s.patientid,
                        dischargedate = case when s.dischargedate is null then null else to_char(s.dischargedate, 'YYYY-MM-DD') end
                    from pg_temp.mrr_statistics_import_stage s
                    where s.sjh is not null
                      and t.sjh = s.sjh
                    """);

            jdbcTemplate.update("""
                    insert into mr_statistics
                    (bah, cid, openerno, date, type, pages, sjh,
                     patientname, inpatientdepartment, patientid, dischargedate)
                    select s.bah,
                           s.cid,
                           s.openerno,
                           case when s.archive_date is null then null else to_char(s.archive_date, 'YYYY-MM-DD') end,
                           s.archive_type,
                           s.pages,
                           s.sjh,
                           s.patientname,
                           s.inpatientdepartment,
                           s.patientid,
                           case when s.dischargedate is null then null else to_char(s.dischargedate, 'YYYY-MM-DD') end
                    from pg_temp.mrr_statistics_import_stage s
                    where (
                        s.sjh is not null
                        and not exists (
                            select 1 from mr_statistics t where t.sjh = s.sjh
                        )
                    ) or (
                        s.sjh is null
                        and not exists (
                            select 1
                            from mr_statistics t
                            where t.sjh is null
                              and t.bah is not distinct from s.bah
                              and t.cid is not distinct from s.cid
                              and t.openerno is not distinct from s.openerno
                              and app.try_parse_date(t.date) is not distinct from s.archive_date
                              and coalesce(nullif(btrim(t.type), ''), '未扫描') is not distinct from s.archive_type
                              and t.pages is not distinct from s.pages
                              and t.patientname is not distinct from s.patientname
                              and t.inpatientdepartment is not distinct from s.inpatientdepartment
                              and t.patientid is not distinct from s.patientid
                              and app.try_parse_date(t.dischargedate) is not distinct from s.dischargedate
                        )
                    )
                    order by s.seq
                    """);
        }

        return new ImportOutcome(insertedRows, updatedRows, databaseDuplicates);
    }

    private void bindRow(PreparedStatement statement, StatisticsImportRow row) throws SQLException {
        statement.setInt(1, row.sequence());
        statement.setString(2, row.bah());
        statement.setString(3, row.cid());
        statement.setString(4, row.openerNo());
        statement.setObject(5, row.archiveDate());
        statement.setString(6, row.type());
        statement.setObject(7, row.pages());
        statement.setString(8, row.sjh());
        statement.setString(9, row.patientName());
        statement.setString(10, row.inpatientDepartment());
        statement.setString(11, row.patientId());
        statement.setObject(12, row.dischargeDate());
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

    private record StatisticsImportRow(
            int sequence,
            String bah,
            String cid,
            String openerNo,
            LocalDate archiveDate,
            String type,
            Integer pages,
            String sjh,
            String patientName,
            String inpatientDepartment,
            String patientId,
            LocalDate dischargeDate
    ) {
        StatisticsImportRow withSequence(int sequence) {
            return new StatisticsImportRow(
                    sequence,
                    bah,
                    cid,
                    openerNo,
                    archiveDate,
                    type,
                    pages,
                    sjh,
                    patientName,
                    inpatientDepartment,
                    patientId,
                    dischargeDate
            );
        }

        String fingerprint() {
            return String.join("\u0001", Arrays.asList(
                    DataExchangeImportSupport.value(bah),
                    DataExchangeImportSupport.value(cid),
                    DataExchangeImportSupport.value(openerNo),
                    DataExchangeImportSupport.value(archiveDate),
                    DataExchangeImportSupport.value(type),
                    DataExchangeImportSupport.value(pages),
                    DataExchangeImportSupport.value(sjh),
                    DataExchangeImportSupport.value(patientName),
                    DataExchangeImportSupport.value(inpatientDepartment),
                    DataExchangeImportSupport.value(patientId),
                    DataExchangeImportSupport.value(dischargeDate)
            ));
        }
    }
}
