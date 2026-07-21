package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.controller.LogController;
import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditCountDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditTrendDTO;
import com.zjcxph.imgapi.dto.resp.LogRetentionCleanupResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.scheduler.LogRetentionCleaner;
import com.zjcxph.imgapi.service.LogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogController 日志控制器测试")
class LogControllerTest {

    @Mock
    private LogService logService;
    @Mock
    private LogRetentionCleaner logRetentionCleaner;
    @Mock
    private LogMapper logMapper;
    @Mock
    private LogRetentionProperties logRetentionProperties;

    @InjectMocks
    private LogController controller;

    @Nested
    @DisplayName("getLogById")
    class GetLogById {

        @Test
        @DisplayName("命中返回 Log")
        void found() {
            Log log = new Log();
            log.setId(1L);
            when(logService.getLogById(1L)).thenReturn(log);

            Result<Log> r = controller.getLogById(1L);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(log);
        }

        @Test
        @DisplayName("未命中返回 fail")
        void notFound() {
            when(logService.getLogById(999L)).thenReturn(null);

            Result<Log> r = controller.getLogById(999L);

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).contains("日志不存在");
        }
    }

    @Nested
    @DisplayName("getAllLogs")
    class GetAllLogs {

        @Test
        @DisplayName("page<1 抛异常")
        void invalidPage() {
            try {
                controller.getAllLogs(0, 10);
            } catch (IllegalArgumentException e) {
                assertThat(e.getMessage()).contains("页码");
            }
        }

        @Test
        @DisplayName("正常查询返回分页结果")
        void validPaging() {
            when(logService.getAllLogs(1, 10)).thenReturn(List.of());
            when(logService.getTotalLogCount()).thenReturn(5);

            Result<PageResult<Log>> r = controller.getAllLogs(1, 10);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData().getTotal()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("searchLogs")
    class SearchLogs {

        @Test
        @DisplayName("size超过200截断为200")
        void sizeCapped() {
            when(logService.searchLogs(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(logService.countSearchLogs(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(5);

            Result<PageResult<Log>> r = controller.searchLogs(1, 500, null, null, null, null, null, null, null, null);

            verify(logService).searchLogs(any(), any(), any(), any(), any(), any(), any(), any(), eq(1), eq(200));
            assertThat(r.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("空白字符串参数规范化为 null")
        void blankParamsNormalized() {
            when(logService.searchLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            when(logService.countSearchLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(0);

            Result<PageResult<Log>> r = controller.searchLogs(1, 20, "   ", "", null, "\t", null, null, null, null);

            assertThat(r.getCode()).isEqualTo(200);
            verify(logService).searchLogs(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20));
        }

        @Test
        @DisplayName("游标查询按 size+1 获取并返回下一页游标")
        void cursorPagingReturnsNextCursor() {
            LocalDateTime cursorAccessTime = LocalDateTime.of(2026, 7, 13, 10, 20, 30);
            Log first = log(30L, LocalDateTime.of(2026, 7, 13, 10, 19, 0));
            Log second = log(29L, LocalDateTime.of(2026, 7, 13, 10, 18, 0));
            Log lookahead = log(28L, LocalDateTime.of(2026, 7, 13, 10, 17, 0));
            when(logService.searchLogs(any(), any(), any(), any(), any(), any(), any(), any(),
                    eq(1), eq(3), eq(cursorAccessTime), eq(31L)))
                    .thenReturn(List.of(first, second, lookahead));
            when(logService.countSearchLogs(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(100);

            Result<PageResult<Log>> result = controller.searchLogs(
                    1, 2, null, null, null, null, null, null, null, null,
                    cursorAccessTime, 31L);

            assertThat(result.getData().getList()).containsExactly(first, second);
            assertThat(result.getData().getNextCursorAccessTime()).isEqualTo("2026-07-13T10:18:00");
            assertThat(result.getData().getNextCursorId()).isEqualTo(29L);
        }

        @Test
        @DisplayName("游标时间与 id 必须成对传入")
        void cursorPartsMustBePaired() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.searchLogs(
                            1, 20, null, null, null, null, null, null, null, null,
                            LocalDateTime.of(2026, 7, 13, 10, 20), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("游标");
            verifyNoInteractions(logService);
        }

        private Log log(long id, LocalDateTime accessTime) {
            Log log = new Log();
            log.setId(id);
            log.setAccessTime(Date.from(accessTime.atZone(ZoneId.systemDefault()).toInstant()));
            return log;
        }
    }

    @Nested
    @DisplayName("图片访问审计")
    class ImageAudit {

        @Test
        @DisplayName("analytics 规范化筛选参数并返回强类型汇总")
        void analyticsNormalizesFilters() {
            ImageAuditAnalyticsDTO analytics = new ImageAuditAnalyticsDTO();
            analytics.setTotalAccesses(12L);
            analytics.setUniqueUsers(3L);
            analytics.setUniqueTargets(8L);
            analytics.setAbnormalAccesses(2L);
            analytics.setAverageDurationMs(18.5D);
            analytics.setTrend(List.of(new ImageAuditTrendDTO(LocalDate.of(2026, 7, 13), 12L)));
            analytics.setActionDistribution(List.of(new ImageAuditCountDTO("VIEW_IMAGE", 12L)));
            analytics.setTopUsers(List.of(new ImageAuditCountDTO("doctor", 7L)));

            when(logService.getImageAuditAnalytics(
                    eq("scan"), eq("doctor"), isNull(), eq("VIEW_IMAGE"), eq("4"),
                    eq("2026-07-01 00:00:00"), eq("2026-07-13 23:59:59")))
                    .thenReturn(analytics);

            Result<ImageAuditAnalyticsDTO> result = controller.getImageAuditAnalytics(
                    " scan ", " doctor ", "   ", " VIEW_IMAGE ", " 4 ",
                    LocalDateTime.of(2026, 7, 1, 0, 0),
                    LocalDateTime.of(2026, 7, 13, 23, 59, 59));

            assertThat(result.getData()).isSameAs(analytics);
            assertThat(result.getData().getTrend()).hasSize(1);
            verify(logService).getImageAuditAnalytics(
                    "scan", "doctor", null, "VIEW_IMAGE", "4",
                    "2026-07-01 00:00:00", "2026-07-13 23:59:59");
        }

        @Test
        @DisplayName("列表装饰不得覆盖已落库的审计字段")
        void existingAuditFieldsAreAuthoritative() {
            Log log = new Log();
            log.setRequestUri("/api/v1/img/image/00789508/605746/24.04.30/0072.jpg");
            log.setAuditAction("DOWNLOAD");
            log.setAuditTarget("stored-target");
            log.setAuditDescription("stored-description");
            when(logService.searchImageAuditLogs(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(log));
            when(logService.countImageAuditLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

            Result<PageResult<Log>> result = controller.searchImageAuditLogs(
                    1, 20, null, null, null, null, null, null, null);

            Log returned = result.getData().getList().getFirst();
            assertThat(returned.getAuditAction()).isEqualTo("DOWNLOAD");
            assertThat(returned.getAuditTarget()).isEqualTo("stored-target");
            assertThat(returned.getAuditDescription()).isEqualTo("stored-description");
        }

        @Test
        @DisplayName("列表装饰只补缺失字段且与拦截器图片规则一致")
        void missingAuditFieldsAreDecorated() {
            Log log = new Log();
            log.setRequestUri("/api/v1/img/123456");
            log.setAuditDescription("stored-description");
            when(logService.searchImageAuditLogs(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(log));
            when(logService.countImageAuditLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

            Result<PageResult<Log>> result = controller.searchImageAuditLogs(
                    1, 20, null, null, null, null, null, null, null);

            Log returned = result.getData().getList().getFirst();
            assertThat(returned.getAuditAction()).isEqualTo("LIST");
            assertThat(returned.getAuditTarget()).isEqualTo("123456");
            assertThat(returned.getAuditDescription()).isEqualTo("stored-description");
        }
    }

    @Nested
    @DisplayName("cleanupRetentionLogs")
    class CleanupRetentionLogs {

        @Test
        @DisplayName("无 cutoff 时调用 cleanupNow 无参版本")
        void noCutoff() {
            LogRetentionCleanupResult result = new LogRetentionCleanupResult();
            result.setSuccess(true);
            result.setMessage("cleaned 100 rows");
            when(logRetentionCleaner.cleanupNow()).thenReturn(result);

            Result<LogRetentionCleanupResult> r = controller.cleanupRetentionLogs(null);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).isSameAs(result);
        }

        @Test
        @DisplayName("有 cutoff 时调用带参版本")
        void withCutoff() {
            var cutoff = java.time.LocalDateTime.now().minusDays(365);
            LogRetentionCleanupResult result = new LogRetentionCleanupResult();
            result.setSuccess(true);
            result.setMessage("done");
            when(logRetentionCleaner.cleanupNow(cutoff)).thenReturn(result);

            Result<LogRetentionCleanupResult> r = controller.cleanupRetentionLogs(cutoff);

            assertThat(r.getCode()).isEqualTo(200);
            verify(logRetentionCleaner).cleanupNow(cutoff);
        }
    }
}
