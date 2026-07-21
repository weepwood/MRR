package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.BatchDownloadRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveExportPlanResponse;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ArchiveExportJobService;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/archive-exports")
@Tag(name = "Archive Export", description = "病案 ZIP/PDF 统一导出接口")
@RequirePermissions({"record:read"})
public class ArchiveExportController {

    private static final int CLIENT_PDF_MAX_IMAGES = 20;
    private static final int MAX_SELECTED_EXPORT_COUNT = 200;
    private static final String BAH_REQUIRES_SJH_MESSAGE =
            "病案号大于等于 10000000 时必须使用上架号导出";

    private final ArchiveExportService archiveExportService;
    private ArchiveExportJobService archiveExportJobService;

    public ArchiveExportController(ArchiveExportService archiveExportService) {
        this.archiveExportService = archiveExportService;
    }

    @Autowired
    void setArchiveExportJobService(ArchiveExportJobService archiveExportJobService) {
        this.archiveExportJobService = archiveExportJobService;
    }

    @Operation(summary = "规划 ZIP 导出执行方式")
    @GetMapping("/plan/zip")
    @RequirePermissions({"record:download"})
    public Result<ArchiveExportPlanResponse> planZip(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh) {
        PreparedArchive prepared = prepareArchive(bah, sjh);
        // ZIP 必须在服务器完成全部图片读取、压缩和文件校验后再提供下载。
        // 继续保留同步接口仅用于旧客户端兼容，新前端统一使用后台任务，
        // 避免流式响应在中途失败后仍向浏览器留下损坏 ZIP。
        String executionMode = "BACKEND_JOB";
        return Result.success(planResponse(
                "ZIP", executionMode, prepared.export().itemCount(),
                prepared.export().itemCount(), true, prepared.export()));
    }

    @Operation(summary = "规划 PDF 导出执行方式")
    @GetMapping("/plan/pdf")
    @RequirePermissions({"record:pdf:export"})
    public Result<ArchiveExportPlanResponse> planPdf(
            @RequestParam(required = false) String bah,
            @RequestParam(required = false) String sjh,
            @RequestParam int selectedCount) {
        if (selectedCount <= 0) {
            throw new BusinessException(400, "请选择要导出 PDF 的影像");
        }

        PreparedArchive prepared = prepareArchive(bah, sjh);
        int totalCount = prepared.export().itemCount();
        if (selectedCount > totalCount) {
            throw new BusinessException(400, "选中影像数量超过当前病案总数，请刷新后重试");
        }
        boolean wholeArchive = selectedCount == totalCount;
        if (!wholeArchive && selectedCount > MAX_SELECTED_EXPORT_COUNT
                && !shouldUseJob(prepared.export())) {
            throw new BusinessException(400, "部分选择最多同步导出 200 张影像");
        }

        String executionMode;
        if (!wholeArchive && selectedCount <= CLIENT_PDF_MAX_IMAGES) {
            executionMode = "CLIENT_PDF";
        } else if (shouldUseJob(prepared.export())) {
            executionMode = "BACKEND_JOB";
        } else {
            executionMode = "BACKEND_STREAM";
        }
        return Result.success(planResponse(
                "PDF", executionMode, selectedCount, totalCount,
                wholeArchive, prepared.export()));
    }

    @Operation(summary = "流式下载整份病案 ZIP")
    @GetMapping("/zip")
    @RequirePermissions({"record:download"})
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
    @RequirePermissions({"record:pdf:export"})
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
    @RequirePermissions({"record:pdf:export"})
    public ResponseEntity<StreamingResponseBody> downloadSelectedPdf(
            @RequestBody BatchDownloadRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException(400, "请选择要导出 PDF 的影像");
        }
        if (request.getIds().size() > MAX_SELECTED_EXPORT_COUNT) {
            throw new BusinessException(400, "单次同步导出最多 200 张选中影像，请使用异步导出");
        }

        ArchiveExportService.BatchZipExport export =
                archiveExportService.prepareSelectedArchive(request.getIds());
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

    private ArchiveExportPlanResponse planResponse(
            String format,
            String executionMode,
            int selectedCount,
            int totalCount,
            boolean wholeArchive,
            ArchiveExportService.BatchZipExport export) {
        return new ArchiveExportPlanResponse(
                format,
                executionMode,
                selectedCount,
                totalCount,
                CLIENT_PDF_MAX_IMAGES,
                wholeArchive,
                export.estimatedBytes(),
                new ArrayList<>(export.sourceSummary())
        );
    }

    private boolean shouldUseJob(ArchiveExportService.BatchZipExport export) {
        return archiveExportJobService != null && archiveExportJobService.shouldUseJob(export);
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
