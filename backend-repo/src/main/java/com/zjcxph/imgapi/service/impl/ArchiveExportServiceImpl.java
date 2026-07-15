package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
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
    public void writeBatchZip(BatchZipExport export, OutputStream outputStream) throws IOException {
        if (export == null) {
            throw new IllegalArgumentException("导出计划不能为空");
        }
        if (outputStream == null) {
            throw new IllegalArgumentException("输出流不能为空");
        }

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
