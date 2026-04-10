package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

@Data
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

}
