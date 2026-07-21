package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArchiveExportJob {
    private String id;
    private Long ownerUserId;
    private String ownerUsername;
    private String format;
    private String scope;
    private String status;
    private String bah;
    private String sjh;
    private String scanIds;
    private Integer plannedCount;
    private Integer processedCount;
    private Integer failedCount;
    private Long estimatedBytes;
    private Long outputBytes;
    private String sourceSummary;
    private String fileName;
    private String filePath;
    private String sha256;
    private Boolean cancelRequested;
    private String errorMessage;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
