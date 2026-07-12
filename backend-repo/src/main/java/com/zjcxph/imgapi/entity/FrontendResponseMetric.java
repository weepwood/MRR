package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FrontendResponseMetric {
    private String requestId;
    private String routePattern;
    private String method;
    private Integer httpStatus;
    private Integer businessCode;
    private Boolean success;
    private Long clientDurationMs;
    private Long serverDurationMs;
    private LocalDateTime occurredAt;
}
