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
import com.zjcxph.imgapi.exception.BusinessException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ArchiveExportServiceImpl implements ArchiveExportService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveExportServiceImpl.class);
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int PDF_PAGE_MARGIN = 18;
    private static final int MAX_PDF_IMAGE_BYTES = 100 * 1024 * 1024;

    private static final Comparator<Scan> ARCHIVE_PAGE_ORDER = Comparator
            .comparing(Scan::getPages, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(Scan::getId, Comparator.nullsLast(Integer::compareTo));

    private static final Comparator<Scan> BATCH_ORDER = Comparator
            .comparing(Scan::getBah, Comparator.nullsLast(String::compareTo))
            .thenComparing(Scan::getSjh, Comparator.nullsLast(String::compareTo))
            .thenComparing(ARCHIVE_PAGE_ORDER);

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
        List<Scan> scans = loadActiveScans(scanIds);
        scans.sort(BATCH_ORDER);
        return toExport(scans);
    }

    @Override
    public BatchZipExport prepareSelectedArchive(List<String> scanIds) {
        if (scanIds == null || scanIds.isEmpty()) {
            return new BatchZipExport(List.of());
        }
        List<Scan> scans = loadActiveScans(scanIds);
        validateSameArchive(scans);
        scans.sort(ARCHIVE_PAGE_ORDER);
        return toExport(scans);
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
        return toExport(scans == null ? List.of() : scans);
    }

    @Override
    public void writeBatchZip(BatchZipExport export, OutputStream outputStream) throws IOException {
        validateExportArguments(export, outputStream);

        Set<String> usedEntryNames = new HashSet<>();
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (PathDO item : export.items()) {
                String entryName = uniqueEntryName(item, usedEntryNames);
                try (InputStream input = imageStorage.open(item)) {
                    zip.putNextEntry(new ZipEntry(entryName));
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                    zip.closeEntry();
                } catch (IOException exception) {
                    logger.error("ZIP 导出影像失败: entry={}", entryName, exception);
                    throw new IOException("无法读取导出影像: " + entryName, exception);
                }
            }
            zip.finish();
        }
    }

    @Override
    public void writeBatchPdf(BatchZipExport export, OutputStream outputStream) throws IOException {
        validateExportArguments(export, outputStream);

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(outputStream))) {
            int written = 0;
            for (PathDO item : export.items()) {
                String imageName = sanitizeSegment(item.getFilename(), "image");
                byte[] imageBytes;
                try (InputStream input = imageStorage.open(item)) {
                    imageBytes = input.readNBytes(MAX_PDF_IMAGE_BYTES + 1);
                } catch (IOException exception) {
                    throw new IOException("无法读取 PDF 影像: " + imageName, exception);
                }
                if (imageBytes.length > MAX_PDF_IMAGE_BYTES) {
                    throw new IOException("单张影像超过 PDF 导出大小上限: " + imageName);
                }

                ImageData image;
                try {
                    image = ImageDataFactory.create(imageBytes);
                } catch (RuntimeException exception) {
                    throw new IOException("无法识别 PDF 影像: " + imageName, exception);
                }
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
                PdfCanvas canvas = new PdfCanvas(page);
                canvas.addImageFittedIntoRectangle(image, target, false);
                canvas.release();
                page.flush();
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

    private List<Scan> loadActiveScans(List<String> rawIds) {
        LinkedHashSet<Integer> uniqueIds = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            String value = rawId == null ? "" : rawId.trim();
            if (value.isEmpty()) {
                throw new BusinessException(400, "影像 ID 不能为空");
            }
            final int id;
            try {
                id = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new BusinessException(400, "影像 ID 格式不正确");
            }
            if (id <= 0) {
                throw new BusinessException(400, "影像 ID 必须大于 0");
            }
            if (!uniqueIds.add(id)) {
                throw new BusinessException(400, "导出请求包含重复影像 ID");
            }
        }

        List<Integer> ids = List.copyOf(uniqueIds);
        List<Scan> queried = scanService.findActiveByIds(ids);
        List<Scan> scans = queried == null
                ? new ArrayList<>()
                : new ArrayList<>(queried.stream().filter(Objects::nonNull).toList());
        Set<Integer> returnedIds = new HashSet<>();
        for (Scan scan : scans) {
            if (scan.getId() != null) {
                returnedIds.add(scan.getId());
            }
        }
        if (returnedIds.size() != ids.size() || !returnedIds.containsAll(ids)) {
            throw new BusinessException(404, "部分影像不存在或已失效，请刷新病案后重试");
        }
        return scans;
    }

    private void validateSameArchive(List<Scan> scans) {
        if (scans.isEmpty()) {
            throw new BusinessException(404, "未找到可导出的影像");
        }
        Scan first = scans.get(0);
        String expectedKey = logicalArchiveKey(first);
        Long expectedArchiveId = first.getArchiveId();

        for (Scan scan : scans) {
            if (!expectedKey.equals(logicalArchiveKey(scan))) {
                throw new BusinessException(400, "选中的影像必须属于同一份病案");
            }
            if (expectedArchiveId != null && scan.getArchiveId() != null
                    && !expectedArchiveId.equals(scan.getArchiveId())) {
                throw new BusinessException(400, "选中的影像必须属于同一份病案");
            }
        }
    }

    private String logicalArchiveKey(Scan scan) {
        String bah = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getBah());
        String sjh = MedicalRecordCodeUtils.normalizeOrEmpty(scan.getSjh());
        if (bah.isEmpty() || MedicalRecordCodeUtils.requiresSjhForBah(bah)) {
            if (sjh.isEmpty()) {
                throw new BusinessException(400, "影像缺少可验证的病案标识");
            }
            return "SJH:" + sjh;
        }
        return "BAH:" + bah;
    }

    private BatchZipExport toExport(List<Scan> scans) {
        List<PathDO> items = scans.stream()
                .filter(Objects::nonNull)
                .map(scan -> new PathDO(
                        scan.getFolder(),
                        scan.getFilename(),
                        scan.getBrxh(),
                        scan.getBah()
                ))
                .toList();
        return new BatchZipExport(items);
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
