package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.req.FrontendResponseMetricRequest;
import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;
import com.zjcxph.imgapi.entity.FrontendResponseMetric;
import com.zjcxph.imgapi.mapper.ResponseMetricMapper;
import com.zjcxph.imgapi.service.ResponseMetricService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ResponseMetricServiceImpl implements ResponseMetricService {

    static final int MAX_BATCH_SIZE = 200;
    private static final int SLOW_ENDPOINT_LIMIT = 10;
    private static final String INGESTION_ROUTE = "/api/v1/response-metrics/frontend/batch";

    private final ResponseMetricMapper responseMetricMapper;

    public ResponseMetricServiceImpl(ResponseMetricMapper responseMetricMapper) {
        this.responseMetricMapper = responseMetricMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFrontendMetrics(List<FrontendResponseMetricRequest> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            throw new IllegalArgumentException("前端响应指标批次不能为空");
        }
        if (metrics.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("前端响应指标单批不能超过 " + MAX_BATCH_SIZE + " 条");
        }

        List<FrontendResponseMetric> rows = metrics.stream()
                .filter(metric -> metric != null && !INGESTION_ROUTE.equals(metric.getRoutePattern()))
                .map(this::toEntity)
                .toList();
        if (!rows.isEmpty()) {
            responseMetricMapper.batchInsert(rows);
        }
    }

    @Override
    public ResponseMetricAnalysisDTO getAnalysis(int days) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days 必须在 1 到 90 之间");
        }

        ResponseMetricAnalysisDTO.Overview serverOverview = responseMetricMapper.getServerOverview(days);
        ResponseMetricAnalysisDTO.Overview frontendOverview = responseMetricMapper.getFrontendOverview(days);
        ResponseMetricAnalysisDTO result = new ResponseMetricAnalysisDTO();
        result.setOverview(mergeOverview(serverOverview, frontendOverview));
        result.setTrend(mergeTrend(
                responseMetricMapper.getServerTrend(days),
                responseMetricMapper.getFrontendTrend(days)
        ));
        List<ResponseMetricAnalysisDTO.SlowEndpoint> slowEndpoints =
                responseMetricMapper.getSlowEndpoints(days, SLOW_ENDPOINT_LIMIT);
        result.setSlowEndpoints(slowEndpoints == null ? List.of() : slowEndpoints);
        return result;
    }

    private FrontendResponseMetric toEntity(FrontendResponseMetricRequest request) {
        FrontendResponseMetric metric = new FrontendResponseMetric();
        metric.setRequestId(request.getRequestId());
        metric.setRoutePattern(request.getRoutePattern());
        metric.setMethod(request.getMethod() == null ? null : request.getMethod().toUpperCase());
        metric.setHttpStatus(request.getHttpStatus());
        metric.setBusinessCode(request.getBusinessCode());
        metric.setSuccess(request.getSuccess());
        metric.setClientDurationMs(request.getClientDurationMs());
        metric.setServerDurationMs(request.getServerDurationMs());
        metric.setOccurredAt(request.getOccurredAt());
        return metric;
    }

    private ResponseMetricAnalysisDTO.Overview mergeOverview(
            ResponseMetricAnalysisDTO.Overview server,
            ResponseMetricAnalysisDTO.Overview frontend
    ) {
        ResponseMetricAnalysisDTO.Overview overview = server == null
                ? new ResponseMetricAnalysisDTO.Overview()
                : server;
        if (frontend != null) {
            overview.setFrontendSampleCount(frontend.getFrontendSampleCount());
            overview.setAvgClientDurationMs(frontend.getAvgClientDurationMs());
            overview.setP95ClientDurationMs(frontend.getP95ClientDurationMs());
        }
        return overview;
    }

    private List<ResponseMetricAnalysisDTO.TrendPoint> mergeTrend(
            List<ResponseMetricAnalysisDTO.TrendPoint> serverPoints,
            List<ResponseMetricAnalysisDTO.TrendPoint> frontendPoints
    ) {
        Map<LocalDateTime, ResponseMetricAnalysisDTO.TrendPoint> byBucket = new TreeMap<>();
        if (serverPoints != null) {
            for (ResponseMetricAnalysisDTO.TrendPoint point : serverPoints) {
                byBucket.put(point.getBucket(), point);
            }
        }
        if (frontendPoints != null) {
            for (ResponseMetricAnalysisDTO.TrendPoint frontendPoint : frontendPoints) {
                ResponseMetricAnalysisDTO.TrendPoint target = byBucket.computeIfAbsent(
                        frontendPoint.getBucket(),
                        ignored -> newTrendPoint(frontendPoint.getBucket())
                );
                target.setAvgClientDurationMs(frontendPoint.getAvgClientDurationMs());
            }
        }
        return new ArrayList<>(byBucket.values());
    }

    private ResponseMetricAnalysisDTO.TrendPoint newTrendPoint(LocalDateTime bucket) {
        ResponseMetricAnalysisDTO.TrendPoint point = new ResponseMetricAnalysisDTO.TrendPoint();
        point.setBucket(bucket);
        return point;
    }
}
