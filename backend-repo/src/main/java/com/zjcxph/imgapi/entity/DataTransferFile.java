package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DataTransferFile {
    private Long id;
    private Long jobId;
    private Integer sequenceNo;
    private String originalFilename;
    private String storedPath;
    private String downloadName;
    private Long fileSize;
    private String sha256;
    private String status;
    private Long totalRows;
    private Long processedRows;
    private Long validRows;
    private Long invalidRows;
    private Long insertedRows;
    private Long updatedRows;
    private Long skippedRows;
    private Long firstRecordId;
    private Long lastRecordId;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;
}
