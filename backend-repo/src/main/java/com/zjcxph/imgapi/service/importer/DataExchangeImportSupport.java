package com.zjcxph.imgapi.service.importer;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportError;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DataExchangeImportSupport {

    public static final int MAX_REPORTED_ERRORS = 200;
    public static final int MAX_CELL_LENGTH = 2_000;
    private static final BigInteger HIGH_BAH_THRESHOLD = BigInteger.valueOf(10_000_000L);
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private DataExchangeImportSupport() {
    }

    public static List<String> validateHeaders(
            List<String> rawHeaders,
            Set<String> requiredHeaders,
            Set<String> optionalHeaders,
            String templateName,
            ErrorCollector errors
    ) {
        List<String> normalizedHeaders = new ArrayList<>(rawHeaders.size());
        Set<String> seen = new LinkedHashSet<>();
        Set<String> allowed = new LinkedHashSet<>(requiredHeaders);
        allowed.addAll(optionalHeaders);

        for (String rawHeader : rawHeaders) {
            String header = normalizeHeader(rawHeader);
            normalizedHeaders.add(header);
            if (header.isEmpty()) {
                errors.add(1, "header", "表头中存在空字段名", "");
            } else if (!seen.add(header)) {
                errors.add(1, header, "字段名称重复", header);
            } else if (!allowed.contains(header)) {
                errors.add(1, header, "未知字段；请使用" + templateName + "中的字段名称", header);
            }
        }

        for (String requiredHeader : requiredHeaders) {
            if (!seen.contains(requiredHeader)) {
                errors.add(1, requiredHeader, "缺少必需字段", requiredHeader);
            }
        }
        return normalizedHeaders;
    }

    public static Map<String, String> toValueMap(
            List<String> headers,
            List<String> row,
            Set<String> ignoredHeaders
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int column = 0; column < headers.size(); column++) {
            String header = headers.get(column);
            if (!header.isEmpty() && !ignoredHeaders.contains(header)) {
                values.put(header, column < row.size() ? row.get(column) : "");
            }
        }
        return values;
    }

    public static boolean isBlankRow(List<String> values) {
        return values.stream().allMatch(value -> value == null || value.trim().isEmpty());
    }

    public static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static String normalizeMedicalRecordCode(String value) {
        return normalizeText(value);
    }

    public static String normalizeStatus(String value, String defaultValue) {
        String normalized = normalizeText(value);
        return normalized == null ? defaultValue : normalized.toUpperCase(Locale.ROOT);
    }

    public static LocalDate parseDate(
            int rowNumber,
            String field,
            String rawValue,
            ErrorCollector errors
    ) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('/', '-').replace('.', '-');
        int space = normalized.indexOf(' ');
        int separator = space >= 0 ? space : normalized.indexOf('T');
        if (separator > 0) {
            normalized = normalized.substring(0, separator);
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种日期格式。
            }
        }
        errors.add(rowNumber, field, "日期必须是 YYYY-MM-DD", value);
        return null;
    }

    public static Integer parseNonNegativeInteger(
            int rowNumber,
            String field,
            String rawValue,
            ErrorCollector errors
    ) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d+")) {
            errors.add(rowNumber, field, "必须为空或非负整数", value);
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            errors.add(rowNumber, field, "数值超过整数允许范围", value);
            return null;
        }
    }

    public static Long parseNonNegativeLong(
            int rowNumber,
            String field,
            String rawValue,
            ErrorCollector errors
    ) {
        String value = normalizeText(rawValue);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d+")) {
            errors.add(rowNumber, field, "必须为空或非负整数", value);
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            errors.add(rowNumber, field, "数值超过长整数允许范围", value);
            return null;
        }
    }

    public static void validateIdentifier(
            int rowNumber,
            String field,
            String value,
            ErrorCollector errors
    ) {
        if (value != null && value.matches("[+-]?\\d+(?:\\.\\d+)?[eE][+-]?\\d+")) {
            errors.add(rowNumber, field, "编号疑似被表格软件转换为科学计数法", value);
        }
        validateLength(rowNumber, field, value, errors);
    }

    public static void validateLength(
            int rowNumber,
            String field,
            String value,
            ErrorCollector errors
    ) {
        if (value != null && value.length() > MAX_CELL_LENGTH) {
            errors.add(rowNumber, field, "字段内容超过 " + MAX_CELL_LENGTH + " 个字符", value);
        }
    }

    public static boolean isHighNumericBah(String bah) {
        if (bah == null || !bah.matches("\\d+")) {
            return false;
        }
        try {
            return new BigInteger(bah).compareTo(HIGH_BAH_THRESHOLD) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * 为 Excel/WPS 打开 CSV 时可能被识别为公式或数字的内容增加可逆文本保护。
     * 同时保护前导零编号和超过电子表格精度上限的长整数。
     */
    public static String protectSpreadsheetValue(String value) {
        if (value == null || value.isEmpty() || !needsSpreadsheetProtection(value)) {
            return value;
        }
        return "'" + value;
    }

    /**
     * 还原由 {@link #protectSpreadsheetValue(String)} 增加的单个文本保护前缀。
     */
    public static String restoreSpreadsheetProtectedValue(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '\'') {
            return value;
        }
        String candidate = value.substring(1);
        return needsSpreadsheetProtection(candidate) ? candidate : value;
    }

    private static boolean needsSpreadsheetProtection(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '\'') {
            index++;
        }
        if (index >= value.length()) {
            return false;
        }
        String candidate = value.substring(index);
        char first = candidate.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            return true;
        }
        return candidate.matches("0\\d+") || candidate.matches("\\d{16,}");
    }

    public static String value(Object value) {
        return value == null ? "\u0000" : value.toString();
    }

    public static final class ErrorCollector {
        private final List<DataExchangeImportError> errors = new ArrayList<>();
        private final Set<Integer> errorRows = new LinkedHashSet<>();
        private int errorCount;
        private boolean truncated;

        public void add(int rowNumber, String field, String message, String rawValue) {
            errorCount++;
            errorRows.add(rowNumber);
            if (errors.size() >= MAX_REPORTED_ERRORS) {
                truncated = true;
                return;
            }
            errors.add(new DataExchangeImportError(
                    rowNumber,
                    field,
                    message,
                    maskValue(field, rawValue)
            ));
        }

        public int size() {
            return errorCount;
        }

        public boolean hasErrors() {
            return !errorRows.isEmpty();
        }

        public int errorRowCount() {
            return errorRows.size();
        }

        public boolean truncated() {
            return truncated;
        }

        public List<DataExchangeImportError> errors() {
            return errors;
        }

        private String maskValue(String field, String rawValue) {
            if (rawValue == null) {
                return "";
            }
            String value = rawValue.strip();
            String normalizedField = field == null ? "" : field.toLowerCase(Locale.ROOT);
            if (("patientid".equals(normalizedField) || "idcard".equals(normalizedField))
                    && value.length() > 7) {
                value = value.substring(0, 3)
                        + "*".repeat(value.length() - 7)
                        + value.substring(value.length() - 4);
            }
            return value.length() <= 80 ? value : value.substring(0, 80) + "…";
        }
    }
}
