package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class DataTransferJob {
    private Long id;
    private String direction;
    private String entityType;
    private String status;
    private String importMode;
    private String sourceType;
    private Integer totalFiles;
    private Integer completedFiles;
    private Long totalRows;
    private Long processedRows;
    private Long validRows;
    private Long invalidRows;
    private Long insertedRows;
    private Long updatedRows;
    private Long skippedRows;
    private BigDecimal progress;
    private String currentStage;
    private Integer currentFileNo;
    private String options;
    private String errorMessage;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime heartbeatAt;
    private OffsetDateTime updatedAt;
    private Long failedFiles;
}
