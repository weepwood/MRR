package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.MigrationJobRequest;
import com.zjcxph.imgapi.dto.resp.MigrationReadinessDTO;
import com.zjcxph.imgapi.dto.resp.MigrationStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.OssUploadResult;
import com.zjcxph.imgapi.entity.MigrationJob;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ImageMigrationLogMapper;
import com.zjcxph.imgapi.mapper.MigrationJobMapper;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.MigrationServiceImpl;
import com.zjcxph.imgapi.service.impl.MigrationSourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private MigrationSourceResolver sourceResolver;
    @Mock
    private Executor migrationTaskExecutor;

    @InjectMocks
    private MigrationServiceImpl migrationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        lenient().when(ossService.generatePresignedUrl(anyString()))
                .thenReturn("https://signed.example/image");
        lenient().when(sourceResolver.describe(any())).thenAnswer(invocation -> {
            Scan scan = invocation.getArgument(0);
            return "AUTO:scan:" + (scan == null ? "unknown" : scan.getId());
        });
        lenient().when(scanMapper.markMigrationStarted(anyInt())).thenReturn(1);
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
        verify(migrationTaskExecutor, never()).execute(any());
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
    @DisplayName("试迁移不执行全量精确计数，并固定快照上界")
    void createsPilotJobWithoutFullCount() throws Exception {
        MigrationJobRequest request = new MigrationJobRequest();
        request.setMode("pilot");
        request.setLimit(1);

        Scan pending = legacyScan(1, "00789508", "605746", null);
        Path file = createFile("pilot.jpg", new byte[]{1, 2, 3});
        AtomicReference<MigrationJob> persisted = new AtomicReference<>();

        when(scanMapper.findPendingMigration(20)).thenReturn(List.of(pending));
        when(sourceResolver.canRead(pending)).thenReturn(true);
        when(scanMapper.findMaxPendingMigrationId(isNull())).thenReturn(10);
        when(migrationJobMapper.insert(any(MigrationJob.class))).thenAnswer(invocation -> {
            MigrationJob job = invocation.getArgument(0);
            job.setId(7L);
            persisted.set(job);
            return 1;
        });
        when(migrationJobMapper.findById(7L)).thenAnswer(invocation -> persisted.get());
        when(migrationJobMapper.isCancelRequested(7L)).thenReturn(false);
        when(migrationJobMapper.markRunning(eq(7L), any(Date.class))).thenReturn(1);
        when(scanMapper.findPendingMigrationAfterId(0, 10, null, 1)).thenReturn(List.of(pending));
        when(sourceResolver.resolve(pending)).thenReturn(resolved(file));
        when(ossService.calculateMd5(file.toString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(ossService.getFileSize(file.toString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);
        when(ossService.uploadFile(anyString(), anyString(), anyString()))
                .thenReturn("medical-records/1234/12345678-00789508/pilot.jpg");
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(migrationTaskExecutor).execute(any(Runnable.class));

        MigrationJob result = migrationService.createMigrationJob(request);

        assertThat(result.getMode()).isEqualTo("pilot");
        assertThat(result.getMaxScanId()).isEqualTo(10);
        assertThat(result.getTotalCount()).isEqualTo(1);
        verify(scanMapper, never()).countEligibleMigrations(anyInt(), any());
        verify(migrationJobMapper).complete(
                eq(7L),
                eq("completed"),
                eq(1L),
                eq(1L),
                eq(0L),
                any(BigDecimal.class),
                isNull(),
                any(Date.class)
        );
        verify(ossService, times(1)).generatePresignedUrl(anyString());
    }

    @Test
    @DisplayName("进度更新后收到取消请求仍以 cancelled 收尾")
    void cancellationAfterProgressIsNotOverwritten() throws Exception {
        MigrationJobRequest request = new MigrationJobRequest();
        request.setMode("pilot");
        request.setLimit(1);

        Scan pending = legacyScan(2, "00789509", "605747", null);
        Path file = createFile("cancel.jpg", new byte[]{4, 5, 6});
        MigrationJob persisted = new MigrationJob();

        when(scanMapper.findPendingMigration(20)).thenReturn(List.of(pending));
        when(sourceResolver.canRead(pending)).thenReturn(true);
        when(scanMapper.findMaxPendingMigrationId(isNull())).thenReturn(20);
        when(migrationJobMapper.insert(any(MigrationJob.class))).thenAnswer(invocation -> {
            MigrationJob job = invocation.getArgument(0);
            job.setId(9L);
            persisted.setId(job.getId());
            persisted.setTotalCount(job.getTotalCount());
            persisted.setMaxScanId(job.getMaxScanId());
            persisted.setScopeValue(job.getScopeValue());
            return 1;
        });
        when(migrationJobMapper.findById(9L)).thenReturn(persisted);
        when(migrationJobMapper.markRunning(eq(9L), any(Date.class))).thenReturn(1);
        when(migrationJobMapper.isCancelRequested(9L)).thenReturn(false, false, false, true);
        when(scanMapper.findPendingMigrationAfterId(0, 20, null, 1)).thenReturn(List.of(pending));
        when(sourceResolver.resolve(pending)).thenReturn(resolved(file));
        when(ossService.calculateMd5(file.toString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(ossService.getFileSize(file.toString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(migrationTaskExecutor).execute(any(Runnable.class));

        migrationService.createMigrationJob(request);

        verify(migrationJobMapper).updateProgress(eq(9L), eq(1L), eq(0L), any(BigDecimal.class));
        verify(migrationJobMapper).complete(
                eq(9L),
                eq("cancelled"),
                eq(1L),
                eq(1L),
                eq(0L),
                any(BigDecimal.class),
                anyString(),
                any(Date.class)
        );
    }

    @Test
    @DisplayName("所有受控来源均永久缺失时标记永久失败")
    void missingSourceIsMarkedFailed() throws Exception {
        Scan scan = legacyScan(1, "00789508", "605746", null);
        when(scanMapper.findById(1)).thenReturn(scan);
        when(sourceResolver.resolve(scan)).thenThrow(
                new MigrationSourceResolver.SourceResolutionException(
                        "所有受控图片来源均读取失败：文件不存在",
                        true,
                        new FileNotFoundException("missing")
                )
        );

        OssUploadResult result = migrationService.uploadSingleScan(1);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getErrorMessage()).contains("不存在");
        verify(scanMapper).markMigrationStarted(1);
        verify(scanMapper).markMigrationFailed(1, "SOURCE_FILE_UNAVAILABLE");
        verify(ossService, never()).uploadFile(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("OSS 临时异常在最大次数前进入等待下一任务重试")
    void temporaryOssFailureWaitsForNextJobRetry() throws Exception {
        Scan scan = legacyScan(1, "00789508", "605746", null);
        Path file = createFile("test.jpg", new byte[]{1, 2, 3});
        when(scanMapper.findById(1)).thenReturn(scan);
        when(sourceResolver.resolve(scan)).thenReturn(resolved(file));
        when(ossService.calculateMd5(file.toString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(ossService.getFileSize(file.toString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);
        when(ossService.uploadFile(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connection timeout"));

        OssUploadResult result = migrationService.uploadSingleScan(1);

        assertThat(result.getStatus()).isEqualTo("retry_wait");
        verify(scanMapper).markMigrationRetryWait(eq(1), eq("SOURCE_OR_OSS_TIMEOUT"), any());
        verify(scanMapper, never()).markMigrationFailed(eq(1), anyString());
    }

    @Test
    @DisplayName("迁移成功后签名 URL 失败不会回滚成功状态")
    void signedUrlFailureDoesNotReclassifySuccessfulMigration() throws Exception {
        Scan scan = legacyScan(3, "00789510", "605748", null);
        scan.setFilename("signed-url.jpg");
        Path file = createFile("signed-url.jpg", new byte[]{7, 8, 9});
        String ossKey = "medical-records/1234/12345678-00789510/signed-url.jpg";

        when(scanMapper.findById(3)).thenReturn(scan);
        when(sourceResolver.resolve(scan)).thenReturn(resolved(file));
        when(ossService.calculateMd5(file.toString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(ossService.getFileSize(file.toString())).thenReturn(3L);
        when(ossService.doesObjectExist(ossKey)).thenReturn(false);
        when(ossService.uploadFile(file.toString(), ossKey, "0123456789abcdef0123456789abcdef"))
                .thenReturn(ossKey);
        when(ossService.generatePresignedUrl(ossKey))
                .thenThrow(new RuntimeException("sign service unavailable"));

        OssUploadResult result = migrationService.uploadSingleScan(3);

        assertThat(result.getStatus()).isEqualTo("success");
        assertThat(result.getOssUrl()).isNull();
        verify(scanMapper).updateOssInfo(
                3,
                ossKey,
                3L,
                "0123456789abcdef0123456789abcdef",
                "migrated"
        );
        verify(scanMapper, never()).markMigrationFailed(eq(3), anyString());
        verify(scanMapper, never()).markMigrationRetryWait(eq(3), anyString(), any());
    }

    @Test
    @DisplayName("原子领取失败时不重复读取和上传")
    void skippedWhenAtomicClaimFails() throws Exception {
        Scan scan = legacyScan(4, "00789511", "605749", null);
        Scan latest = legacyScan(4, "00789511", "605749", null);
        latest.setOssUrl("medical-records/existing.jpg");
        when(scanMapper.findById(4)).thenReturn(scan, latest);
        when(scanMapper.markMigrationStarted(4)).thenReturn(0);

        OssUploadResult result = migrationService.uploadSingleScan(4);

        assertThat(result.getStatus()).isEqualTo("skipped");
        assertThat(result.getOssUrl()).isEqualTo("medical-records/existing.jpg");
        verify(sourceResolver, never()).resolve(any());
        verify(ossService, never()).uploadFile(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("缺少上架号时保留记录等待补齐且不计失败")
    void missingSjhWaitsWithoutClaimingOrRetrying() {
        Scan scan = legacyScan(6, "00789513", "605751", null);
        scan.setSjh(null);
        when(scanMapper.findById(6)).thenReturn(scan);

        OssUploadResult result = migrationService.uploadSingleScan(6);

        assertThat(result.getStatus()).isEqualTo("waiting_sjh");
        verify(scanMapper).markMigrationWaitingSjh(6);
        verify(scanMapper, never()).markMigrationStarted(6);
        verify(scanMapper, never()).markMigrationFailed(eq(6), anyString());
        verify(scanMapper, never()).markMigrationRetryWait(eq(6), anyString(), any());
        verify(sourceResolver, never()).resolve(scan);
        verify(ossService, never()).uploadFile(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("高位病案号使用上架号构建 OSS Key")
    void highBahUsesSjhAsDirectoryKey() throws Exception {
        Scan scan = legacyScan(2, "10000001", "605746", "87654321");
        scan.setFilename("high-bah.jpg");
        Path file = createFile("high-bah.jpg", new byte[]{4, 5, 6});
        when(scanMapper.findById(2)).thenReturn(scan);
        when(sourceResolver.resolve(scan)).thenReturn(resolved(file));
        when(ossService.calculateMd5(file.toString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(ossService.getFileSize(file.toString())).thenReturn(3L);
        when(ossService.doesObjectExist(anyString())).thenReturn(false);

        OssUploadResult result = migrationService.uploadSingleScan(2);

        assertThat(result.getStatus()).isEqualTo("success");
        verify(ossService).uploadFile(
                file.toString(),
                "medical-records/8765/87654321-10000001/high-bah.jpg",
                "0123456789abcdef0123456789abcdef"
        );
    }

    @Test
    @DisplayName("迁移统计复用短期缓存并使用显式 pending 口径")
    void statisticsUseExplicitPendingAndShortCache() {
        when(scanMapper.countMigrationStats()).thenReturn(stats(4, 1, 1, 1, 1, 0, 1));

        MigrationStatisticsDTO first = migrationService.getStatistics();
        MigrationStatisticsDTO second = migrationService.getStatistics();

        assertThat(first.getMigratedCount()).isEqualTo(2);
        assertThat(first.getPendingCount()).isEqualTo(1);
        assertThat(second).isSameAs(first);
        verify(scanMapper, times(1)).countMigrationStats();
    }

    @Test
    @DisplayName("迁移前检查抽样支持多来源可读与缺失统计")
    void readinessSamplesResolvedSources() {
        Scan readable = legacyScan(1, "00789508", "605746", null);
        Scan missing = legacyScan(2, "00789509", "605747", null);
        when(scanMapper.countMigrationStats()).thenReturn(stats(2, 0, 0, 0, 0, 0, 2));
        when(scanMapper.findPendingMigration(2)).thenReturn(List.of(readable, missing));
        when(sourceResolver.canRead(readable)).thenReturn(true);
        when(sourceResolver.canRead(missing)).thenReturn(false);
        when(sourceResolver.isLocalBasePathConfigured()).thenReturn(false);
        when(sourceResolver.isLocalBasePathReadable()).thenReturn(false);

        MigrationReadinessDTO readiness = migrationService.getReadiness(2);

        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.isSourcePathConfigured()).isTrue();
        assertThat(readiness.getSampleReadableCount()).isEqualTo(1);
        assertThat(readiness.getSampleMissingCount()).isEqualTo(1);
        assertThat(readiness.getRecommendedMode()).isEqualTo("pilot");
        assertThat(readiness.getWarnings()).anyMatch(item -> item.contains("缺失"));
    }

    @Test
    @DisplayName("执行器拒绝长任务时把任务明确标记失败")
    void executorRejectionMarksJobFailed() {
        MigrationJobRequest request = new MigrationJobRequest();
        request.setMode("pilot");
        request.setLimit(10);
        Scan pending = legacyScan(5, "00789512", "605750", null);

        when(scanMapper.findPendingMigration(20)).thenReturn(List.of(pending));
        when(sourceResolver.canRead(pending)).thenReturn(true);
        when(scanMapper.findMaxPendingMigrationId(isNull())).thenReturn(30);
        when(migrationJobMapper.insert(any(MigrationJob.class))).thenAnswer(invocation -> {
            MigrationJob job = invocation.getArgument(0);
            job.setId(10L);
            return 1;
        });
        doThrow(new RejectedExecutionException("shutdown"))
                .when(migrationTaskExecutor).execute(any(Runnable.class));

        assertThatThrownBy(() -> migrationService.createMigrationJob(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("执行器暂不可用");

        verify(migrationJobMapper).complete(
                eq(10L),
                eq("failed"),
                eq(0L),
                eq(0L),
                eq(0L),
                eq(BigDecimal.ZERO),
                anyString(),
                any(Date.class)
        );
    }

    private Scan legacyScan(int id, String bah, String brxh, String sjh) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setBah(bah);
        scan.setBrxh(brxh);
        scan.setSjh(sjh == null ? "12345678" : sjh);
        scan.setFolder("25.03.15");
        scan.setFilename(id + ".jpg");
        scan.setUploadFlag(1);
        return scan;
    }

    private Path createFile(String name, byte[] content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private MigrationSourceResolver.ResolvedSource resolved(Path file) {
        return new MigrationSourceResolver.ResolvedSource(file, file.toString(), false);
    }

    private Map<String, Object> stats(long total,
                                      long migrated,
                                      long verified,
                                      long failed,
                                      long retryWait,
                                      long migrating,
                                      long pending) {
        Map<String, Object> values = new HashMap<>();
        values.put("total", total);
        values.put("migrated", migrated);
        values.put("verified", verified);
        values.put("failed", failed);
        values.put("retry_wait", retryWait);
        values.put("migrating", migrating);
        values.put("pending", pending);
        values.put("waiting_sjh", 0L);
        return values;
    }
}
