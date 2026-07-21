package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.ResponseMetricAnalysisDTO;
import com.zjcxph.imgapi.entity.FrontendResponseMetric;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResponseMetricMapper {

    int batchInsert(@Param("metrics") List<FrontendResponseMetric> metrics);

    ResponseMetricAnalysisDTO.Overview getServerOverview(@Param("days") int days);

    ResponseMetricAnalysisDTO.Overview getFrontendOverview(@Param("days") int days);

    List<ResponseMetricAnalysisDTO.TrendPoint> getServerTrend(@Param("days") int days);

    List<ResponseMetricAnalysisDTO.TrendPoint> getFrontendTrend(@Param("days") int days);

    List<ResponseMetricAnalysisDTO.SlowEndpoint> getSlowEndpoints(
            @Param("days") int days,
            @Param("limit") int limit
    );
}
