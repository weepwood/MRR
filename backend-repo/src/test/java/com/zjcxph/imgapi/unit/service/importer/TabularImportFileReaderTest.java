package com.zjcxph.imgapi.unit.service.importer;

import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularImportFileReaderTest {
    private final TabularImportFileReader reader = new TabularImportFileReader();

    @ParameterizedTest
    @ValueSource(strings = {"1,2,private-extra", "\"1\"suffix,2", "1\"oops,2", "\"unclosed,2"})
    void rejectsMalformedCsvWithoutEchoingValues(String row) {
        assertThatThrownBy(() -> readCsv("a,b\n" + row, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2")
                .hasMessageNotContaining("private-extra")
                .hasMessageNotContaining("suffix")
                .hasMessageNotContaining("oops")
                .hasMessageNotContaining("unclosed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void rejectsExcelDataOutsideHeaders(String extension) throws Exception {
        try (Workbook workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("a");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("one");
            row.createCell(2).setCellValue("private-extra");
            assertThatThrownBy(() -> reader.read(excel(workbook, extension), 1_000_000, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2")
                    .hasMessageNotContaining("private-extra");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void rejectsHiddenNumericDataOutsideHeaders(String extension) throws Exception {
        try (Workbook workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("a");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("one");
            var hidden = row.createCell(1);
            hidden.setCellValue(123);
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("[>0]\"\";General"));
            hidden.setCellStyle(style);
            assertThatThrownBy(() -> reader.read(excel(workbook, extension), 1_000_000, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("第 2 行")
                    .hasMessageNotContaining("123");
        }
    }

    @Test
    void acceptsSparseExcelRowsAndFormattingOutsideHeaders() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("a");
            header.createCell(1).setCellValue("b");
            sheet.createRow(1).createCell(0).setCellValue("one");
            sheet.getRow(1).createCell(5).setCellStyle(workbook.createCellStyle());
            var table = reader.read(excel(workbook, "xlsx"), 1_000_000, 10);
            assertThat(table.rows().getFirst().values()).containsExactly("one", "");
        }
    }

    @Test
    void stopsAtRowLimitBeforeParsingTheRestOfCsv() {
        assertThatThrownBy(() -> readCsv("a,b\n1,2\n3,4\n\"unclosed", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多导入 1 行");
    }

    @Test
    void keepsCsvEscapesAndLogicalRowNumbersAcrossBlankAndMultilineRows() throws Exception {
        var table = readCsv("a,b\r\n\r\n\"one\r\ntwo\",\"say \"\"hi\"\"\"\r\n3\r\n", 2);
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().getFirst().rowNumber()).isEqualTo(3);
        assertThat(table.rows().getFirst().values()).containsExactly("one\r\ntwo", "say \"hi\"");
        assertThat(table.rows().getLast().values()).containsExactly("3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16", "GB18030"})
    void preservesSupportedEncodings(String encoding) throws Exception {
        var file = new MockMultipartFile("file", "data.csv", "text/csv",
                "a,b\n中文,备注".getBytes(Charset.forName(encoding)));
        var table = reader.read(file, 1_000_000, 10);
        assertThat(table.rows().getFirst().values()).containsExactly("中文", "备注");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void preservesExcelDatesIncludingMidnightAdmissionTime(String extension) throws Exception {
        try (Workbook workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("ruyuan");
            header.createCell(1).setCellValue("admissiontime");
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            var row = sheet.createRow(1);
            for (int column = 0; column < 2; column++) {
                var cell = row.createCell(column);
                cell.setCellValue(LocalDateTime.of(2024, 2, 29, 0, 0));
                cell.setCellStyle(style);
            }
            var table = reader.read(excel(workbook, extension), 1_000_000, 10);
            assertThat(table.rows().getFirst().values())
                    .containsExactly("2024-02-29", "2024-02-29 00:00:00");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void enforcesExcelRowLimitIgnoringBlankRows(String extension) throws Exception {
        try (Workbook workbook = extension.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("a");
            sheet.createRow(2).createCell(0).setCellValue("one");
            sheet.createRow(3).createCell(0).setCellValue("two");
            assertThatThrownBy(() -> reader.read(excel(workbook, extension), 1_000_000, 1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("最多导入 1 行");
        }
    }

    private TabularImportFileReader.ParsedTable readCsv(String text, int maxRows) throws Exception {
        return reader.read(new MockMultipartFile("file", "data.csv", "text/csv",
                text.getBytes(StandardCharsets.UTF_8)), 1_000_000, maxRows);
    }

    private MockMultipartFile excel(Workbook workbook, String extension) throws Exception {
        var output = new ByteArrayOutputStream();
        workbook.write(output);
        return new MockMultipartFile("file", "data." + extension, "application/octet-stream", output.toByteArray());
    }
}
