package com.zjcxph.imgapi.unit.service;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zjcxph.imgapi.config.CacheConfig;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.impl.ManagedPrefixOssService;
import com.zjcxph.imgapi.service.impl.OssServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real Spring cache proxies and a controlled Caffeine ticker; no remote storage. */
class OssSignedUrlCacheTest {
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger signatures = new AtomicInteger();
    private final S3Presigner presigner = mock(S3Presigner.class);

    @Test
    void shortLivedSignaturesExpireFromCacheBeforeTheirSigningLifetime() {
        runner(60).run(context -> {
            OssService service = prepare(context.getBean(OssService.class));
            String first = service.generatePresignedUrl("medical-records/synthetic.jpg");
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isEqualTo(first);
            nanos.set(TimeUnit.SECONDS.toNanos(29));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isEqualTo(first);
            nanos.set(TimeUnit.SECONDS.toNanos(31));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isNotEqualTo(first);
            nanos.set(TimeUnit.SECONDS.toNanos(92));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isNotEqualTo(first);
            verify(presigner, times(3)).presignGetObject(any(GetObjectPresignRequest.class));
        });
    }

    @Test
    void longLivedSignaturesKeepTheExistingTenMinuteCacheBudget() {
        runner(3600).run(context -> {
            OssService service = prepare(context.getBean(OssService.class));
            String first = service.generatePresignedUrl("medical-records/synthetic.jpg");
            nanos.set(TimeUnit.SECONDS.toNanos(599));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isEqualTo(first);
            nanos.set(TimeUnit.SECONDS.toNanos(601));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isNotEqualTo(first);
        });
    }

    @Test
    void browserAndGenericSigningShareExpiryButNeverBypassManagedPrefixChecks() {
        runner(60).run(context -> {
            OssService delegate = prepare(context.getBean(OssService.class));
            OssService managed = new ManagedPrefixOssService(delegate);
            String first = managed.generatePresignedUrl("medical-records/synthetic.jpg");
            assertThat(managed.generateBrowserPresignedUrl("medical-records/synthetic.jpg")).isEqualTo(first);
            managed.generatePresignedUrl("private/synthetic.jpg");
            assertThatThrownBy(() -> managed.generateBrowserPresignedUrl("private/synthetic.jpg"))
                    .isInstanceOf(IllegalArgumentException.class);
            nanos.set(TimeUnit.SECONDS.toNanos(61));
            assertThat(managed.generateBrowserPresignedUrl("medical-records/synthetic.jpg")).isNotEqualTo(first);
            verify(presigner, times(3)).presignGetObject(any(GetObjectPresignRequest.class));
        });
    }

    @Test
    void changingStartupConfigurationCreatesFreshSignaturesAndUsesNewDuration() {
        String[] previous = new String[1];
        runner(3600).run(context -> previous[0] = prepare(context.getBean(OssService.class))
                .generatePresignedUrl("medical-records/synthetic.jpg"));
        runner(60).run(context -> {
            OssService service = prepare(context.getBean(OssService.class));
            assertThat(service.generatePresignedUrl("medical-records/synthetic.jpg")).isNotEqualTo(previous[0]);
            verify(presigner).presignGetObject(org.mockito.ArgumentMatchers.argThat(
                    (GetObjectPresignRequest request) -> request.signatureDuration().equals(Duration.ofSeconds(60))));
        });
    }

    private ApplicationContextRunner runner(int seconds) {
        return new ApplicationContextRunner().withUserConfiguration(CacheConfig.class, SigningConfig.class)
                .withBean(OssProperties.class, () -> {
                    OssProperties properties = new OssProperties();
                    properties.setUrlExpireSeconds(seconds);
                    properties.setBucket("synthetic-bucket");
                    return properties;
                }).withBean(Ticker.class, () -> nanos::get);
    }

    private OssService prepare(OssService service) throws Exception {
        Object target = AopTestUtils.getUltimateTargetObject(service);
        ReflectionTestUtils.setField(target, "s3Client", mock(S3Client.class));
        ReflectionTestUtils.setField(target, "s3Presigner", presigner);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenAnswer(invocation -> {
            PresignedGetObjectRequest result = mock(PresignedGetObjectRequest.class);
            when(result.url()).thenReturn(URI.create("https://example.invalid/synthetic/" + signatures.incrementAndGet()).toURL());
            return result;
        });
        return service;
    }

    @Configuration(proxyBeanMethods = false)
    static class SigningConfig {
        @Bean
        OssService ossService(OssProperties properties) { return new OssServiceImpl(properties); }
    }
}
