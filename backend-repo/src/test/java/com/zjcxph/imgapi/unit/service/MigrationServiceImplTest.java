package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageMigrationLogMapper;
import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.MigrationService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.MigrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationServiceImpl OSS 迁移管理测试")
class MigrationServiceImplTest {

    @Mock
    private OssService ossService;
    @Mock
    private ScanMapper scanMapper;
    @Mock
    private ImageMigrationLogMapper migrationLogMapper;
    @Mock
    private MigrationJobMapper migrationJobMapper;
    @Mock
    private ImageProperties imageProperties;
    @Mock
    private Executor taskAsyncExecutor;
    @Mock
    private MigrationService self;

    @InjectMocks
    private MigrationServiceImpl migrationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(migrationService, "self", self);
        lenient().when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
        lenient().when(ossService.generatePresignedUrl(anyString())).thenReturn("https://signed.example/image");
    }

    @Test
    @DisplayName("已有活动任务时复用任务，不重复启动线程")
    void reusesExistingActiveJob() {
        MigrationJob active = new MigrationJob();
        active.setId(8L);
        active.setStatus("running");
        when(migrationJobMapper.findLatestActive()).thenReturn(active);

        MigrationJob result = migrationService.createMigrationJob(new MigrationJobRequest());

        assertThat(result.getId()).isEqualTo(8L);
        assertThat(result.getReused()).isTrue();
        verify(migrationJobMapper, never()).insert(any());
        verify(taskAsyncExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("全量迁移必须输入固定确认短语")
    void fullMigrationRequiresConfirmation() {
        MigrationJobRequest request = new MigrationJobRequest();
        request.setMode("full");

        assertThatThrownBy(() -> migrationService.createMigrationJob(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("确认全量迁移");
    }

    @Test
    @DisplayName("试迁移固定快照与数量并按已加载 Scan 执行")
    void createsPilotJobWithSnapshotAndLimit() {
        MigrationJobRequest request = new MigrationJobRequest();
        request.setMode("pilot");
        request.setLimit(1);

        Scan pending = legacyScan(1, "00789508", "605746", null);
        AtomicReference<MigrationJob> persisted = new AtomicReference<>();
        when(scanMapper.findMaxPendingMigrationId(isNull())).thenReturn(10);
        when(scanMapper.countEligibleMigrations(10, null)).thenReturn(5L);
        when(migrationJobMapper.insert(any(MigrationJob.class))).thenAnswer(invocation -> {
            MigrationJob job = invocation.getArgument(0);
            job.setId(7L);
            persisted.set(job);
            return 1;
        });
        when(migrationJobMapper.findById(7L)).thenAnswer(invocation -> persisted.get());
        when(migrationJobMapper.isCancelRequested(7L)).thenReturn(false);
        when(scanMapper.findPendingMigrationAfterId(0, 10, null, 1)).thenReturn(List.of(pending));
        when(self.uploadLoadedScan(same(pending))).thenReturn(success(1));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(taskAsyncExecutor).execute(any(Runnable.class));

        MigrationJob result = migrationService.createMigrationJob(request);

        assertThat(result.getMode()).isEqualTo("pilot");
        assertThat(result.getMaxScanId()).isEqualTo(10);
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo("completed");
        assertThat(result.getProcessedCount()).isEqualTo(1);
        verify(self).uploadLoadedScan(same(pending));
    }

    @Test
    @DisplayName("本地文件缺失会标记永久失败，不继续停留在待迁移集合")
    void missingFileIsMarkedFailed() {
        Scan scan = legacyScan(1, "00789508", "605746", null);
        when(scanMapper.findById(1)).thenReturn(scan);

        OssUploadResult result = migrationService.uploadSingleScan(1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getErrorMessage()).contains("不存在或不可读");
        verify(scanMapper).markMigrationStarted(1);
        verify(scanMapper).markMigrationFailed(1, "SOURCE_FILE_MISSING");
        verify(ossService, never()).uploadFile(anyString(), anyString());
    }

    @Test
    @DisplayName("OSS 临时异常在最大次数前进入等待重试")
    void temporaryOssFailureWaitsForRetry() throws Exception {
        Scan scan = legacyScan(1, "00789508", "605746", null);
        when(scanMapper.findById(1)).thenReturn(scan);
        createLegacyFile(scan, new byte[]{1, 2, 3});
        when(ossService.calculateMd5(anyString())).thenReturn("md5");
        when(ossService.getFileSize(anyString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);
        when(ossService.uploadFile(anyString(), anyString()))
                .thenThrow(new RuntimeException("connection timeout"));

        OssUploadResult result = migrationService.uploadSingleScan(1);

        assertThat(result.getStatus()).isEqualTo("retry_wait");
        verify(scanMapper).markMigrationRetryWait(eq(1), eq("OSS_TIMEOUT"), any());
        verify(scanMapper, never()).markMigrationFailed(eq(1), anyString());
    }

    @Test
    @DisplayName("高位病案号使用上架号构建本地目录和 OSS Key")
    void highBahUsesSjhAsDirectoryKey() throws Exception {
        Scan scan = legacyScan(2, "10000001", "605746", "87654321");
        when(scanMapper.findById(2)).thenReturn(scan);
        Path file = tempDir.resolve("25.03").resolve("25.03.15")
                .resolve("87654321-10000001").resolve("test.jpg");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{4, 5, 6});
        when(ossService.calculateMd5(anyString())).thenReturn("md5");
        when(ossService.getFileSize(anyString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);

        OssUploadResult result = migrationService.uploadSingleScan(2);

        assertThat(result.getStatus()).isEqualTo("success");
        verify(ossService).uploadFile(file.toString(),
                "medical-records/25.03/25.03.15/87654321-10000001/test.jpg");
    }

    @Test
    @DisplayName("迁移前检查抽样展示可读与缺失文件")
    void readinessSamplesSourceFiles() throws Exception {
        Scan readable = legacyScan(1, "00789508", "605746", null);
        Scan missing = legacyScan(2, "00789509", "605747", null);
        createLegacyFile(readable, new byte[]{1});
        when(scanMapper.countMigrationStats()).thenReturn(stats(2, 0, 0, 0, 0, 0));
        when(scanMapper.findPendingMigration(2)).thenReturn(List.of(readable, missing));

        MigrationReadinessDTO readiness = migrationService.getReadiness(2);

        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.getSampleReadableCount()).isEqualTo(1);
        assertThat(readiness.getSampleMissingCount()).isEqualTo(1);
        assertThat(readiness.getRecommendedMode()).isEqualTo("pilot");
        assertThat(readiness.getWarnings()).anyMatch(item -> item.contains("缺失"));
    }

    private Scan legacyScan(int id, String bah, String brxh, String sjh) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setBah(bah);
        scan.setBrxh(brxh);
        scan.setSjh(sjh);
        scan.setFolder("25.03.15");
        scan.setFilename("test.jpg");
        scan.setUploadFlag(1);
        return scan;
    }

    private void createLegacyFile(Scan scan, byte[] content) throws Exception {
        String directoryKey = scan.getBah().compareTo("10000000") >= 0 ? scan.getSjh() : scan.getBrxh();
        Path file = tempDir.resolve("25.03").resolve("25.03.15")
                .resolve(directoryKey + "-" + scan.getBah()).resolve("test.jpg");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private OssUploadResult success(int id) {
        OssUploadResult result = new OssUploadResult();
        result.setScanId(id);
        result.setStatus("success");
        return result;
    }

    private Map<String, Object> stats(long total, long migrated, long verified,
                                      long failed, long retryWait, long migrating) {
        Map<String, Object> values = new HashMap<>();
        values.put("total", total);
        values.put("migrated", migrated);
        values.put("verified", verified);
        values.put("failed", failed);
        values.put("retry_wait", retryWait);
        values.put("migrating", migrating);
        return values;
    }
}
