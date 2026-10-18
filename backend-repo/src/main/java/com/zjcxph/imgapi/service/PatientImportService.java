package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.resp.PatientImportError;
import com.zjcxph.imgapi.dto.resp.PatientImportResult;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PatientImportService {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_REPORTED_ERRORS = 200;
    private static final int MAX_CELL_LENGTH = 2_000;
    private static final int JDBC_BATCH_SIZE = 1_000;

    private static final List<String> REQUIRED_HEADERS = List.of(
            "bah", "name", "idcard", "ruyuan", "admissiontime", "department", "bingqu", "chuangwei"
    );
    private static final Set<String> ALLOWED_IGNORED_HEADERS = Set.of(
            "brxh", "id", "keshicode", "bingqucode"
    );
    private static final DateTimeFormatter NORMALIZED_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ISO_LOCAL_DATE
    );
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("uuuu-M-d H:m").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-M-d H:m:s").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
    );

    private final JdbcTemplate jdbcTemplate;
    private final TabularImportFileReader fileReader;

    public PatientImportService(JdbcTemplate jdbcTemplate, TabularImportFileReader fileReader) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileReader = fileReader;
    }

    @Transactional(rollbackFor = Exception.class)
    public PatientImportResult importPatients(MultipartFile file, boolean dryRun) throws IOException {
        TabularImportFileReader.ParsedTable parsedFile = fileReader.read(file, MAX_FILE_SIZE, MAX_ROWS);
        ErrorCollector errors = new ErrorCollector();
        List<String> headers = validateAndNormalizeHeaders(parsedFile.headers(), errors);

        if (errors.hasErrors()) {
            return result(file, parsedFile.encoding(), dryRun, false, 0, 0, 0, 0, errors);
        }

        LinkedHashMap<String, PatientRow> uniqueRows = new LinkedHashMap<>();
        int totalRows = 0;
        int fileDuplicateRows = 0;

        for (TabularImportFileReader.SourceRow sourceRow : parsedFile.rows()) {
            if (isBlankRow(sourceRow.values())) {
                continue;
            }
            totalRows++;

            Map<String, String> values = toValueMap(headers, sourceRow.values());
            PatientRow patientRow = normalizeRow(sourceRow.rowNumber(), values, errors);
            if (patientRow == null) {
                continue;
            }

            String fingerprint = patientRow.fingerprint();
            if (uniqueRows.containsKey(fingerprint)) {
                fileDuplicateRows++;
            } else {
                uniqueRows.put(fingerprint, patientRow);
            }
        }

        if (errors.hasErrors()) {
            return result(
                    file,
                    parsedFile.encoding(),
                    dryRun,
                    false,
                    totalRows,
                    uniqueRows.size(),
                    0,
                    fileDuplicateRows,
                    errors
            );
        }

        List<PatientRow> rows = new ArrayList<>(uniqueRows.values());
        DatabaseImportOutcome databaseOutcome = stageAndImport(rows, dryRun);
        return result(
                file,
                parsedFile.encoding(),
                dryRun,
                true,
                totalRows,
                rows.size(),
                databaseOutcome.insertedRows(),
                fileDuplicateRows + databaseOutcome.databaseDuplicateRows(),
                errors
        );
    }

    private List<String> validateAndNormalizeHeaders(List<String> rawHeaders, ErrorCollector errors) {
        List<String> headers = new ArrayList<>(rawHeaders.size());
        Set<String> seen = new LinkedHashSet<>();
        Set<String> allowed = new LinkedHashSet<>(REQUIRED_HEADERS);
        allowed.addAll(ALLOWED_IGNORED_HEADERS);

        for (String rawHeader : rawHeaders) {
            String header = normalizeHeader(rawHeader);
            headers.add(header);
            if (header.isEmpty()) {
                errors.add(1, "header", "表头中存在空字段名", "");
            } else if (!seen.add(header)) {
                errors.add(1, header, "字段名称重复", header);
            } else if (!allowed.contains(header)) {
                errors.add(1, header, "未知字段；请使用患者导入模板中的字段名称", header);
            }
        }

        for (String requiredHeader : REQUIRED_HEADERS) {
            if (!seen.contains(requiredHeader)) {
                errors.add(1, requiredHeader, "缺少必需字段", requiredHeader);
            }
        }
        return headers;
    }

    private PatientRow normalizeRow(int rowNumber, Map<String, String> values, ErrorCollector errors) {
        int before = errors.size();
        String bah = normalizeText(values.get("bah"));
        String name = normalizeText(values.get("name"));
        String idCard = normalizeText(values.get("idcard"));
        String department = normalizeText(values.get("department"));
        String bingqu = normalizeText(values.get("bingqu"));
        String chuangwei = normalizeText(values.get("chuangwei"));

        if (bah == null) {
            errors.add(rowNumber, "bah", "病案号不能为空", "");
        } else if (looksLikeScientificNotation(bah)) {
            errors.add(rowNumber, "bah", "病案号疑似被表格软件转换为科学计数法", bah);
        }
        if (idCard != null && looksLikeScientificNotation(idCard)) {
            errors.add(rowNumber, "idcard", "身份证号疑似被表格软件转换为科学计数法", idCard);
        }

        validateLength(rowNumber, "bah", bah, errors);
        validateLength(rowNumber, "name", name, errors);
        validateLength(rowNumber, "idcard", idCard, errors);
        validateLength(rowNumber, "department", department, errors);
        validateLength(rowNumber, "bingqu", bingqu, errors);
        validateLength(rowNumber, "chuangwei", chuangwei, errors);

        LocalDate ruyuan = parseDate(rowNumber, values.get("ruyuan"), errors);
        String admissionTime = parseDateTime(rowNumber, values.get("admissiontime"), errors);

        if (errors.size() > before) {
            return null;
        }
        return new PatientRow(rowNumber, bah, name, idCard, ruyuan, admissionTime, department, bingqu, chuangwei);
    }

    private LocalDate parseDate(int rowNumber, String rawValue, ErrorCollector errors) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('/', '-').replace('.', '-');
        int separator = firstDateTimeSeparator(normalized);
        if (separator > 0) {
            normalized = normalized.substring(0, separator);
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式。
            }
        }
        errors.add(rowNumber, "ruyuan", "入院日期必须是 YYYY-MM-DD", value);
        return null;
    }

    private String parseDateTime(int rowNumber, String rawValue, ErrorCollector errors) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('/', '-').replace('.', '-').replace('T', ' ');
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter).format(NORMALIZED_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式。
            }
        }
        errors.add(rowNumber, "admissiontime", "入院时间必须是 YYYY-MM-DD HH:MM 或 YYYY-MM-DD HH:MM:SS", value);
        return null;
    }

    private int firstDateTimeSeparator(String value) {
        int space = value.indexOf(' ');
        int t = value.indexOf('T');
        if (space < 0) {
            return t;
        }
        if (t < 0) {
            return space;
        }
        return Math.min(space, t);
    }

    private void validateLength(int rowNumber, String field, String value, ErrorCollector errors) {
        if (value != null && value.length() > MAX_CELL_LENGTH) {
            errors.add(rowNumber, field, "字段内容超过 " + MAX_CELL_LENGTH + " 个字符", value);
        }
    }

    private DatabaseImportOutcome stageAndImport(List<PatientRow> rows, boolean dryRun) {
        if (rows.isEmpty()) {
            return new DatabaseImportOutcome(0, 0);
        }

        jdbcTemplate.execute("select pg_advisory_xact_lock(hashtext('mrr.patient.import'))");
        jdbcTemplate.execute("drop table if exists pg_temp.mrr_patient_import_stage");
        jdbcTemplate.execute("""
                create temporary table mrr_patient_import_stage (
                    seq integer not null,
                    bah text,
                    name text,
                    idcard text,
                    ruyuan date,
                    admissiontime text,
                    department text,
                    bingqu text,
                    chuangwei text
                ) on commit drop
                """);

        jdbcTemplate.batchUpdate(
                """
                        insert into pg_temp.mrr_patient_import_stage
                        (seq, bah, name, idcard, ruyuan, admissiontime, department, bingqu, chuangwei)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                rows,
                JDBC_BATCH_SIZE,
                this::bindPatientRow
        );

        Integer databaseDuplicates = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_temp.mrr_patient_import_stage s
                where exists (
                    select 1
                    from mr_patient p
                    where p.bah is not distinct from s.bah
                      and p.name is not distinct from s.name
                      and p.idcard is not distinct from s.idcard
                      and p.ruyuan is not distinct from s.ruyuan
                      and p.admissiontime is not distinct from s.admissiontime
                      and p.department is not distinct from s.department
                      and p.bingqu is not distinct from s.bingqu
                      and p.chuangwei is not distinct from s.chuangwei
                )
                """, Integer.class);

        int insertedRows = 0;
        if (!dryRun) {
            insertedRows = jdbcTemplate.update("""
                    insert into mr_patient
                    (bah, name, idcard, ruyuan, admissiontime, department, bingqu, chuangwei)
                    select s.bah, s.name, s.idcard, s.ruyuan, s.admissiontime,
                           s.department, s.bingqu, s.chuangwei
                    from pg_temp.mrr_patient_import_stage s
                    where not exists (
                        select 1
                        from mr_patient p
                        where p.bah is not distinct from s.bah
                          and p.name is not distinct from s.name
                          and p.idcard is not distinct from s.idcard
                          and p.ruyuan is not distinct from s.ruyuan
                          and p.admissiontime is not distinct from s.admissiontime
                          and p.department is not distinct from s.department
                          and p.bingqu is not distinct from s.bingqu
                          and p.chuangwei is not distinct from s.chuangwei
                    )
                    order by s.seq
                    """);
        }
        return new DatabaseImportOutcome(databaseDuplicates == null ? 0 : databaseDuplicates, insertedRows);
    }

    private void bindPatientRow(PreparedStatement statement, PatientRow row) throws SQLException {
        statement.setInt(1, row.sequence());
        statement.setString(2, row.bah());
        statement.setString(3, row.name());
        statement.setString(4, row.idCard());
        statement.setObject(5, row.ruyuan());
        statement.setString(6, row.admissionTime());
        statement.setString(7, row.department());
        statement.setString(8, row.bingqu());
        statement.setString(9, row.chuangwei());
    }

    private PatientImportResult result(
            MultipartFile file,
            String encoding,
            boolean dryRun,
            boolean canImport,
            int totalRows,
            int validRows,
            int insertedRows,
            int duplicateRows,
            ErrorCollector errors
    ) {
        return new PatientImportResult(
                safeFileName(file.getOriginalFilename()),
                encoding,
                dryRun,
                canImport,
                totalRows,
                validRows,
                insertedRows,
                duplicateRows,
                errors.errorRowCount(),
                errors.truncated(),
                List.copyOf(errors.errors())
        );
    }

    private Map<String, String> toValueMap(List<String> headers, List<String> row) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int column = 0; column < headers.size(); column++) {
            String header = headers.get(column);
            if (!header.isEmpty() && !ALLOWED_IGNORED_HEADERS.contains(header)) {
                values.put(header, column < row.size() ? row.get(column) : "");
            }
        }
        return values;
    }

    private boolean isBlankRow(List<String> values) {
        return values.stream().allMatch(value -> value == null || value.trim().isEmpty());
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean looksLikeScientificNotation(String value) {
        return value.matches("[+-]?\\d+(?:\\.\\d+)?[eE][+-]?\\d+");
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "patients";
        }
        return fileName.replace('\\', '/').substring(fileName.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private static final class ErrorCollector {
        private final List<PatientImportError> errors = new ArrayList<>();
        private final Set<Integer> errorRows = new LinkedHashSet<>();
        private int errorCount;
        private boolean truncated;

        void add(int rowNumber, String field, String message, String rawValue) {
            errorCount++;
            errorRows.add(rowNumber);
            if (errors.size() >= MAX_REPORTED_ERRORS) {
                truncated = true;
                return;
            }
            errors.add(new PatientImportError(rowNumber, field, message, maskValue(field, rawValue)));
        }

        int size() {
            return errorCount;
        }

        boolean hasErrors() {
            return !errorRows.isEmpty();
        }

        int errorRowCount() {
            return errorRows.size();
        }

        boolean truncated() {
            return truncated;
        }

        List<PatientImportError> errors() {
            return errors;
        }

        private static String maskValue(String field, String rawValue) {
            if (rawValue == null) {
                return "";
            }
            String value = rawValue.strip();
            if ("idcard".equals(field) && value.length() > 7) {
                value = value.substring(0, 3) + "*".repeat(value.length() - 7) + value.substring(value.length() - 4);
            }
            return value.length() <= 80 ? value : value.substring(0, 80) + "…";
        }
    }

    private record DatabaseImportOutcome(int databaseDuplicateRows, int insertedRows) {
    }

    private record PatientRow(
            int sequence,
            String bah,
            String name,
            String idCard,
            LocalDate ruyuan,
            String admissionTime,
            String department,
            String bingqu,
            String chuangwei
    ) {
        String fingerprint() {
            return String.join("\u0001", Arrays.asList(
                    value(bah), value(name), value(idCard), value(ruyuan), value(admissionTime),
                    value(department), value(bingqu), value(chuangwei)
            ));
        }

        private static String value(Object value) {
            return value == null ? "\u0000" : value.toString();
        }
    }
}
