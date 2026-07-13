package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.PdfService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/img")
@Tag(name = "Archive PDF Controller", description = "影像档案 PDF 导出接口")
@RequirePermissions({"record:read"})
public class ArchivePdfController {

    private static final int MAX_EXPORT_IMAGES = 500;

    private final ImageProperties imageProperties;
    private final ScanService scanService;
    private final PdfService pdfService;

    public ArchivePdfController(ImageProperties imageProperties, ScanService scanService, PdfService pdfService) {
        this.imageProperties = imageProperties;
        this.scanService = scanService;
        this.pdfService = pdfService;
    }

    @Operation(summary = "将选中的影像按选择顺序导出为 PDF")
    @PostMapping(value = "/export-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> exportSelectedPdf(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请至少选择一张影像");
        }
        if (ids.size() > MAX_EXPORT_IMAGES) {
            throw new BusinessException(400, "单次最多导出 " + MAX_EXPORT_IMAGES + " 张影像");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(400, "影像 ID 不能为空");
        }

        List<Integer> uniqueIds = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Scan> scans = new ArrayList<>(uniqueIds.size());
        for (Integer id : uniqueIds) {
            Scan scan = scanService.findById(id);
            if (scan == null) {
                throw new BusinessException(404, "未找到影像记录：" + id);
            }
            scans.add(scan);
        }

        Scan first = scans.get(0);
        String archiveKey = archiveKey(first);
        Path basePath = Paths.get(imageProperties.getBasePath()).toAbsolutePath().normalize();
        List<String> imagePaths = new ArrayList<>(scans.size());

        for (Scan scan : scans) {
            if (!archiveKey.equals(archiveKey(scan))) {
                throw new BusinessException(400, "选中的影像不属于同一个档案袋");
            }
            Path imagePath = resolveImagePath(basePath, scan);
            if (imagePath == null || !Files.isRegularFile(imagePath)) {
                throw new BusinessException(404, "影像文件不存在：" + Objects.toString(scan.getFilename(), ""));
            }
            imagePaths.add(imagePath.toString());
        }

        try {
            Path outputPath = Files.createTempFile("archive-selected-", ".pdf");
            pdfService.createPdfFromImages(outputPath.toString(), imagePaths);

            File pdfFile = outputPath.toFile();
            pdfFile.deleteOnExit();
            String fileName = buildFileName(first);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfFile.length())
                    .body(new FileSystemResource(pdfFile));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, "PDF 导出失败：" + ex.getMessage());
        }
    }

    private Path resolveImagePath(Path basePath, Scan scan) {
        String folder = scan.getFolder();
        String filename = scan.getFilename();
        String brxh = scan.getBrxh();
        String bah = scan.getBah();
        if (folder == null || folder.length() < 5 || filename == null || brxh == null || bah == null) {
            return null;
        }

        Path resolvedPath = basePath
                .resolve(folder.substring(0, 5))
                .resolve(folder)
                .resolve(brxh + "-" + bah)
                .resolve(filename)
                .normalize();
        return resolvedPath.startsWith(basePath) ? resolvedPath : null;
    }

    private String archiveKey(Scan scan) {
        return MedicalRecordCodeUtils.normalizeOrEmpty(scan.getBah())
                + "|"
                + MedicalRecordCodeUtils.normalizeOrEmpty(scan.getSjh());
    }

    private String buildFileName(Scan scan) {
        String bah = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getBah());
        String sjh = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getSjh());
        String archiveCode = bah.isEmpty() ? "archive" : bah;
        if (!sjh.isEmpty()) {
            archiveCode += "-" + sjh;
        }
        return archiveCode + "-selected.pdf";
    }
}
