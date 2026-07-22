package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.OssController;
import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.req.MigrationRetryRequest;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OssController OSS 迁移管理测试")
class OssControllerTest {

    @Mock
    private MigrationService migrationService;
    @Mock
    private OssService ossService;

    @InjectMocks
    private OssController controller;

    @Test
    @DisplayName("手工上传限制为 500 条")
    void limitsManualUploadSize() {
        OssUploadRequest request = new OssUploadRequest();
        request.setScanIds(java.util.stream.IntStream.rangeClosed(1, 501).boxed().toList());

        Result<Map<String, Object>> result = controller.upload(request);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("500");
    }

    @Test
    @DisplayName("手工上传聚合成功、跳过与失败数量")
    void aggregatesManualUploadResult() {
        when(migrationService.uploadSingleScan(anyInt())).thenReturn(
                new OssUploadResult(1, "success", null),
                new OssUploadResult(2, "skipped", null),
                new OssUploadResult(3, "retry_wait", "timeout")
        );
        OssUploadRequest request = new OssUploadRequest();
        request.setScanIds(List.of(1, 2, 3));

        Result<Map<String, Object>> result = controller.upload(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("success", 2L);
        assertThat(result.getData()).containsEntry("failed", 1L);
    }

    @Test
    @DisplayName("迁移前检查限制抽样数量并透传结果")
    void returnsReadiness() {
        MigrationReadinessDTO readiness = new MigrationReadinessDTO();
        readiness.setReady(true);
        when(migrationService.getReadiness(5000)).thenReturn(readiness);

        Result<MigrationReadinessDTO> result = controller.getMigrationReadiness(5000);

        assertThat(result.getData().isReady()).isTrue();
        verify(migrationService).getReadiness(5000);
    }

    @Test
    @DisplayName("创建任务时返回已存在的活动任务")
    void returnsReusedJob() {
        MigrationJob job = new MigrationJob();
        job.setId(9L);
        job.setReused(true);
        when(migrationService.createMigrationJob(org.mockito.ArgumentMatchers.any(MigrationJobRequest.class)))
                .thenReturn(job);

        Result<MigrationJob> result = controller.createMigrationJob(new MigrationJobRequest());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).contains("已有迁移任务");
        assertThat(result.getData().getId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("安全取消不存在的任务返回失败")
    void cancelMissingJob() {
        when(migrationService.cancelMigrationJob(99L)).thenReturn(null);

        Result<MigrationJob> result = controller.cancelMigrationJob(99L);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("重置失败记录返回更新数量")
    void retriesFailedScans() {
        MigrationRetryRequest request = new MigrationRetryRequest();
        request.setScanIds(List.of(1, 2));
        when(migrationService.retryFailedScans(List.of(1, 2))).thenReturn(2);

        Result<Map<String, Object>> result = controller.retryFailedScans(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("updated", 2);
    }
}
