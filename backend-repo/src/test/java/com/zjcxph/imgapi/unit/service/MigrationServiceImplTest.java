package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageMigrationLogMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.MigrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationServiceImpl 迁移服务测试")
class MigrationServiceImplTest {

    @Mock
    private OssService ossService;
    @Mock
    private ScanMapper scanMapper;
    @Mock
    private ImageMigrationLogMapper migrationLogMapper;
    @Mock
    private ImageProperties imageProperties;

    @InjectMocks
    private MigrationServiceImpl migrationService;

    @TempDir
    Path tempDir;

    /** 构造一个 folder=25.03.15, brxh=605746, bah=00789508, filename=test.jpg 的扫描记录。
     *  buildLocalPath 会用 basePath/25.03/25.03.15/605746-00789508/test.jpg。
     *  为避免真实文件系统耦合，测试通过设置 imageProperties.basePath 指向 tempDir 来控制路径。 */
    private Scan fullScan() {
        Scan s = new Scan();
        s.setId(1);
        s.setBrxh("605746");
        s.setBah("00789508");
        s.setFilename("test.jpg");
        s.setFolder("25.03.15");
        s.setUploadFlag(1);
        return s;
    }

    @Nested
    @DisplayName("uploadSingleScan")
    class UploadSingleScan {

        @Test
        @DisplayName("scan 不存在 — 返回 failed")
        void scanNotFound() {
            when(scanMapper.findById(99)).thenReturn(null);

            OssUploadResult r = migrationService.uploadSingleScan(99);

            assertThat(r.getStatus()).isEqualTo("failed");
            assertThat(r.getErrorMessage()).contains("扫描记录不存在");
        }

        @Test
        @DisplayName("已迁移（ossUrl 非空）— 返回 skipped")
        void alreadyMigrated() {
            Scan s = fullScan();
            s.setOssUrl("medical-records/old");
            when(scanMapper.findById(1)).thenReturn(s);

            OssUploadResult r = migrationService.uploadSingleScan(1);

            assertThat(r.getStatus()).isEqualTo("skipped");
            assertThat(r.getOssUrl()).isEqualTo("medical-records/old");
            verify(ossService, never()).uploadFile(any(), any());
        }

        @Test
        @DisplayName("字段不全（folder 不足 5 字符）— 返回 failed，无法构建路径")
        void cannotBuildPath() {
            Scan s = fullScan();
            s.setFolder("ab"); // 长度 < 5
            when(scanMapper.findById(1)).thenReturn(s);

            OssUploadResult r = migrationService.uploadSingleScan(1);

            assertThat(r.getStatus()).isEqualTo("failed");
            assertThat(r.getErrorMessage()).contains("无法构建本地路径");
        }

        @Test
        @DisplayName("本地文件不存在 — 记录 failed 日志并返回 failed")
        void localFileNotFound() {
            Scan s = fullScan();
            when(scanMapper.findById(1)).thenReturn(s);
            when(imageProperties.getBasePath()).thenReturn(tempDir.toString());

            OssUploadResult r = migrationService.uploadSingleScan(1);

            assertThat(r.getStatus()).isEqualTo("failed");
            assertThat(r.getErrorMessage()).contains("本地文件不存在");
            verify(migrationLogMapper).insert(argThat(log -> "failed".equals(log.getMigrationStatus())));
        }

        @Test
        @DisplayName("正常迁移 — OSS 不存在则上传，更新 DB，记录成功日志")
        void success_uploadNew() throws Exception {
            Scan s = fullScan();
            when(scanMapper.findById(1)).thenReturn(s);
            when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
            // 在预期路径下创建真实文件
            Path localFile = tempDir.resolve("25.03").resolve("25.03.15")
                    .resolve("605746-00789508").resolve("test.jpg");
            Files.createDirectories(localFile.getParent());
            Files.write(localFile, new byte[]{1, 2, 3});

            when(ossService.calculateMd5(anyString())).thenReturn("md5hex");
            when(ossService.getFileSize(anyString())).thenReturn(3L);
            when(ossService.doesObjectExist(anyString())).thenReturn(false);
            when(ossService.generatePresignedUrl(anyString())).thenReturn("https://signed-url");

            OssUploadResult r = migrationService.uploadSingleScan(1);

            assertThat(r.getStatus()).isEqualTo("success");
            assertThat(r.getChecksumMd5()).isEqualTo("md5hex");
            assertThat(r.getFileSize()).isEqualTo(3L);
            assertThat(r.getOssUrl()).isEqualTo("https://signed-url");
            verify(ossService).uploadFile(anyString(), anyString());
            verify(scanMapper).updateOssInfo(eq(1), anyString(), eq(3L), eq("md5hex"), eq("migrated"));
            verify(migrationLogMapper).insert(argThat(log -> "success".equals(log.getMigrationStatus())));
        }

        @Test
        @DisplayName("OSS 已存在同名对象 — 只更新 DB 不重复上传")
        void success_objectExists_noUpload() throws Exception {
            Scan s = fullScan();
            when(scanMapper.findById(1)).thenReturn(s);
            when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
            Path localFile = tempDir.resolve("25.03").resolve("25.03.15")
                    .resolve("605746-00789508").resolve("test.jpg");
            Files.createDirectories(localFile.getParent());
            Files.write(localFile, new byte[]{1, 2, 3});

            when(ossService.calculateMd5(anyString())).thenReturn("md5hex");
            when(ossService.getFileSize(anyString())).thenReturn(3L);
            when(ossService.doesObjectExist(anyString())).thenReturn(true);
            when(ossService.generatePresignedUrl(anyString())).thenReturn("https://signed-url");

            migrationService.uploadSingleScan(1);

            // 已存在则不调 uploadFile
            verify(ossService, never()).uploadFile(any(), any());
            verify(scanMapper).updateOssInfo(eq(1), anyString(), eq(3L), eq("md5hex"), eq("migrated"));
        }

        @Test
        @DisplayName("上传过程抛异常 — 记录 failed 日志并返回 failed")
        void uploadThrows_returnsFailed() throws Exception {
            Scan s = fullScan();
            when(scanMapper.findById(1)).thenReturn(s);
            when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
            Path localFile = tempDir.resolve("25.03").resolve("25.03.15")
                    .resolve("605746-00789508").resolve("test.jpg");
            Files.createDirectories(localFile.getParent());
            Files.write(localFile, new byte[]{1, 2, 3});

            when(ossService.calculateMd5(anyString())).thenReturn("md5hex");
            when(ossService.getFileSize(anyString())).thenReturn(3L);
            when(ossService.doesObjectExist(anyString())).thenReturn(false);
            when(ossService.uploadFile(anyString(), anyString()))
                    .thenThrow(new RuntimeException("OSS down"));

            OssUploadResult r = migrationService.uploadSingleScan(1);

            assertThat(r.getStatus()).isEqualTo("failed");
            assertThat(r.getErrorMessage()).contains("OSS down");
            verify(migrationLogMapper).insert(argThat(log -> "failed".equals(log.getMigrationStatus())));
        }
    }

    @Nested
    @DisplayName("uploadByBah")
    class UploadByBah {

        @Test
        @DisplayName("无扫描记录 — 返回单条 failed")
        void noScans() {
            when(scanMapper.findByBah("EMPTY")).thenReturn(java.util.Collections.emptyList());

            var results = migrationService.uploadByBah("EMPTY");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getStatus()).isEqualTo("failed");
        }

        @Test
        @DisplayName("跳过 uploadFlag=0 的记录")
        void skipsDeletedRecords() {
            Scan deleted = fullScan();
            deleted.setId(2);
            deleted.setUploadFlag(0);
            when(scanMapper.findByBah("00789508")).thenReturn(java.util.List.of(deleted));

            var results = migrationService.uploadByBah("00789508");

            // 被跳过，结果为空
            assertThat(results).isEmpty();
            verify(scanMapper, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("getStatistics")
    class GetStatistics {

        @Test
        @DisplayName("统计计数与百分比计算正确")
        void statisticsValues() {
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("total", 100L);
            statsMap.put("migrated", 60L);
            statsMap.put("verified", 10L);
            when(scanMapper.countMigrationStats()).thenReturn(statsMap);
            when(migrationLogMapper.countWithFilter("failed")).thenReturn(5L);

            MigrationStatisticsDTO dto = migrationService.getStatistics();

            assertThat(dto.getTotalCount()).isEqualTo(100);
            assertThat(dto.getMigratedCount()).isEqualTo(70); // migrated + verified
            assertThat(dto.getPendingCount()).isEqualTo(30);  // total - migrated - verified
            assertThat(dto.getFailedCount()).isEqualTo(5);
            assertThat(dto.getPercentage()).isEqualTo(70.0);
        }

        @Test
        @DisplayName("total=0 时百分比为 0（不除零）")
        void statistics_zeroTotal() {
            Map<String, Object> statsMap = new HashMap<>();
            statsMap.put("total", 0L);
            statsMap.put("migrated", 0L);
            statsMap.put("verified", 0L);
            when(scanMapper.countMigrationStats()).thenReturn(statsMap);
            when(migrationLogMapper.countWithFilter("failed")).thenReturn(0L);

            MigrationStatisticsDTO dto = migrationService.getStatistics();

            assertThat(dto.getPercentage()).isEqualTo(0.0);
            assertThat(dto.getTotalCount()).isZero();
        }
    }

    @Nested
    @DisplayName("分页与透传方法")
    class Delegation {

        @Test
        @DisplayName("getPendingMigrations — 透传 limit")
        void getPendingMigrations() {
            when(scanMapper.findPendingMigration(10)).thenReturn(java.util.Collections.emptyList());
            migrationService.getPendingMigrations(10);
            verify(scanMapper).findPendingMigration(10);
        }

        @Test
        @DisplayName("getMigrationLogs — 合法分页透传 offset")
        void getMigrationLogs_valid() {
            when(migrationLogMapper.findWithPagination(eq("failed"), anyInt(), anyInt()))
                    .thenReturn(java.util.Collections.emptyList());
            migrationService.getMigrationLogs("failed", 2, 10);
            verify(migrationLogMapper).findWithPagination("failed", 10, 10);
        }

        @Test
        @DisplayName("getMigrationLogs — page<1 抛异常")
        void getMigrationLogs_invalidPage() {
            assertThatThrownBy(() -> migrationService.getMigrationLogs("failed", 0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("countMigrationLogs — 透传 status")
        void countMigrationLogs() {
            when(migrationLogMapper.countWithFilter("success")).thenReturn(8L);
            assertThat(migrationService.countMigrationLogs("success")).isEqualTo(8L);
        }
    }
}
