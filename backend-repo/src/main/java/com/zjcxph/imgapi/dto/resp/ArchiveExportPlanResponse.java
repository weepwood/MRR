package com.zjcxph.imgapi.dto.resp;

import java.util.List;

public record ArchiveExportPlanResponse(
        String format,
        String executionMode,
        int selectedCount,
        int totalCount,
        int clientPdfMaxImages,
        boolean wholeArchive,
        long estimatedBytes,
        List<String> sourceSummary) {

    public ArchiveExportPlanResponse {
        sourceSummary = sourceSummary == null ? List.of() : List.copyOf(sourceSummary);
    }

    public ArchiveExportPlanResponse(
            String format,
            String executionMode,
            int selectedCount,
            int totalCount,
            int clientPdfMaxImages,
            boolean wholeArchive) {
        this(format, executionMode, selectedCount, totalCount,
                clientPdfMaxImages, wholeArchive, 0L, List.of());
    }
}
