package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.controller.LogController;
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
            assertThat(r.getMessage()).contains("not found");
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
