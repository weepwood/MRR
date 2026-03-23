package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.BatchDownloadRequest;
import com.zjcxph.imgapi.pojo.PathDO;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.pojo.Scan;
import com.zjcxph.imgapi.pojo.ScanRequest;
import com.zjcxph.imgapi.service.ScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Scan Controller
 * 提供 mr_scan 表的增删改查功能
 */
@RestController
@RequestMapping("/v1/scan-api")
@Tag(name = "Scan Management", description = "病案扫描记录管理接口")
public class ScanController {

    private static final Logger logger = LoggerFactory.getLogger(ScanController.class);

    @Autowired
    private ScanService scanService;

    @Value("${image.basePath}")
    private String basePath;

    @Operation(summary = "创建新的扫描记录")
    @PostMapping
    public Result<Object> create(@Valid @RequestBody ScanRequest request) {
        logger.info("创建扫描记录：BAH={}", request.getBah());
        
        Scan scan = new Scan(
            null,
            request.getBrxh(),
            request.getBah(),
            request.getFilename(),
            request.getBtype(),
            request.getPages(),
            request.getOpenerNo(),
            null, // uploadDate 由数据库自动处理
            request.getUploadFlag(),
            request.getFolder()
        );
        
        Scan created = scanService.create(scan);
        if (created != null) {
            logger.info("创建成功，ID={}", created.getId());
            return Result.success(null).data(created);
        } else {
            logger.error("创建失败");
            return Result.<Object>fail("创建扫描记录失败");
        }
    }

    @Operation(summary = "根据 ID 删除扫描记录")
    @DeleteMapping("/{id}")
    public Result<Object> deleteById(
            @PathVariable 
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        logger.info("删除扫描记录：ID={}", id);
        
        if (id == null) {
            return Result.fail("ID 不能为空");
        }
        
        boolean deleted = scanService.deleteById(id);
        if (deleted) {
            logger.info("删除成功：ID={}", id);
            return Result.success("删除成功");
        } else {
            logger.error("删除失败：ID={}", id);
            return Result.fail("删除失败，记录不存在");
        }
    }

    @Operation(summary = "更新扫描记录")
    @PutMapping("/{id}")
    public Result<Object> update(
            @PathVariable 
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id,
            @Valid @RequestBody ScanRequest request) {
        logger.info("更新扫描记录：ID={}", id);
        
        if (id == null) {
            return Result.fail("ID 不能为空");
        }
        
        Scan scan = new Scan(
            id,
            request.getBrxh(),
            request.getBah(),
            request.getFilename(),
            request.getBtype(),
            request.getPages(),
            request.getOpenerNo(),
            null,
            request.getUploadFlag(),
            request.getFolder()
        );
        
        Scan updated = scanService.update(scan);
        if (updated != null) {
            logger.info("更新成功：ID={}", id);
            return Result.success(null).data(updated);
        } else {
            logger.error("更新失败：ID={}", id);
            return Result.fail("更新失败，记录不存在");
        }
    }

    @Operation(summary = "获取所有扫描记录")
    @GetMapping
    public Result<Object> findAll() {
        logger.info("获取所有扫描记录");
        List<Scan> scans = scanService.findAll();
        return Result.success(null).data(scans);
    }

    @Operation(summary = "根据 ID 查询扫描记录")
    @GetMapping("/{id}")
    public Result<Object> findById(
            @PathVariable 
            @Parameter(description = "扫描记录 ID", example = "1")
            Integer id) {
        logger.info("查询扫描记录：ID={}", id);
        
        if (id == null) {
            return Result.fail("ID 不能为空");
        }
        
        Scan scan = scanService.findById(id);
        if (scan != null) {
            return Result.success(null).data(scan);
        } else {
            return Result.fail("未找到该扫描记录");
        }
    }

    @Operation(summary = "根据病案号查询扫描记录")
    @GetMapping("/bah/{bah}")
    public Result<Object> findByBah(
            @PathVariable 
            @Parameter(description = "病案号", example = "00789508")
            String bah) {
        logger.info("查询病案号下的扫描记录：BAH={}", bah);
        
        if (bah == null || bah.isEmpty()) {
            return Result.fail("病案号不能为空");
        }
        
        List<Scan> scans = scanService.findByBah(bah);
        return Result.success(null).data(scans);
    }

    @Operation(summary = "根据病人序号查询扫描记录")
    @GetMapping("/brxh/{brxh}")
    public Result<Object> findByBrxh(
            @PathVariable 
            @Parameter(description = "病人序号", example = "605746")
            String brxh) {
        logger.info("查询病人序号下的扫描记录：BRXH={}", brxh);
        
        if (brxh == null || brxh.isEmpty()) {
            return Result.fail("病人序号不能为空");
        }
        
        List<Scan> scans = scanService.findByBrxh(brxh);
        return Result.success(null).data(scans);
    }

    @Operation(summary = "分页查询所有扫描记录")
    @GetMapping("/page")
    public Result<Object> findAllWithPagination(
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "10") 
            @RequestParam(defaultValue = "10") int size) {
        logger.info("分页查询扫描记录：page={}, size={}", page, size);
        
        if (page < 1 || size < 1) {
            return Result.fail("页码和每页大小必须大于 0");
        }
        
        List<Scan> scans = scanService.findAllWithPagination(page, size);
        Map<String, Object> response = new HashMap<>();
        response.put("list", scans);
        response.put("total", scanService.findAll().size());
        response.put("page", page);
        response.put("size", size);
        
        return Result.success(null).data(response);
    }

    @Operation(summary = "根据条件动态查询扫描记录")
    @PostMapping("/condition")
    public Result<Object> findByCondition(@RequestBody ScanRequest request) {
        logger.info("根据条件查询扫描记录");
        
        List<Scan> scans = scanService.findByCondition(request);
        return Result.success(null).data(scans);
    }

    @PostMapping("/batch-download")
    public ResponseEntity<?> batchDownload(@RequestBody BatchDownloadRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Result.fail("ids cannot be empty"));
        }

        List<PathDO> items = scanService.getImagePathList(request.getIds());
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(Result.fail("no downloadable records found"));
        }

        try {
            byte[] zipBytes = buildBatchZip(items);
            ByteArrayResource resource = new ByteArrayResource(zipBytes);
            String fileName = "scan-batch-" + System.currentTimeMillis() + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipBytes.length)
                    .body(resource);
        } catch (IOException e) {
            logger.error("batch download failed", e);
            return ResponseEntity.internalServerError().body(Result.fail("batch download failed"));
        }
    }

    private byte[] buildBatchZip(List<PathDO> items) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            byte[] buffer = new byte[8192];
            for (PathDO item : items) {
                if (item == null) {
                    continue;
                }
                Path path = buildImagePath(item);
                if (path == null) {
                    continue;
                }
                File file = path.toFile();
                if (!file.exists() || !file.isFile()) {
                    logger.warn("skip missing file: {}", path);
                    continue;
                }

                String bah = item.getBAH() == null ? "unknown" : item.getBAH();
                String entryName = bah + "/" + file.getName();
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        }
    }

    private Path buildImagePath(PathDO item) {
        String folder = item.getFolder();
        String brxh = item.getBRXH();
        String bah = item.getBAH();
        String filename = item.getFilename();

        if (folder == null || folder.length() < 5 || brxh == null || bah == null || filename == null) {
            logger.warn("incomplete file params: folder={}, brxh={}, bah={}, filename={}", folder, brxh, bah, filename);
            return null;
        }

        String parentFolder = folder.substring(0, 5);
        String folderName = brxh + "-" + bah;
        return Paths.get(basePath, parentFolder, folder, folderName, filename);
    }
}
