package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.impl.MigrationSourceResolver;
import com.zjcxph.imgapi.storage.ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationSourceResolver 多来源解析测试")
class MigrationSourceResolverTest {

    @Mock
    private ImageStorage imageStorage;
    @Mock
    private ImageProperties imageProperties;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("普通病案优先直接使用 BRXH 本地文件")
    void resolvesLowBahLocalFileDirectly() throws Exception {
        Scan scan = scan(1, "00789508", "605746", null);
        Path source = tempDir.resolve("25.03/25.03.15/605746-00789508/test.jpg");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1, 2, 3});
        when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        try (MigrationSourceResolver.ResolvedSource resolved = resolver.resolve(scan)) {
            assertThat(resolved.path()).isEqualTo(source);
            assertThat(resolved.temporary()).isFalse();
        }

        assertThat(source).exists();
        verify(imageStorage, never()).open(any(PathDO.class));
    }

    @Test
    @DisplayName("高位病案直接路径使用唯一 SJH")
    void resolvesHighBahWithSjh() throws Exception {
        Scan scan = scan(2, "10000001", "605746", "87654321");
        Path source = tempDir.resolve("25.03/25.03.15/87654321-10000001/test.jpg");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{4, 5, 6});
        when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        try (MigrationSourceResolver.ResolvedSource resolved = resolver.resolve(scan)) {
            assertThat(resolved.path()).isEqualTo(source);
        }
    }

    @Test
    @DisplayName("本地文件不可用时通过现有 ImageStorage 读取 Nginx 或 HTTP 来源")
    void materializesResolvedImageStorageStream() throws Exception {
        Scan scan = scan(3, "00789509", "605747", null);
        scan.setSourceType("HTTP");
        scan.setSourceRef("25.03/25.03.15/605747-00789509/test.jpg");
        when(imageStorage.open(any(PathDO.class)))
                .thenReturn(new ByteArrayInputStream(new byte[]{7, 8, 9}));
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        Path materialized;
        try (MigrationSourceResolver.ResolvedSource resolved = resolver.resolve(scan)) {
            materialized = resolved.path();
            assertThat(resolved.temporary()).isTrue();
            assertThat(Files.readAllBytes(materialized)).containsExactly(7, 8, 9);
        }

        assertThat(materialized).doesNotExist();
    }

    @Test
    @DisplayName("混合来源包含超时时不能因其他来源缺失而判定永久失败")
    void mixedPermanentAndTransientFailuresRemainRetryable() throws Exception {
        Scan scan = scan(4, "00789510", "605748", null);
        scan.setSourceType("AUTO");
        IOException aggregate = new IOException("所有受控图片来源均读取失败");
        aggregate.addSuppressed(new FileNotFoundException("本地文件不存在"));
        aggregate.addSuppressed(new SocketTimeoutException("HTTP timeout"));
        when(imageStorage.open(any(PathDO.class))).thenThrow(aggregate);
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        assertThatThrownBy(() -> resolver.resolve(scan))
                .isInstanceOfSatisfying(
                        MigrationSourceResolver.SourceResolutionException.class,
                        exception -> assertThat(exception.isPermanent()).isFalse()
                );
    }

    @Test
    @DisplayName("所有候选来源均明确缺失时判定永久失败")
    void allPermanentFailuresArePermanent() throws Exception {
        Scan scan = scan(5, "00789511", "605749", null);
        IOException aggregate = new IOException("所有受控图片来源均读取失败");
        aggregate.addSuppressed(new FileNotFoundException("本地文件不存在"));
        aggregate.addSuppressed(new IOException("HTTP 状态码 404"));
        when(imageStorage.open(any(PathDO.class))).thenThrow(aggregate);
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        assertThatThrownBy(() -> resolver.resolve(scan))
                .isInstanceOfSatisfying(
                        MigrationSourceResolver.SourceResolutionException.class,
                        exception -> assertThat(exception.isPermanent()).isTrue()
                );
    }

    @Test
    @DisplayName("LOCAL sourceRef 越界时拒绝读取")
    void rejectsTraversalSourceRef() {
        Scan scan = scan(6, "00789512", "605750", null);
        scan.setSourceType("LOCAL");
        scan.setSourceRef("../outside.jpg");
        when(imageProperties.getBasePath()).thenReturn(tempDir.toString());
        MigrationSourceResolver resolver = new MigrationSourceResolver(imageStorage, imageProperties);

        assertThatThrownBy(() -> resolver.resolve(scan))
                .isInstanceOf(MigrationSourceResolver.SourceResolutionException.class)
                .hasMessageContaining("受控相对路径");
    }

    private Scan scan(int id, String bah, String brxh, String sjh) {
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
}
