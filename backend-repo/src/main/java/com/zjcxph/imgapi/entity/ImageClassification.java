package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class ImageClassification {
    private Integer scanId;
    private Long archiveId;
    private Integer predictedBtype;
    private BigDecimal confidence;
    private String classificationState;
    private String effectiveSource;
    private String modelVersion;
    private String ruleVersion;
    private String ocrTitle;
    private String evidence;
    private String imageChecksum;
    private Integer reviewedBtype;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String errorMessage;
    private OffsetDateTime classifiedAt;
    private OffsetDateTime updatedAt;
}
