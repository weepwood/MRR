package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.PathDO;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 病案影像导出应用服务。
 */
public interface ArchiveExportService {

    BatchZipExport prepareBatch(List<String> scanIds);

    BatchZipExport prepareSelectedArchive(List<String> scanIds);

    BatchZipExport prepareArchive(String bah, String sjh);

    default void writeBatchZip(BatchZipExport export, OutputStream outputStream) throws IOException {
        writeBatchZip(export, outputStream, ExportProgress.NONE);
    }

    void writeBatchZip(BatchZipExport export, OutputStream outputStream, ExportProgress progress) throws IOException;

    default void writeBatchPdf(BatchZipExport export, OutputStream outputStream) throws IOException {
        writeBatchPdf(export, outputStream, ExportProgress.NONE);
    }

    void writeBatchPdf(BatchZipExport export, OutputStream outputStream, ExportProgress progress) throws IOException;

    interface ExportProgress {
        ExportProgress NONE = new ExportProgress() {
        };

        default boolean isCancelled() {
            return false;
        }

        default void onItemCompleted(int completed, int total, PathDO item) {
        }
    }

    record BatchZipExport(List<PathDO> items) {
        public BatchZipExport {
            items = items == null
                    ? List.of()
                    : items.stream().filter(Objects::nonNull).toList();
        }

        public int itemCount() {
            return items.size();
        }

        /**
         * 仅当所有图片大小都已知时返回精确估算；任一图片大小缺失时返回 0，
         * 由任务策略使用配置化的每图保守值估算，避免只累计部分已知大小而低估。
         */
        public long estimatedBytes() {
            long total = 0;
            for (PathDO item : items) {
                if (item.getFileSize() == null || item.getFileSize() <= 0) {
                    return 0;
                }
                long value = item.getFileSize();
                if (Long.MAX_VALUE - total < value) {
                    return Long.MAX_VALUE;
                }
                total += value;
            }
            return total;
        }

        public Set<String> sourceSummary() {
            LinkedHashSet<String> sources = new LinkedHashSet<>();
            for (PathDO item : items) {
                String source = item.getSourceType();
                if (source == null || source.isBlank() || "AUTO".equalsIgnoreCase(source)) {
                    source = item.getOssUrl() == null || item.getOssUrl().isBlank() ? "LOCAL" : "OSS";
                }
                sources.add(source.toUpperCase());
            }
            return Collections.unmodifiableSet(sources);
        }
    }
}
