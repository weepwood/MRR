package com.zjcxph.imgapi.service.impl;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ArchiveExportServiceImpl implements ArchiveExportService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExportServiceImpl.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int PDF_PAGE_MARGIN = 18;
    private static final int MAX_PDF_IMAGE_BYTES = 100 * 1024 * 1024;

    private final ScanService scanService;
    private final ImageStorage imageStorage;

    public ArchiveExportServiceImpl(ScanService scanService, ImageStorage imageStorage) {
        this.scanService = scanService;
        this.imageStorage = imageStorage;
    }

    @Override
    public BatchZipExport prepareBatch(List<String> scanIds) {
        if (scanIds == null || scanIds.isEmpty()) {
            return new BatchZipExport(List.of());
        }
        return new BatchZipExport(scanService.getImagePathList(scanIds));
    }

    @Override
    public BatchZipExport prepareArchive(String bah, String sjh) {
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        List<Scan> scans = scanService.getImageListByCode(
                normalizedBah,
                MedicalRecordCodeUtils.toSearchTerm(normalizedBah),
                normalizedSjh,
                MedicalRecordCodeUtils.toSearchTerm(normalizedSjh)
        );
        List<PathDO> items = scans.stream()
                .map(scan -> new PathDO(
                        scan.getFolder(),
                        scan.getFilename(),
                        scan.getBrxh(),
                        scan.getBah()
                ))
                .toList();
        return new BatchZipExport(items);
    }

    @Override
    public void writeBatchZip(BatchZipExport export, OutputStream outputStream) throws IOException {
        validateExportArguments(export, outputStream);

        Set<String> usedEntryNames = new HashSet<>();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (PathDO item : export.items()) {
                String entryName = uniqueEntryName(item, usedEntryNames);
                InputStream opened;
                try {
                    opened = imageStorage.open(item);
                } catch (IOException exception) {
                    logger.warn("跳过无法打开的导出影像: entry={}, reason={}", entryName, exception.getMessage());
                    continue;
                }

                try (InputStream input = opened) {
                    zip.putNextEntry(new ZipEntry(entryName));
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                    zip.closeEntry();
                }
            }
            zip.finish();
        }
    }

    @Override
    public void writeBatchPdf(BatchZipExport export, OutputStream outputStream) throws IOException {
        validateExportArguments(export, outputStream);

        try (PdfWriter writer = new PdfWriter(outputStream);
             PdfDocument pdf = new PdfDocument(writer)) {
            int written = 0;
            for (PathDO item : export.items()) {
                byte[] imageBytes;
                try (InputStream input = imageStorage.open(item)) {
                    imageBytes = input.readNBytes(MAX_PDF_IMAGE_BYTES + 1);
                }
                if (imageBytes.length > MAX_PDF_IMAGE_BYTES) {
                    throw new IOException("单张影像超过 PDF 导出大小上限");
                }

                ImageData image = ImageDataFactory.create(imageBytes);
                PageSize pageSize = image.getWidth() > image.getHeight()
                        ? PageSize.A4.rotate()
                        : PageSize.A4;
                PdfPage page = pdf.addNewPage(pageSize);
                Rectangle target = new Rectangle(
                        PDF_PAGE_MARGIN,
                        PDF_PAGE_MARGIN,
                        pageSize.getWidth() - PDF_PAGE_MARGIN * 2f,
                        pageSize.getHeight() - PDF_PAGE_MARGIN * 2f
                );
                new PdfCanvas(page).addImageFittedIntoRectangle(image, target, false);
                written++;
            }

            if (written == 0) {
                throw new IOException("没有可写入 PDF 的影像");
            }
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            logger.error("病案 PDF 生成失败", exception);
            throw new IOException("病案 PDF 生成失败", exception);
        }
    }

    private void validateExportArguments(BatchZipExport export, OutputStream outputStream) {
        if (export == null) {
            throw new IllegalArgumentException("导出计划不能为空");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("输出流不能为空");
        }
    }

    private String uniqueEntryName(PathDO item, Set<String> usedEntryNames) {
        String archiveCode = sanitizeSegment(item.getBah(), "unknown");
        String filename = sanitizeSegment(item.getFilename(), "image");
        String candidate = archiveCode + "/" + filename;
        if (usedEntryNames.add(candidate)) {
            return candidate;
        }

        int extensionIndex = filename.lastIndexOf('.');
        String stem = extensionIndex <= 0 ? filename : filename.substring(0, extensionIndex);
        String extension = extensionIndex <= 0 ? "" : filename.substring(extensionIndex);
        int suffix = 2;
        do {
            candidate = archiveCode + "/" + stem + "-" + suffix + extension;
            suffix++;
        } while (!usedEntryNames.add(candidate));
        return candidate;
    }

    private String sanitizeSegment(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = value.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replace("..", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}
