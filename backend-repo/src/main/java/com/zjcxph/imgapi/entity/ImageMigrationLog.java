package com.zjcxph.imgapi.entity;

import java.util.Date;

public class ImageMigrationLog {
    private Long id;
    private Integer scanId;
    private String localPath;
    private String ossUrl;
    private String migrationStatus;
    private String errorMessage;
    private Long fileSize;
    private String checksumMd5;
    private Date migratedAt;
    private Date verifiedAt;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getScanId() { return scanId; }
    public void setScanId(Integer scanId) { this.scanId = scanId; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public String getOssUrl() { return ossUrl; }
    public void setOssUrl(String ossUrl) { this.ossUrl = ossUrl; }
    public String getMigrationStatus() { return migrationStatus; }
    public void setMigrationStatus(String migrationStatus) { this.migrationStatus = migrationStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getChecksumMd5() { return checksumMd5; }
    public void setChecksumMd5(String checksumMd5) { this.checksumMd5 = checksumMd5; }
    public Date getMigratedAt() { return migratedAt; }
    public void setMigratedAt(Date migratedAt) { this.migratedAt = migratedAt; }
    public Date getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Date verifiedAt) { this.verifiedAt = verifiedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
