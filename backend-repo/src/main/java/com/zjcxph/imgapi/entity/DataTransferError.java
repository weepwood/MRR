package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class DataTransferError {
    private Long id;
    private Long jobId;
    private Long fileId;
    private Long sourceRowNo;
    private String fieldName;
    private String errorCode;
    private String errorMessage;
    private String rawData;
    private OffsetDateTime createdAt;
}
