package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.service.importer.DataExchangeImportSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class DataExchangeExportService {

    private static final int EXPORT_LIMIT = 100_000;

    private final JdbcTemplate jdbcTemplate;

    public DataExchangeExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void exportPatients(String keyword, Writer writer) throws IOException {
        StringBuilder sql = new StringBuilder("""
                select bah, name, idcard, ruyuan, admissiontime, department, bingqu, chuangwei
                from mr_patient
                where 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            sql.append("""
                     and (
                         coalesce(bah, '') ilike ?
                         or coalesce(name, '') ilike ?
                         or coalesce(idcard, '') ilike ?
                         or coalesce(department, '') ilike ?
                         or coalesce(bingqu, '') ilike ?
                         or coalesce(chuangwei, '') ilike ?
                     )
                    """);
            String pattern = containsPattern(normalizedKeyword);
            for (int index = 0; index < 6; index++) {
                arguments.add(pattern);
            }
        }
        sql.append(" order by id limit ").append(EXPORT_LIMIT);

        writeHeader(writer, List.of(
                "bah", "name", "idcard", "ruyuan", "admissiontime", "department", "bingqu", "chuangwei"
        ));
        streamQuery(sql.toString(), arguments, writer, resultSet -> Arrays.asList(
                resultSet.getString("bah"),
                resultSet.getString("name"),
                resultSet.getString("idcard"),
                resultSet.getString("ruyuan"),
                resultSet.getString("admissiontime"),
                resultSet.getString("department"),
                resultSet.getString("bingqu"),
                resultSet.getString("chuangwei")
        ));
    }

    public void exportStatistics(
            String keyword,
            String bah,
            String sjh,
            String type,
            String startDate,
            String endDate,
            Writer writer
    ) throws IOException {
        StringBuilder sql = new StringBuilder("""
                select bah, cid, openerno, date, type, pages, sjh,
                       patientname, inpatientdepartment, patientid, dischargedate
                from mr_statistics
                where 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        appendStatisticsFilters(sql, arguments, keyword, bah, sjh, type, startDate, endDate);
        sql.append(" order by id limit ").append(EXPORT_LIMIT);

        writeHeader(writer, StatisticsDataExchangeService.TEMPLATE_HEADERS);
        streamQuery(sql.toString(), arguments, writer, resultSet -> Arrays.asList(
                resultSet.getString("bah"),
                resultSet.getString("cid"),
                resultSet.getString("openerno"),
                resultSet.getString("date"),
                resultSet.getString("type"),
                nullableNumber(resultSet, "pages"),
                resultSet.getString("sjh"),
                resultSet.getString("patientname"),
                resultSet.getString("inpatientdepartment"),
                resultSet.getString("patientid"),
                resultSet.getString("dischargedate")
        ));
    }

    public void exportArchives(
            String keyword,
            String bah,
            String sjh,
            String patientId,
            String type,
            String startDate,
            String endDate,
            List<String> headers,
            Writer writer
    ) throws IOException {
        StringBuilder sql = new StringBuilder("""
                select id, sjh, bah, patient_id, patient_name, inpatient_department,
                       device_id, operator_no, archive_date, discharge_date, archive_type,
                       page_count, source_statistics_id
                from mr_archive
                where 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        appendContains(sql, arguments, "bah", bah);
        appendContains(sql, arguments, "sjh", sjh);
        appendContains(sql, arguments, "patient_id", patientId);
        appendContains(sql, arguments, "archive_type", type);
        appendDateRange(sql, arguments, "archive_date", startDate, endDate);

        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            sql.append("""
                     and (
                         coalesce(patient_name, '') ilike ? escape '\\'
                         or coalesce(inpatient_department, '') ilike ? escape '\\'
                         or coalesce(device_id, '') ilike ? escape '\\'
                         or coalesce(operator_no, '') ilike ? escape '\\'
                     )
                    """);
            String pattern = containsPattern(normalizedKeyword);
            for (int index = 0; index < 4; index++) {
                arguments.add(pattern);
            }
        }
        sql.append(" order by id limit ").append(EXPORT_LIMIT);

        writeHeader(writer, headers);
        streamQuery(sql.toString(), arguments, writer, resultSet -> Arrays.asList(
                nullableNumber(resultSet, "id"),
                resultSet.getString("sjh"),
                resultSet.getString("bah"),
                resultSet.getString("patient_id"),
                resultSet.getString("patient_name"),
                resultSet.getString("inpatient_department"),
                resultSet.getString("device_id"),
                resultSet.getString("operator_no"),
                resultSet.getString("archive_date"),
                resultSet.getString("discharge_date"),
                resultSet.getString("archive_type"),
                nullableNumber(resultSet, "page_count"),
                nullableNumber(resultSet, "source_statistics_id")
        ));
    }

    public void exportArchiveBoxes(
            String keyword,
            String bah,
            String sjh,
            String boxNo,
            String status,
            Writer writer
    ) throws IOException {
        StringBuilder sql = new StringBuilder("""
                select bah, sjh, box_no, expected_box_no, status, remark
                from mr_archive_box_record
                where 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        appendContains(sql, arguments, "bah", bah);
        appendContains(sql, arguments, "sjh", sjh);
        appendContains(sql, arguments, "box_no", boxNo);
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            sql.append(" and status = ?");
            arguments.add(normalizedStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            sql.append("""
                     and (
                         coalesce(bah, '') ilike ?
                         or coalesce(sjh, '') ilike ?
                         or coalesce(box_no, '') ilike ?
                         or coalesce(expected_box_no, '') ilike ?
                         or coalesce(status, '') ilike ?
                         or coalesce(remark, '') ilike ?
                     )
                    """);
            String pattern = containsPattern(normalizedKeyword);
            for (int index = 0; index < 6; index++) {
                arguments.add(pattern);
            }
        }
        sql.append(" order by id limit ").append(EXPORT_LIMIT);

        writeHeader(writer, ArchiveBoxDataExchangeService.TEMPLATE_HEADERS);
        streamQuery(sql.toString(), arguments, writer, resultSet -> Arrays.asList(
                resultSet.getString("bah"),
                resultSet.getString("sjh"),
                resultSet.getString("box_no"),
                resultSet.getString("expected_box_no"),
                resultSet.getString("status"),
                resultSet.getString("remark")
        ));
    }

    public void exportScan(
            String bah,
            String sjh,
            String brxh,
            String folder,
            String filename,
            Integer btype,
            Long afterId,
            Writer writer
    ) throws IOException {
        StringBuilder sql = new StringBuilder("""
                select sjh, bah, brxh, folder, filename, btype, file_size
                from mr_scan
                where 1 = 1
                """);
        List<Object> arguments = new ArrayList<>();
        appendContains(sql, arguments, "bah", bah);
        appendContains(sql, arguments, "sjh", sjh);
        appendContains(sql, arguments, "brxh", brxh);
        appendContains(sql, arguments, "folder", folder);
        appendContains(sql, arguments, "filename", filename);
        if (btype != null) {
            if (btype < 0 || btype > 15) {
                throw new IllegalArgumentException("图片类型必须位于 0 到 15");
            }
            sql.append(" and btype = ?");
            arguments.add(btype);
        }
        if (afterId != null) {
            if (afterId < 0) {
                throw new IllegalArgumentException("起始 ID 不能小于 0");
            }
            sql.append(" and id > ?");
            arguments.add(afterId);
        }
        sql.append(" order by id limit ").append(EXPORT_LIMIT);

        writeHeader(writer, ScanDataExchangeService.TEMPLATE_HEADERS);
        streamQuery(sql.toString(), arguments, writer, resultSet -> Arrays.asList(
                resultSet.getString("sjh"),
                resultSet.getString("bah"),
                resultSet.getString("brxh"),
                resultSet.getString("folder"),
                resultSet.getString("filename"),
                nullableNumber(resultSet, "btype"),
                nullableNumber(resultSet, "file_size")
        ));
    }

    private void appendStatisticsFilters(
            StringBuilder sql,
            List<Object> arguments,
            String keyword,
            String bah,
            String sjh,
            String type,
            String startDate,
            String endDate
    ) {
        appendContains(sql, arguments, "bah", bah);
        appendContains(sql, arguments, "sjh", sjh);
        String normalizedType = trimToNull(type);
        if (normalizedType != null) {
            sql.append(" and type = ?");
            arguments.add(normalizedType);
        }
        String normalizedStartDate = trimToNull(startDate);
        if (normalizedStartDate != null) {
            sql.append(" and app.try_parse_date(date) >= app.try_parse_date(?)");
            arguments.add(normalizedStartDate);
        }
        String normalizedEndDate = trimToNull(endDate);
        if (normalizedEndDate != null) {
            sql.append(" and app.try_parse_date(date) <= app.try_parse_date(?)");
            arguments.add(normalizedEndDate);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            sql.append("""
                     and (
                         coalesce(cid, '') ilike ?
                         or coalesce(openerno, '') ilike ?
                         or coalesce(date, '') ilike ?
                         or coalesce(type, '') ilike ?
                         or coalesce(patientname, '') ilike ?
                         or coalesce(inpatientdepartment, '') ilike ?
                         or coalesce(patientid, '') ilike ?
                         or coalesce(dischargedate, '') ilike ?
                     )
                    """);
            String pattern = containsPattern(normalizedKeyword);
            for (int index = 0; index < 8; index++) {
                arguments.add(pattern);
            }
        }
    }

    private void appendDateRange(
            StringBuilder sql,
            List<Object> arguments,
            String column,
            String startDate,
            String endDate
    ) {
        String normalizedStartDate = trimToNull(startDate);
        if (normalizedStartDate != null) {
            sql.append(" and ").append(column).append(" >= app.try_parse_date(?)");
            arguments.add(normalizedStartDate);
        }
        String normalizedEndDate = trimToNull(endDate);
        if (normalizedEndDate != null) {
            sql.append(" and ").append(column).append(" <= app.try_parse_date(?)");
            arguments.add(normalizedEndDate);
        }
    }

    private void appendContains(
            StringBuilder sql,
            List<Object> arguments,
            String column,
            String value
    ) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        sql.append(" and coalesce(").append(column).append(", '') ilike ? escape '\\'");
        arguments.add(containsPattern(normalized));
    }

    private String containsPattern(String value) {
        return "%" + value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private void writeHeader(Writer writer, List<String> headers) throws IOException {
        writer.write('\uFEFF');
        writer.write(String.join(",", headers));
        writer.write('\n');
    }

    private void streamQuery(
            String sql,
            List<Object> arguments,
            Writer writer,
            RowValuesExtractor extractor
    ) throws IOException {
        try {
            jdbcTemplate.query(connection -> {
                var statement = connection.prepareStatement(sql);
                statement.setFetchSize(1_000);
                for (int index = 0; index < arguments.size(); index++) {
                    statement.setObject(index + 1, arguments.get(index));
                }
                return statement;
            }, (RowCallbackHandler) resultSet -> {
                try {
                    writeCsvRow(writer, extractor.extract(resultSet));
                } catch (IOException exception) {
                    throw new CsvWriteRuntimeException(exception);
                }
            });
            writer.flush();
        } catch (CsvWriteRuntimeException exception) {
            throw exception.getCause();
        }
    }

    private void writeCsvRow(Writer writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(index)));
        }
        writer.write('\n');
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String safeValue = DataExchangeImportSupport.protectSpreadsheetValue(value);
        boolean quoted = safeValue.indexOf(',') >= 0
                || safeValue.indexOf('"') >= 0
                || safeValue.indexOf('\r') >= 0
                || safeValue.indexOf('\n') >= 0;
        if (!quoted) {
            return safeValue;
        }
        return '"' + safeValue.replace("\"", "\"\"") + '"';
    }

    private String nullableNumber(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    private interface RowValuesExtractor {
        List<String> extract(ResultSet resultSet) throws SQLException;
    }

    private static final class CsvWriteRuntimeException extends RuntimeException {
        CsvWriteRuntimeException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
