package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.DataQualityController;
import com.zjcxph.imgapi.service.DataQualityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityControllerTest {

    @Mock
    private DataQualityService dataQualityService;

    @InjectMocks
    private DataQualityController controller;

    @Test
    void summaryReturnsServicePayload() {
        Map<String, Object> summary = Map.of("running", false, "enabled", true);
        when(dataQualityService.getSummary()).thenReturn(summary);

        Result<Map<String, Object>> result = controller.summary();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("获取数据质量摘要成功");
        assertThat(result.getData()).isSameAs(summary);
        verify(dataQualityService).getSummary();
    }

    @Test
    void issuesForwardsRequestedLimit() {
        List<Map<String, Object>> issues = List.of(Map.of("check_code", "SCAN_CODE_BLANK"));
        when(dataQualityService.getIssues(25)).thenReturn(issues);

        Result<List<Map<String, Object>>> result = controller.issues(25);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("获取数据质量异常成功");
        assertThat(result.getData()).isSameAs(issues);
        verify(dataQualityService).getIssues(25);
    }

    @Test
    void issueReturnsSelectedIssue() {
        Map<String, Object> issue = Map.of("id", 9L, "checkCode", "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED");
        when(dataQualityService.getIssue(9L)).thenReturn(issue);

        Result<Map<String, Object>> result = controller.issue(9L);

        assertThat(result.getData()).isSameAs(issue);
        verify(dataQualityService).getIssue(9L);
    }

    @Test
    void repairPreviewIsReadOnly() {
        Map<String, Object> preview = Map.of("readOnly", true, "canApply", false);
        when(dataQualityService.previewRepair(9L)).thenReturn(preview);

        Result<Map<String, Object>> result = controller.repairPreview(9L);

        assertThat(result.getData()).isSameAs(preview);
        verify(dataQualityService).previewRepair(9L);
    }

    @Test
    void runUsesManualTriggerAndReturnsSummary() {
        Map<String, Object> summary = Map.of("latestRun", Map.of("status", "SUCCESS"));
        when(dataQualityService.runChecks("manual")).thenReturn(summary);

        Result<Map<String, Object>> result = controller.run();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("数据质量检查完成");
        assertThat(result.getData()).isSameAs(summary);
        verify(dataQualityService).runChecks("manual");
    }

    @Test
    void runConvertsConcurrentExecutionFailureToBusinessResult() {
        when(dataQualityService.runChecks("manual"))
                .thenThrow(new IllegalStateException("数据质量检查正在运行，请稍后重试"));

        Result<Map<String, Object>> result = controller.run();

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("数据质量检查正在运行，请稍后重试");
        assertThat(result.getData()).isNull();
    }
}
