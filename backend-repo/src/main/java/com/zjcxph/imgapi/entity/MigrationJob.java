package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class MigrationJob {
    private Long id;
    private String status;
    private String mode;
    private String scopeValue;
    private Long requestedCount;
    private Integer maxScanId;
    private Boolean cancelRequested;
    private Long totalCount;
    private Long processedCount;
    private Long failedCount;
    private BigDecimal rate;
    private String errorMessage;
    private String createdBy;
    private Date startedAt;
    private Date completedAt;
    private Date createdAt;
    private Date updatedAt;

    /** 仅用于接口返回，表示本次请求复用了已存在的活动任务。 */
    private Boolean reused;
}
