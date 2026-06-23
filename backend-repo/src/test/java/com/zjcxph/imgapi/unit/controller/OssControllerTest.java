package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.OssController;
import com.zjcxph.imgapi.dto.req.OssUploadRequest;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OssController OSS 控制器测试")
class OssControllerTest {

    @Mock
    private MigrationService migrationService;
    @Mock
    private OssService ossService;

    @InjectMocks
    private OssController controller;

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("请求为 null 返回 fail")
        void nullRequest() {
            Result<Map<String, Object>> r = controller.upload(null);
            assertThat(r.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("scanIds 为空返回 fail")
        void emptyScanIds() {
            OssUploadRequest req = new OssUploadRequest();
            req.setScanIds(List.of());
            Result<Map<String, Object>> r = controller.upload(req);
            assertThat(r.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("正常上传聚合成功/失败计数")
        void success() {
            OssUploadResult ok = new OssUploadResult(1, "success", null);
            OssUploadResult fail = new OssUploadResult(2, "failed", "err");
            when(migrationService.uploadSingleScan(anyInt())).thenReturn(ok, fail);

            OssUploadRequest req = new OssUploadRequest();
            req.setScanIds(List.of(1, 2));
            Result<Map<String, Object>> r = controller.upload(req);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).containsEntry("total", 2);
            assertThat(r.getData()).containsEntry("success", 1L);
            assertThat(r.getData()).containsEntry("failed", 1L);
        }
    }

    @Nested
    @DisplayName("uploadByBah")
    class UploadByBah {

        @Test
        @DisplayName("bah 为空返回 fail")
        void emptyBah() {
            Result<Map<String, Object>> r = controller.uploadByBah("");
            assertThat(r.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("正常返回聚合结果")
        void validBah() {
            when(migrationService.uploadByBah("00789508"))
                    .thenReturn(List.of(new OssUploadResult(1, "success", null)));

            Result<Map<String, Object>> r = controller.uploadByBah("00789508");

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).containsEntry("bah", "00789508");
        }
    }

    @Nested
    @DisplayName("迁移统计与列表")
    class Migration {

        @Test
        @DisplayName("getMigrationStatistics — 透传")
        void getMigrationStatistics() {
            MigrationStatisticsDTO dto = new MigrationStatisticsDTO();
            dto.setTotalCount(100);
            when(migrationService.getStatistics()).thenReturn(dto);

            Result<MigrationStatisticsDTO> r = controller.getMigrationStatistics();

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData().getTotalCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("getPendingMigrations — 透传")
        void getPendingMigrations() {
            when(migrationService.getPendingMigrations(50)).thenReturn(List.of());
            Result<Map<String, Object>> r = controller.getPendingMigrations(50, null);
            assertThat(r.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("getMigrationLogs — 成功记录预签名 URL")
        void getMigrationLogs_withPresignedUrl() {
            ImageMigrationLog log = new ImageMigrationLog();
            log.setId(1L);
            log.setMigrationStatus("success");
            log.setOssUrl("oss-key-123");
            when(migrationService.getMigrationLogs(eq("success"), anyInt(), anyInt()))
                    .thenReturn(List.of(log));
            when(migrationService.countMigrationLogs("success")).thenReturn(1L);
            when(ossService.generatePresignedUrl("oss-key-123")).thenReturn("https://signed");

            Result<PageResult<ImageMigrationLog>> r = controller.getMigrationLogs("success", 1, 20);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData().getList().get(0).getOssUrl()).isEqualTo("https://signed");
        }
    }

    @Nested
    @DisplayName("deleteOssFile")
    class DeleteOssFile {

        @Test
        @DisplayName("正常删除返回 success")
        void success() {
            Result<String> r = controller.deleteOssFile("key-123");
            assertThat(r.getCode()).isEqualTo(200);
            verify(ossService).deleteObject("key-123");
        }

        @Test
        @DisplayName("OSS 异常返回 fail")
        void ossException() {
            doThrow(new RuntimeException("OSS error")).when(ossService).deleteObject("error-key");
            Result<String> r = controller.deleteOssFile("error-key");
            assertThat(r.getCode()).isEqualTo(400);
        }
    }
}
