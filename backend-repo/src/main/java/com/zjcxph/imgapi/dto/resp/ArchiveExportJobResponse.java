package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.entity.ArchiveExportJob;

import java.time.LocalDateTime;

public record ArchiveExportJobResponse(
        String id,
        String format,
        String scope,
        String status,
        String bah,
        String sjh,
        int plannedCount,
        int processedCount,
        int failedCount,
        long estimatedBytes,
        long outputBytes,
        String sourceSummary,
        String fileName,
        String sha256,
        boolean cancelRequested,
        String errorMessage,
        int downloadCount,
        LocalDateTime lastDownloadedAt,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {

    public static ArchiveExportJobResponse from(ArchiveExportJob job) {
        return new ArchiveExportJobResponse(
                job.getId(),
                job.getFormat(),
                job.getScope(),
                job.getStatus(),
                job.getBah(),
                job.getSjh(),
                value(job.getPlannedCount()),
                value(job.getProcessedCount()),
                value(job.getFailedCount()),
                value(job.getEstimatedBytes()),
                value(job.getOutputBytes()),
                job.getSourceSummary(),
                job.getFileName(),
                job.getSha256(),
                Boolean.TRUE.equals(job.getCancelRequested()),
                safeError(job.getErrorMessage()),
                value(job.getDownloadCount()),
                job.getLastDownloadedAt(),
                job.getExpiresAt(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }

    private static String safeError(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value) {
            case "异步导出队列已满，请稍后重试",
                 "病案导出失败，请使用任务编号联系管理员",
                 com.zjcxph.imgapi.utils.ExportSizeLimitOutputStream.LIMIT_MESSAGE -> value;
            default -> "病案导出失败，请使用任务编号联系管理员";
        };
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
