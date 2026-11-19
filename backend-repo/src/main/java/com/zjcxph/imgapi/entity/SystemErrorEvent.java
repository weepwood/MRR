package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemErrorEvent {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String projectionEventId;
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
    /** 最近 24 小时发生次数（由 recent_seen_at 数组统计，非持久化列，最多统计最近 50 次）。 */
    private Long recent24hCount;
    private String status;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
}
