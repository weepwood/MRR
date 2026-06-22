package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.StatisticsController;
import com.zjcxph.imgapi.dto.resp.BAHStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.DateStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Statistics;
import com.zjcxph.imgapi.service.StatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsController 统计控制器测试")
class StatisticsControllerTest {

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private StatisticsController controller;

    @Nested
    @DisplayName("getAllStatistics")
    class GetAllStatistics {

        @Test
        @DisplayName("page<1 抛异常")
        void invalidPage() {
            assertThatThrownBy(() -> controller.getAllStatistics(0, 10, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空白参数规范化为null，sortBy默认date")
        void blankParamsAndDefaults() {
            when(statisticsService.findWithConditionAndPagination(anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(statisticsService.getTotalCountByCondition(any(), any(), any(), any())).thenReturn(0L);

            Result<PageResult<Statistics>> r = controller.getAllStatistics(1, 20, "  ", "", "  \t", null, "", "  ");

            assertThat(r.getCode()).isEqualTo(200);
            // 验证空白 key/type/startDate/endDate/sortBy 均被 normalize 为 null，
            // sortOrder 默认为 "desc"
            verify(statisticsService).findWithConditionAndPagination(
                    eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), eq("date"), eq("desc"));
        }
    }

    @Nested
    @DisplayName("getStatisticsByBah")
    class GetStatisticsByBah {

        @Test
        @DisplayName("bah 为空返回 fail")
        void emptyBah() {
            Result<List<Statistics>> r = controller.getStatisticsByBah("");
            assertThat(r.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("合法查询返回 success")
        void validBah() {
            when(statisticsService.findByBah("00789508")).thenReturn(List.of());
            Result<List<Statistics>> r = controller.getStatisticsByBah("00789508");
            assertThat(r.getCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("getStatisticsByDate")
    class GetStatisticsByDate {

        @Test
        @DisplayName("date 为空返回 fail")
        void emptyDate() {
            Result<List<Statistics>> r = controller.getStatisticsByDate("");
            assertThat(r.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("透传类方法")
    class Delegation {

        @Test
        @DisplayName("getBAHStatistics — 透传 service")
        void getBAHStatistics() {
            when(statisticsService.getBAHStatistics()).thenReturn(List.of());
            Result<List<BAHStatisticsDTO>> r = controller.getBAHStatistics();
            assertThat(r.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("getDateStatistics — 透传 service")
        void getDateStatistics() {
            when(statisticsService.getDateStatistics()).thenReturn(List.of());
            Result<List<DateStatisticsDTO>> r = controller.getDateStatistics();
            assertThat(r.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("getTotalStatistics — 聚合三个统计")
        void getTotalStatistics() {
            when(statisticsService.getTotalStatistics()).thenReturn(Map.of());
            when(statisticsService.getUniqueBAHCount()).thenReturn(10L);
            when(statisticsService.getTypeStatistics()).thenReturn(List.of());

            Result<Map<String, Object>> r = controller.getTotalStatistics();

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).containsKeys("total", "uniqueBAHCount", "byType");
        }

        @Test
        @DisplayName("getDashboardData — 聚合面板数据")
        void getDashboardData() {
            when(statisticsService.getTotalStatistics()).thenReturn(Map.of());
            when(statisticsService.getUniqueBAHCount()).thenReturn(5L);
            when(statisticsService.getDateStatisticsByCondition(anyString(), anyString(), isNull()))
                    .thenReturn(List.of());
            when(statisticsService.getBAHStatistics()).thenReturn(List.of());

            Result<Map<String, Object>> r = controller.getDashboardData();

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).containsKeys("overview", "uniqueBAHCount", "recentTrend", "topBAH");
        }
    }
}
