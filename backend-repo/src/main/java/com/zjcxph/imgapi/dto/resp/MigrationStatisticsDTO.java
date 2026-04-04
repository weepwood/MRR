package com.zjcxph.imgapi.dto.resp;

public class MigrationStatisticsDTO {
    private long totalCount;
    private long migratedCount;
    private long pendingCount;
    private long failedCount;
    private double percentage;

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public long getMigratedCount() { return migratedCount; }
    public void setMigratedCount(long migratedCount) { this.migratedCount = migratedCount; }

    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }

    public long getFailedCount() { return failedCount; }
    public void setFailedCount(long failedCount) { this.failedCount = failedCount; }

    public double getPercentage() { return percentage; }
    public void setPercentage(double percentage) { this.percentage = percentage; }
}
