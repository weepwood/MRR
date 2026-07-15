package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.PathDO;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * 病案影像导出应用服务。
 */
public interface ArchiveExportService {

    BatchZipExport prepareBatch(List<String> scanIds);

    void writeBatchZip(BatchZipExport export, OutputStream outputStream) throws IOException;

    record BatchZipExport(List<PathDO> items) {
        public BatchZipExport {
            items = items == null
                    ? List.of()
                    : items.stream().filter(Objects::nonNull).toList();
        }

        public int itemCount() {
            return items.size();
        }
    }
}
