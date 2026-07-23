package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemErrorEvent {
    private Long id;
    private String errorId;
    private String fingerprint;
    private String level;
    private String module;
    private String loggerName;
    private String exceptionType;
    private String messageSummary;
    private String stackTrace;
    private String requestId;
    private String threadName;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private Long occurrenceCount;
    private String status;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
}
