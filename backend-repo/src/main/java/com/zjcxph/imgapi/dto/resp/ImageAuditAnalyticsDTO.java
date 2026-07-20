package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.util.List;

@Data
public class ImageAuditAnalyticsDTO {
    private long totalAccesses;
    private long uniqueUsers;
    private long uniqueTargets;
    private long abnormalAccesses;
    private double averageDurationMs;
    private List<ImageAuditTrendDTO> trend = List.of();
    private List<ImageAuditCountDTO> actionDistribution = List.of();
    private List<ImageAuditCountDTO> topUsers = List.of();
    private List<ImageAuditCountDTO> topTargets = List.of();
}
