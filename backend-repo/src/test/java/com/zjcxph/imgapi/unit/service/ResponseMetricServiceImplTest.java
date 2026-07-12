package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.FrontendResponseMetricRequest;
import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;
import com.zjcxph.imgapi.entity.FrontendResponseMetric;
import com.zjcxph.imgapi.mapper.ResponseMetricMapper;
import com.zjcxph.imgapi.service.impl.ResponseMetricServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseMetricServiceImplTest {

    @Mock
    private ResponseMetricMapper mapper;

    @InjectMocks
    private ResponseMetricServiceImpl service;

    @Test
    void batchPersistsOnlyBusinessApiMetrics() {
        FrontendResponseMetricRequest businessMetric = metric("req-1", "/api/v1/scans/{id}");
        businessMetric.setSuccess(false);
        FrontendResponseMetricRequest ingestionMetric = metric("req-2", "/api/v1/response-metrics/frontend/batch");
        Instant beforeSave = Instant.now();

        service.saveFrontendMetrics(List.of(businessMetric, ingestionMetric));
        Instant afterSave = Instant.now();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FrontendResponseMetric>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsert(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().getSuccess()).isTrue();
        assertThat(captor.getValue().getFirst().getOccurredAt())
                .isBetween(Timestamp.from(beforeSave), Timestamp.from(afterSave));
    }

    @Test
    void batchContainingOnlyIngestionMetricDoesNotWrite() {
        service.saveFrontendMetrics(List.of(metric("req-2", "/api/v1/response-metrics/frontend/batch")));

        verify(mapper, never()).batchInsert(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void analysisMergesServerTrendWithFrontendDurations() {
        ResponseMetricAnalysisDTO.Overview server = new ResponseMetricAnalysisDTO.Overview();
        server.setTotalRequests(20L);
        server.setSuccessRate(95.0);
        server.setAvgServerDurationMs(30.0);
        ResponseMetricAnalysisDTO.Overview frontend = new ResponseMetricAnalysisDTO.Overview();
        frontend.setFrontendSampleCount(10L);
        frontend.setAvgClientDurationMs(45.0);
        frontend.setP95ClientDurationMs(90.0);

        LocalDateTime bucket = LocalDateTime.of(2026, 7, 13, 10, 0);
        ResponseMetricAnalysisDTO.TrendPoint serverPoint = new ResponseMetricAnalysisDTO.TrendPoint();
        serverPoint.setBucket(bucket);
        serverPoint.setRequestCount(20L);
        serverPoint.setErrorCount(1L);
        serverPoint.setAvgServerDurationMs(30.0);
        ResponseMetricAnalysisDTO.TrendPoint frontendPoint = new ResponseMetricAnalysisDTO.TrendPoint();
        frontendPoint.setBucket(bucket);
        frontendPoint.setAvgClientDurationMs(45.0);

        when(mapper.getServerOverview(7)).thenReturn(server);
        when(mapper.getFrontendOverview(7)).thenReturn(frontend);
        when(mapper.getServerTrend(7)).thenReturn(List.of(serverPoint));
        when(mapper.getFrontendTrend(7)).thenReturn(List.of(frontendPoint));
        when(mapper.getSlowEndpoints(7, 10)).thenReturn(List.of());

        ResponseMetricAnalysisDTO result = service.getAnalysis(7);

        assertThat(result.getOverview().getTotalRequests()).isEqualTo(20L);
        assertThat(result.getOverview().getFrontendSampleCount()).isEqualTo(10L);
        assertThat(result.getOverview().getAvgClientDurationMs()).isEqualTo(45.0);
        assertThat(result.getTrend()).singleElement()
                .extracting(ResponseMetricAnalysisDTO.TrendPoint::getAvgClientDurationMs)
                .isEqualTo(45.0);
    }

    @Test
    void analysisRejectsDaysOutsideOneToNinety() {
        assertThatThrownBy(() -> service.getAnalysis(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getAnalysis(91)).isInstanceOf(IllegalArgumentException.class);
    }

    private FrontendResponseMetricRequest metric(String requestId, String routePattern) {
        FrontendResponseMetricRequest request = new FrontendResponseMetricRequest();
        request.setRequestId(requestId);
        request.setRoutePattern(routePattern);
        request.setMethod("GET");
        request.setHttpStatus(200);
        request.setBusinessCode(200);
        request.setSuccess(true);
        request.setClientDurationMs(42L);
        request.setServerDurationMs(30L);
        request.setOccurredAt(Instant.parse("2026-07-13T02:00:00Z"));
        return request;
    }
}
