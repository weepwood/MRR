package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class MigrationJob {
    private Long id;
    private String status;
    private Long totalCount;
    private Long processedCount;
    private Long failedCount;
    private BigDecimal rate;
    private String errorMessage;
    private String createdBy;
    private Long maxScanId;
    private Date startedAt;
    private Date completedAt;
    private Date createdAt;
    private Date updatedAt;
}
