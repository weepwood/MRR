package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ResponseMetricController;
import com.zjcxph.imgapi.dto.req.FrontendResponseMetricRequest;
import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;
import com.zjcxph.imgapi.service.ResponseMetricService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResponseMetricControllerTest {

    @Mock
    private ResponseMetricService service;

    @InjectMocks
    private ResponseMetricController controller;

    @Test
    void recordsFrontendBatch() {
        List<FrontendResponseMetricRequest> metrics = List.of(new FrontendResponseMetricRequest());

        Result<Void> result = controller.saveFrontendMetrics(metrics);

        assertThat(result.getCode()).isEqualTo(200);
        verify(service).saveFrontendMetrics(metrics);
    }

    @Test
    void returnsStronglyTypedAnalysis() {
        ResponseMetricAnalysisDTO analysis = new ResponseMetricAnalysisDTO();
        when(service.getAnalysis(7)).thenReturn(analysis);

        Result<ResponseMetricAnalysisDTO> result = controller.getAnalysis(7);

        assertThat(result.getData()).isSameAs(analysis);
    }
}
