package com.zjcxph.imgapi.entity;

import java.math.BigDecimal;
import java.util.Date;

public class MigrationJob {

    private Long id;
    private String status;
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }

    public Long getProcessedCount() { return processedCount; }
    public void setProcessedCount(Long processedCount) { this.processedCount = processedCount; }

    public Long getFailedCount() { return failedCount; }
    public void setFailedCount(Long failedCount) { this.failedCount = failedCount; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }

    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
