package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class ClassificationJob {
    private Long id;
    private Long archiveId;
    private String scopeType;
    private String status;
    private Long totalCount;
    private Long processedCount;
    private Long suggestedCount;
    private Long noMatchCount;
    private Long failedCount;
    private Integer cursorScanId;
    private String modelVersion;
    private String errorMessage;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;
}
