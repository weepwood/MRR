package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.BatchDownloadRequest;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.dto.resp.CursorPageResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scan")
@Tag(name = "Scan Management", description = "病案扫描记录管理接口")
@RequirePermissions({"record:read"})
public class ScanController {

    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);
    private static final int MAX_LEGACY_QUERY_LIMIT = 1000;
    private static final int MAX_BATCH_DOWNLOAD_COUNT = 200;

    private final ScanService scanService;
    private final ArchiveExportService archiveExportService;

    public ScanController(ScanService scanService, ArchiveExportService archiveExportService) {
        this.scanService = scanService;
        this.archiveExportService = archiveExportService;
    }

    @Operation(summary = "创建新的扫描记录")
    @PostMapping
    @RequirePermissions({"record:edit"})
    public Result<Scan> create(@Valid @RequestBody ScanRequest request) {
        logger.info("创建扫描记录：BAH={}", request.getBah());
        Scan scan = new Scan(
                null,
                request.getBrxh(),
                request.getBah(),
                request.getSjh(),
                request.getFilename(),
                request.getBtype(),
                request.getPages(),
                request.getOpenerNo(),
                null,
                request.getUploadFlag(),
                request.getFolder()
        );

        Scan created = scanService.create(scan);
        return created == null ? Result.fail("创建扫描记录失败") : Result.success(created);
    }

    @Operation(summary = "根据 ID 删除扫描记录")
    @DeleteMapping("/{id}")
    @RequirePermissions({"record:edit"})
    public Result<String> deleteById(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        if (id == null) {
            return Result.fail("ID 不能为空");
        }
        return scanService.softDeleteById(id)
                ? Result.success("删除成功")
                : Result.fail("删除失败，记录不存在");
    }

    @Operation(summary = "更新扫描记录")
    @PutMapping("/{id}")
    @RequirePermissions({"record:edit"})
    public Result<Scan> update(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id,
            @Valid @RequestBody ScanRequest request) {
        if (id == null) {
            return Result.fail("ID 不能为空");
        }

        Scan scan = new Scan(
                id,
                request.getBrxh(),
                request.getBah(),
                request.getSjh(),
                request.getFilename(),
                request.getBtype(),
                request.getPages(),
                request.getOpenerNo(),
                null,
                request.getUploadFlag(),
                request.getFolder()
        );
        Scan updated = scanService.update(scan);
        return updated == null ? Result.fail("更新失败，记录不存在") : Result.success(updated);
    }

    /**
     * 兼容旧客户端的有限查询。SQL 在数据库端最多返回 1000 条，不再加载全表后截断。
     */
    @Deprecated
    @Operation(summary = "获取扫描记录（兼容接口，最多 1000 条；建议使用 /cursor）")
    @GetMapping
    public Result<List<Scan>> findAll() {
        logger.warn("兼容 findAll 接口被调用，建议改用 /api/v1/scan/cursor");
        return Result.success(scanService.findAll(MAX_LEGACY_QUERY_LIMIT));
    }

    @Operation(summary = "按 ID 游标分页查询扫描记录")
    @GetMapping("/cursor")
    public Result<CursorPageResult<Scan>> findAfterId(
            @RequestParam(defaultValue = "0") Integer afterId,
            @RequestParam(defaultValue = "100") int size) {
        return Result.success(scanService.findAfterId(afterId, size));
    }

    @Operation(summary = "根据 ID 查询扫描记录")
    @GetMapping("/{id}")
    public Result<Scan> findById(
            @PathVariable
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        if (id == null) {
            return Result.fail("ID 不能为空");
        }
        Scan scan = scanService.findById(id);
        return scan == null ? Result.fail("未找到该扫描记录") : Result.success(scan);
    }

    @Operation(summary = "根据病案号查询扫描记录")
    @GetMapping("/bah/{bah}")
    public Result<List<Scan>> findByBah(
            @PathVariable
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        if (bah == null || bah.isEmpty()) {
            return Result.fail("病案号不能为空");
        }
        return Result.success(scanService.findByBah(bah));
    }

    @Operation(summary = "根据病人序号查询扫描记录")
    @GetMapping("/brxh/{brxh}")
    public Result<List<Scan>> findByBrxh(
            @PathVariable
            @Parameter(description = "病人序号", example = "605746")
            String brxh) {
        if (brxh == null || brxh.isEmpty()) {
            return Result.fail("病人序号不能为空");
        }
        return Result.success(scanService.findByBrxh(brxh));
    }

    @Operation(summary = "分页查询所有扫描记录")
    @GetMapping("/page")
    public Result<PageResult<Scan>> findAllWithPagination(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginationUtils.validatePageParams(page, size);
        List<Scan> scans = scanService.findAllWithPagination(page, size);
        long total = scanService.countByCondition(new ScanRequest());
        return Result.success(PageResult.of(scans, total, page, size));
    }

    /**
     * 兼容旧客户端的有限条件查询。新页面应使用 /page/condition。
     */
    @Deprecated
    @Operation(summary = "根据条件查询扫描记录（兼容接口，最多 1000 条）")
    @PostMapping("/condition")
    @RequirePermissions({"record:read"})
    public Result<List<Scan>> findByCondition(@RequestBody ScanRequest request) {
        logger.warn("兼容 condition 接口被调用，建议改用 /api/v1/scan/page/condition");
        return Result.success(scanService.findByCondition(request, MAX_LEGACY_QUERY_LIMIT));
    }

    @Operation(summary = "根据条件分页查询扫描记录")
    @PostMapping("/page/condition")
    @RequirePermissions({"record:read"})
    public Result<PageResult<Scan>> findByConditionWithPagination(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestBody ScanRequest request) {
        PaginationUtils.validatePageParams(page, size);
        List<Scan> scans = scanService.findByConditionWithPagination(request, page, size);
        long total = scanService.countByCondition(request);
        return Result.success(PageResult.of(scans, total, page, size));
    }

    @Operation(summary = "批量下载病案图片（ZIP，流式传输）")
    @PostMapping("/batch-download")
    @RequirePermissions({"record:download"})
    public ResponseEntity<StreamingResponseBody> batchDownload(@RequestBody BatchDownloadRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getIds().size() > MAX_BATCH_DOWNLOAD_COUNT) {
            return ResponseEntity.badRequest().build();
        }

        ArchiveExportService.BatchZipExport export = archiveExportService.prepareBatch(request.getIds());
        if (export.itemCount() == 0) {
            return ResponseEntity.badRequest().build();
        }

        StreamingResponseBody body = outputStream -> archiveExportService.writeBatchZip(export, outputStream);
        String fileName = "scan-batch-" + System.currentTimeMillis() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
