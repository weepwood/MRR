package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.FrontendResponseMetricRequest;
import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;

import java.util.List;

public interface ResponseMetricService {

    void saveFrontendMetrics(List<FrontendResponseMetricRequest> metrics);

    ResponseMetricAnalysisDTO getAnalysis(int days);
}
