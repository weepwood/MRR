package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.DataExchangeImportError;
import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.dto.resp.PatientImportResult;
import com.zjcxph.imgapi.service.ArchiveBoxDataExchangeService;
import com.zjcxph.imgapi.service.DataExchangeExportService;
import com.zjcxph.imgapi.service.PatientImportService;
import com.zjcxph.imgapi.service.ScanDataExchangeService;
import com.zjcxph.imgapi.service.StatisticsDataExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/data-exchange")
@Tag(name = "Data Exchange", description = "受控核心业务数据统一导入导出接口")
public class DataExchangeController {

    private static final Logger logger = LoggerFactory.getLogger(DataExchangeController.class);
    private static final List<String> PATIENT_HEADERS = List.of(
            "bah", "name", "idcard", "ruyuan", "admissiontime", "department", "bingqu", "chuangwei"
    );
    private static final List<String> ARCHIVE_HEADERS = List.of(
            "id", "sjh", "bah", "patient_id", "patient_name", "inpatient_department",
            "device_id", "operator_no", "archive_date", "discharge_date", "archive_type",
            "page_count", "source_statistics_id"
    );

    private final PatientImportService patientImportService;
    private final StatisticsDataExchangeService statisticsImportService;
    private final ArchiveBoxDataExchangeService archiveBoxImportService;
    private final ScanDataExchangeService scanImportService;
    private final DataExchangeExportService exportService;

    public DataExchangeController(
            PatientImportService patientImportService,
            StatisticsDataExchangeService statisticsImportService,
            ArchiveBoxDataExchangeService archiveBoxImportService,
            ScanDataExchangeService scanImportService,
            DataExchangeExportService exportService
    ) {
        this.patientImportService = patientImportService;
        this.statisticsImportService = statisticsImportService;
        this.archiveBoxImportService = archiveBoxImportService;
        this.scanImportService = scanImportService;
        this.exportService = exportService;
    }

    @Operation(summary = "校验或导入患者数据")
    @PostMapping(value = "/patients/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermissions({"record:edit"})
    public Result<DataExchangeImportResult> importPatients(
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "true 仅校验，false 正式导入")
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        try {
            PatientImportResult patientResult = patientImportService.importPatients(file, dryRun);
            return Result.success(dryRun ? "患者文件校验完成" : "患者数据导入完成", adaptPatientResult(patientResult));
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        } catch (IOException exception) {
            logger.warn("患者导入文件读取失败：file={}", safeFileName(file));
            return Result.fail("导入文件读取失败，请检查文件格式和编码");
        }
    }

    @Operation(summary = "校验或导入统计数据")
    @PostMapping(value = "/statistics/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermissions({"record:edit"})
    public Result<DataExchangeImportResult> importStatistics(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        try {
            DataExchangeImportResult result = statisticsImportService.importStatistics(file, dryRun);
            return Result.success(dryRun ? "统计文件校验完成" : "统计数据导入完成", result);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        } catch (IOException exception) {
            logger.warn("统计导入文件读取失败：file={}", safeFileName(file));
            return Result.fail("导入文件读取失败，请检查文件格式和编码");
        }
    }

    @Operation(summary = "校验或导入档案装箱数据")
    @PostMapping(value = "/archive-boxes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermissions({"record:edit"})
    public Result<DataExchangeImportResult> importArchiveBoxes(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        try {
            DataExchangeImportResult result = archiveBoxImportService.importArchiveBoxes(file, dryRun);
            return Result.success(dryRun ? "装箱文件校验完成" : "装箱数据导入完成", result);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        } catch (IOException exception) {
            logger.warn("装箱导入文件读取失败：file={}", safeFileName(file));
            return Result.fail("导入文件读取失败，请检查文件格式和编码");
        }
    }

    @Operation(summary = "校验或导入小批量扫描记录")
    @PostMapping(value = "/scan/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermissions({"record:edit"})
    public Result<DataExchangeImportResult> importScan(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        try {
            DataExchangeImportResult result = scanImportService.importScans(file, dryRun);
            return Result.success(dryRun ? "扫描记录文件校验完成" : "扫描记录导入完成", result);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        } catch (IOException exception) {
            logger.warn("扫描记录导入文件读取失败：file={}", safeFileName(file));
            return Result.fail("导入文件读取失败，请检查文件格式和编码");
        }
    }

    @Operation(summary = "下载患者数据导入模板")
    @GetMapping("/patients/template")
    @RequirePermissions({"record:read"})
    public void downloadPatientTemplate(HttpServletResponse response) throws IOException {
        writeTemplate(response, "mr_patient_template.csv", PATIENT_HEADERS);
    }

    @Operation(summary = "下载统计数据导入模板")
    @GetMapping("/statistics/template")
    @RequirePermissions({"statistics:read"})
    public void downloadStatisticsTemplate(HttpServletResponse response) throws IOException {
        writeTemplate(response, "mr_statistics_template.csv", StatisticsDataExchangeService.TEMPLATE_HEADERS);
    }

    @Operation(summary = "下载档案装箱导入模板")
    @GetMapping("/archive-boxes/template")
    @RequirePermissions({"record:read"})
    public void downloadArchiveBoxTemplate(HttpServletResponse response) throws IOException {
        writeTemplate(response, "mr_archive_box_record_template.csv", ArchiveBoxDataExchangeService.TEMPLATE_HEADERS);
    }

    @Operation(summary = "下载小批量扫描记录导入模板")
    @GetMapping("/scan/template")
    @RequirePermissions({"record:read"})
    public void downloadScanTemplate(HttpServletResponse response) throws IOException {
        writeTemplate(response, "mr_scan_template.csv", ScanDataExchangeService.TEMPLATE_HEADERS);
    }

    @Operation(summary = "按条件导出患者 CSV")
    @GetMapping("/patients/export/csv")
    @RequirePermissions({"record:read"})
    public void exportPatients(
            @RequestParam(required = false) String keyword,
            HttpServletResponse response
    ) throws IOException {
        writeCsv(response, "mr_patient.csv", writer -> exportService.exportPatients(keyword, writer));
    }

    @Operation(summary = "按条件导出可重新导入的统计 CSV")
    @GetMapping("/statistics/export/csv")
    @RequirePermissions({"statistics:read"})
    public void exportStatistics(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response
    ) throws IOException {
        writeCsv(response, "mr_statistics.csv",
                writer -> exportService.exportStatistics(keyword, bah, sjh, type, startDate, endDate, writer));
    }

    @Operation(summary = "按条件导出病案主档 CSV")
    @GetMapping("/archives/export/csv")
    @RequirePermissions({"record:read"})
    public void exportArchives(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response
    ) throws IOException {
        writeCsv(response, "mr_archive.csv",
                writer -> exportService.exportArchives(
                        keyword, bah, sjh, patientId, type, startDate, endDate, ARCHIVE_HEADERS, writer
                ));
    }

    @Operation(summary = "按条件导出可重新导入的档案装箱 CSV")
    @GetMapping("/archive-boxes/export/csv")
    @RequirePermissions({"record:read"})
    public void exportArchiveBoxes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) String boxNo,
            @RequestParam(required = false) String status,
            HttpServletResponse response
    ) throws IOException {
        writeCsv(response, "mr_archive_box_record.csv",
                writer -> exportService.exportArchiveBoxes(keyword, bah, sjh, boxNo, status, writer));
    }

    @Operation(summary = "按条件导出扫描记录 CSV")
    @GetMapping("/scan/export/csv")
    @RequirePermissions({"record:read"})
    public void exportScan(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam(required = false) String brxh,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) Integer btype,
            @RequestParam(required = false) Long afterId,
            HttpServletResponse response
    ) throws IOException {
        writeCsv(response, "mr_scan.csv",
                writer -> exportService.exportScan(bah, sjh, brxh, folder, filename, btype, afterId, writer));
    }

    private DataExchangeImportResult adaptPatientResult(PatientImportResult source) {
        List<DataExchangeImportError> errors = source.errors().stream()
                .map(error -> new DataExchangeImportError(
                        error.rowNumber(), error.field(), error.message(), error.value()
                ))
                .toList();
        return new DataExchangeImportResult(
                "MR_PATIENT",
                source.fileName(),
                source.encoding(),
                source.dryRun(),
                source.canImport(),
                source.totalRows(),
                source.validRows(),
                source.insertedRows(),
                0,
                source.duplicateRows(),
                source.errorRows(),
                source.errorsTruncated(),
                errors
        );
    }

    private void writeTemplate(
            HttpServletResponse response,
            String fileName,
            List<String> headers
    ) throws IOException {
        writeCsv(response, fileName, writer -> {
            writer.write('\uFEFF');
            writer.write(String.join(",", headers));
            writer.write("\r\n");
        });
    }

    private void writeCsv(
            HttpServletResponse response,
            String fileName,
            CsvWriterAction action
    ) throws IOException {
        prepareCsvResponse(response, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                response.getOutputStream(),
                StandardCharsets.UTF_8
        )) {
            action.write(writer);
        }
    }

    private void prepareCsvResponse(HttpServletResponse response, String fileName) {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Export-Row-Limit", "100000");
    }

    private String safeFileName(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null) {
            return "unknown";
        }
        String fileName = file.getOriginalFilename().replace('\\', '/');
        return fileName.substring(fileName.lastIndexOf('/') + 1);
    }

    @FunctionalInterface
    private interface CsvWriterAction {
        void write(OutputStreamWriter writer) throws IOException;
    }
}
