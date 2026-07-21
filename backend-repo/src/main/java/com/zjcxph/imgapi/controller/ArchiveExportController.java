package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.dto.req.BatchDownloadRequest;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/archive-exports")
@Tag(name = "Archive Export", description = "病案 ZIP/PDF 统一导出接口")
@RequirePermissions({"record:read"})
public class ArchiveExportController {

    private static final int MAX_SELECTED_EXPORT_COUNT = 200;
    private static final String BAH_REQUIRES_SJH_MESSAGE =
            "病案号大于等于 10000000 时必须使用上架号导出";

    private final ArchiveExportService archiveExportService;

    public ArchiveExportController(ArchiveExportService archiveExportService) {
        this.archiveExportService = archiveExportService;
    }

    @Operation(summary = "流式下载整份病案 ZIP")
    @GetMapping("/zip")
    public ResponseEntity<StreamingResponseBody> downloadZip(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh) {
        PreparedArchive prepared = prepareArchive(bah, sjh);
        StreamingResponseBody body = outputStream ->
                archiveExportService.writeBatchZip(prepared.export(), outputStream);
        return attachment(body, prepared.fileStem() + ".zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    @Operation(summary = "流式导出整份病案 PDF")
    @GetMapping("/pdf")
    public ResponseEntity<StreamingResponseBody> downloadPdf(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh) {
        PreparedArchive prepared = prepareArchive(bah, sjh);
        StreamingResponseBody body = outputStream ->
                archiveExportService.writeBatchPdf(prepared.export(), outputStream);
        return attachment(body, prepared.fileStem() + ".pdf", MediaType.APPLICATION_PDF);
    }

    @Operation(summary = "流式导出选中影像 PDF")
    @PostMapping("/pdf/selection")
    public ResponseEntity<StreamingResponseBody> downloadSelectedPdf(
            @RequestBody BatchDownloadRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException(400, "请选择要导出 PDF 的影像");
        }
        if (request.getIds().size() > MAX_SELECTED_EXPORT_COUNT) {
            throw new BusinessException(400, "单次最多导出 200 张选中影像");
        }

        ArchiveExportService.BatchZipExport export =
                archiveExportService.prepareBatch(request.getIds());
        if (export.itemCount() == 0) {
            throw new BusinessException(404, "未找到可导出的影像");
        }

        StreamingResponseBody body = outputStream ->
                archiveExportService.writeBatchPdf(export, outputStream);
        return attachment(
                body,
                "archive-selected-" + System.currentTimeMillis() + ".pdf",
                MediaType.APPLICATION_PDF
        );
    }

    private PreparedArchive prepareArchive(String bah, String sjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (normalizedBah.isEmpty() && normalizedSjh.isEmpty()) {
            throw new BusinessException(400, "病案号和上架号不能同时为空");
        }
        if (MedicalRecordCodeUtils.requiresSjhForBah(normalizedBah) && normalizedSjh.isEmpty()) {
            throw new BusinessException(400, BAH_REQUIRES_SJH_MESSAGE);
        }

        ArchiveExportService.BatchZipExport export =
                archiveExportService.prepareArchive(normalizedBah, normalizedSjh);
        if (export.itemCount() == 0) {
            throw new BusinessException(404, "未找到匹配档案的图片");
        }

        String fileStem = normalizedBah.isEmpty() ? "archive" : normalizedBah;
        if (!normalizedSjh.isEmpty()) {
            fileStem += "-" + normalizedSjh;
        }
        return new PreparedArchive(export, fileStem);
    }

    private ResponseEntity<StreamingResponseBody> attachment(
            StreamingResponseBody body,
            String fileName,
            MediaType mediaType) {
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(mediaType)
                .body(body);
    }

    private record PreparedArchive(
            ArchiveExportService.BatchZipExport export,
            String fileStem) {
    }
}
