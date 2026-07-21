package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResponseMetricAnalysisDTO {

    private Overview overview = new Overview();
    private List<TrendPoint> trend = new ArrayList<>();
    private List<SlowEndpoint> slowEndpoints = new ArrayList<>();

    @Data
    public static class Overview {
        private Long totalRequests = 0L;
        private Long frontendSampleCount = 0L;
        private Double successRate = 0.0;
        private Double avgServerDurationMs = 0.0;
        private Double avgClientDurationMs = 0.0;
        private Double p95ClientDurationMs = 0.0;
    }

    @Data
    public static class TrendPoint {
        private LocalDateTime bucket;
        private Long requestCount = 0L;
        private Long errorCount = 0L;
        private Double avgServerDurationMs = 0.0;
        private Double avgClientDurationMs = 0.0;
    }

    @Data
    public static class SlowEndpoint {
        private String routePattern;
        private String method;
        private Long requestCount = 0L;
        private Long errorCount = 0L;
        private Double avgServerDurationMs = 0.0;
        private Double avgClientDurationMs = 0.0;
        private Double p95ClientDurationMs = 0.0;
    }
}
