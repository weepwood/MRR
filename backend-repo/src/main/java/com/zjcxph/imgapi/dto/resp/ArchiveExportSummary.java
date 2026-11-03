package com.zjcxph.imgapi.dto.resp;

/** Internal job preflight metadata; never contains image paths or source credentials. */
public record ArchiveExportSummary(int itemCount, long estimatedBytes) {
}
