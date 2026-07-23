package com.zjcxph.imgapi.service.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class TabularImportFileReader {

    private static final DateTimeFormatter NORMALIZED_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ParsedTable read(MultipartFile file, long maxFileSize, int maxRows) throws IOException {
        validateFile(file, maxFileSize);
        String fileName = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        ParsedTable parsed = fileName.endsWith(".csv") ? parseCsv(file.getBytes()) : parseWorkbook(file);
        if (parsed.rows().size() > maxRows) {
            throw new IllegalArgumentException("单次最多导入 " + maxRows + " 行数据，请拆分文件后重试");
        }
        return parsed;
    }

    public String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "data";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private void validateFile(MultipartFile file, long maxFileSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择需要导入的数据文件");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("导入文件不能超过 " + (maxFileSize / 1024 / 1024) + " MB");
        }
        String fileName = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!(fileName.endsWith(".csv") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls"))) {
            throw new IllegalArgumentException("仅支持 CSV、XLSX 或 XLS 文件");
        }
    }

    private ParsedTable parseCsv(byte[] bytes) {
        DecodedText decoded = decodeCsv(bytes);
        List<List<String>> records = parseCsvRecords(decoded.text());
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV 文件没有表头");
        }
        for (List<String> record : records) {
            for (int column = 0; column < record.size(); column++) {
                record.set(column, DataExchangeImportSupport.restoreSpreadsheetProtectedValue(record.get(column)));
            }
        }
        List<SourceRow> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            rows.add(new SourceRow(index + 1, records.get(index)));
        }
        return new ParsedTable(decoded.encoding(), records.getFirst(), rows);
    }

    private ParsedTable parseWorkbook(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel 文件没有工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel 文件没有表头");
            }

            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int columnCount = Math.max(0, headerRow.getLastCellNum());
            List<String> headers = new ArrayList<>(columnCount);
            for (int column = 0; column < columnCount; column++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(column), evaluator));
            }

            List<SourceRow> rows = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> values = new ArrayList<>(columnCount);
                for (int column = 0; column < columnCount; column++) {
                    Cell cell = row == null ? null : row.getCell(column);
                    values.add(formatExcelCell(cell, formatter, evaluator));
                }
                rows.add(new SourceRow(rowIndex + 1, values));
            }
            return new ParsedTable("Excel", headers, rows);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Excel 文件无法读取，请确认文件未损坏且格式正确", exception);
        }
    }

    private String formatExcelCell(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        if (DateUtil.isCellDateFormatted(cell)) {
            try {
                LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                if (LocalTime.MIDNIGHT.equals(dateTime.toLocalTime())) {
                    return dateTime.toLocalDate().toString();
                }
                return dateTime.format(NORMALIZED_DATE_TIME);
            } catch (RuntimeException ignored) {
                // 回退到 DataFormatter，由业务导入器继续校验。
            }
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private DecodedText decodeCsv(byte[] bytes) {
        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new DecodedText(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8), "UTF-8 (BOM)");
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new DecodedText(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new DecodedText(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }

        Charset utf16 = detectUtf16WithoutBom(bytes);
        if (utf16 != null) {
            return new DecodedText(new String(bytes, utf16), utf16.displayName());
        }

        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DecodedText(text, "UTF-8");
        } catch (CharacterCodingException ignored) {
            Charset gb18030 = Charset.forName("GB18030");
            return new DecodedText(new String(bytes, gb18030), "GB18030");
        }
    }

    private Charset detectUtf16WithoutBom(byte[] bytes) {
        int sampleLength = Math.min(bytes.length, 512);
        if (sampleLength < 8) {
            return null;
        }
        int zeroEven = 0;
        int zeroOdd = 0;
        for (int index = 0; index < sampleLength; index++) {
            if (bytes[index] == 0) {
                if ((index & 1) == 0) {
                    zeroEven++;
                } else {
                    zeroOdd++;
                }
            }
        }
        int pairs = sampleLength / 2;
        if (zeroOdd > pairs / 3 && zeroEven < pairs / 10) {
            return StandardCharsets.UTF_16LE;
        }
        if (zeroEven > pairs / 3 && zeroOdd < pairs / 10) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private List<List<String>> parseCsvRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            if (current == '"' && field.isEmpty()) {
                quoted = true;
            } else if (current == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (current == '\r' || current == '\n') {
                row.add(field.toString());
                field.setLength(0);
                records.add(row);
                row = new ArrayList<>();
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                field.append(current);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("CSV 文件存在未闭合的引号");
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            records.add(row);
        }
        while (!records.isEmpty() && records.getLast().stream().allMatch(String::isBlank)) {
            records.removeLast();
        }
        return records;
    }

    public record ParsedTable(String encoding, List<String> headers, List<SourceRow> rows) {
    }

    public record SourceRow(int rowNumber, List<String> values) {
    }

    private record DecodedText(String text, String encoding) {
    }
}
