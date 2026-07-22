package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.impl.MigrationSourceResolver;
import com.zjcxph.imgapi.service.impl.NginxOnlyMigrationSourceResolver;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.NginxArchiveImageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
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
@DisplayName("OSS 迁移强制 Nginx 来源测试")
class NginxOnlyMigrationSourceResolverTest {

    @Mock
    private ImageStorage imageStorage;
    @Mock
    private ImageProperties imageProperties;
    @Mock
    private NginxArchiveImageSource nginxSource;

    @Test
    @DisplayName("只通过 Nginx 获取图片并在上传后删除临时文件")
    void materializesNginxStreamOnly() throws Exception {
        Scan scan = scan();
        when(nginxSource.open(any(PathDO.class)))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        MigrationSourceResolver resolver = new NginxOnlyMigrationSourceResolver(
                imageStorage, imageProperties, nginxSource);

        Path materialized;
        try (MigrationSourceResolver.ResolvedSource resolved = resolver.resolve(scan)) {
            materialized = resolved.path();
            assertThat(resolved.description()).isEqualTo("NGINX:scan:1");
            assertThat(resolved.temporary()).isTrue();
            assertThat(Files.readAllBytes(materialized)).containsExactly(1, 2, 3);
        }

        assertThat(materialized).doesNotExist();
        verify(imageStorage, never()).open(any(PathDO.class));
    }

    @Test
    @DisplayName("Nginx 404 视为永久缺失")
    void nginx404IsPermanent() throws Exception {
        when(nginxSource.open(any(PathDO.class)))
                .thenThrow(new IOException("Nginx 图片服务返回状态码 404"));
        MigrationSourceResolver resolver = new NginxOnlyMigrationSourceResolver(
                imageStorage, imageProperties, nginxSource);

        assertThatThrownBy(() -> resolver.resolve(scan()))
                .isInstanceOfSatisfying(
                        MigrationSourceResolver.SourceResolutionException.class,
                        exception -> assertThat(exception.isPermanent()).isTrue());
    }

    @Test
    @DisplayName("Nginx 超时保留为可重试故障")
    void nginxTimeoutIsRetryable() throws Exception {
        when(nginxSource.open(any(PathDO.class)))
                .thenThrow(new SocketTimeoutException("timeout"));
        MigrationSourceResolver resolver = new NginxOnlyMigrationSourceResolver(
                imageStorage, imageProperties, nginxSource);

        assertThatThrownBy(() -> resolver.resolve(scan()))
                .isInstanceOfSatisfying(
                        MigrationSourceResolver.SourceResolutionException.class,
                        exception -> assertThat(exception.isPermanent()).isFalse());
    }

    private Scan scan() {
        Scan scan = new Scan();
        scan.setId(1);
        scan.setSjh("00123456");
        scan.setBah("00789124");
        scan.setBrxh("605746");
        scan.setFolder("25.03.15");
        scan.setFilename("0013.jpg");
        scan.setUploadFlag(1);
        return scan;
    }
}
