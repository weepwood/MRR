package com.zjcxph.imgapi.dto.resp;

public class OssUploadResult {
    private Integer scanId;
    private String ossUrl;
    private Long fileSize;
    private String checksumMd5;
    private String status;
    private String errorMessage;

    public OssUploadResult() {}

    public OssUploadResult(Integer scanId, String status, String errorMessage) {
        this.scanId = scanId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public Integer getScanId() { return scanId; }
    public void setScanId(Integer scanId) { this.scanId = scanId; }

    public String getOssUrl() { return ossUrl; }
    public void setOssUrl(String ossUrl) { this.ossUrl = ossUrl; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getChecksumMd5() { return checksumMd5; }
    public void setChecksumMd5(String checksumMd5) { this.checksumMd5 = checksumMd5; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
