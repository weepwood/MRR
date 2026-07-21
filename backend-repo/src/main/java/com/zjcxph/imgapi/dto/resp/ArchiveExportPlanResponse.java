package com.zjcxph.imgapi.dto.resp;

public record ArchiveExportPlanResponse(
        String format,
        String executionMode,
        int selectedCount,
        int totalCount,
        int clientPdfMaxImages,
        boolean wholeArchive) {
}
