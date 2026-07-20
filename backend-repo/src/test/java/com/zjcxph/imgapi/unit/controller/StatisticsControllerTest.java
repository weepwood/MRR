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
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
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
            assertThatThrownBy(() -> controller.getAllStatistics(0, 10, null, null, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空白参数规范化为null，sortBy默认date")
        void blankParamsAndDefaults() {
            when(statisticsService.findWithConditionAndPagination(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(statisticsService.getTotalCountByCondition(any(), any(), any(), any(), any(), any())).thenReturn(0L);

            Result<PageResult<Statistics>> r = controller.getAllStatistics(1, 20, "  ", "", "  \t", null, null, null, "", "  ");

            assertThat(r.getCode()).isEqualTo(200);
            verify(statisticsService).findWithConditionAndPagination(
                    eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("date"), eq("desc"));
        }

        @Test
        @DisplayName("传递非空 bah 和 sjh")
        void nonEmptyBahAndSjh() {
            when(statisticsService.findWithConditionAndPagination(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());
            when(statisticsService.getTotalCountByCondition(any(), any(), any(), any(), any(), any())).thenReturn(0L);

            controller.getAllStatistics(1, 20, null, "0078", "SJH001", null, null, null, null, null);

            verify(statisticsService).findWithConditionAndPagination(
                    eq(1), eq(20), isNull(), eq("0078"), eq("SJH001"), isNull(), isNull(), isNull(), eq("date"), eq("desc"));
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
    @DisplayName("exportCsv")
    class ExportCsv {

        @Test
        @DisplayName("使用专用导出查询并写入带 BOM 的 CSV")
        void exportCsvUsesDedicatedQuery() throws Exception {
            Statistics item = new Statistics();
            item.setBah("00001234");
            item.setPatientName("张三");
            item.setDate("2026-01-02");
            item.setPages(3);
            when(statisticsService.findWithConditionForExport(
                    eq(100000), eq("张三"), isNull(), isNull(), eq("01-病案首页"),
                    eq("2026-01-01"), eq("2026-12-31")))
                    .thenReturn(List.of(item));
            MockHttpServletResponse response = new MockHttpServletResponse();

            controller.exportCsv(" 张三 ", null, null, "01-病案首页",
                    "2026-01-01", "2026-12-31", response);

            verify(statisticsService).findWithConditionForExport(
                    100000, "张三", null, null, "01-病案首页", "2026-01-01", "2026-12-31");
            assertThat(response.getContentType()).startsWith("text/csv");
            assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFF病案号,")
                    .contains("00001234,张三")
                    .contains("2026-01-02")
                    .contains(",3,");
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
