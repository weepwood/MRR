package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.OssProperties;
import software.amazon.awssdk.services.s3.model.S3Exception;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolvedImageStorageFallbackTest {

    @Test
    void diagnosticClassifiesOssStatusAndDoesNotExposeObjectKey() throws Exception {
        PathDO image = new PathDO();
        image.setSourceType("OSS");
        image.setSourceRef("synthetic-private-key");
        OssObjectReader reader = mock(OssObjectReader.class);
        when(reader.open("synthetic-private-key")).thenThrow(new IOException("synthetic-private-error",
                S3Exception.builder().statusCode(403).message("synthetic-private-error").build()));
        var source = new OssArchiveImageSource(reader, new ArchiveImageSourceProperties(), new OssProperties());
        var storage = new ResolvedImageStorage(new ArchiveImageSourceResolver(List.of(source)), new SimpleMeterRegistry());

        var probe = storage.probe(image);
        assertThat(probe.available()).isFalse();
        assertThat(probe.attempts()).containsExactly(new ResolvedImageStorage.SourceAttempt("OSS", false, "ACCESS_DENIED"));
        assertThat(probe.toString()).doesNotContain("synthetic-private");
    }

    @Test
    void interruptedSourceMustNotStartAnotherFallback() throws Exception {
        var image = new PathDO();
        var first = mock(ArchiveImageSource.class);
        var second = mock(ArchiveImageSource.class);
        var resolver = mock(ArchiveImageSourceResolver.class);
        when(resolver.resolveCandidates(image)).thenReturn(List.of(first, second));
        when(first.describeSource(image)).thenReturn("NGINX");
        when(second.describeSource(image)).thenReturn("LOCAL");
        when(first.open(image)).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IOException("synthetic cancelled", new InterruptedException());
        });
        when(second.open(image)).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        var storage = new ResolvedImageStorage(resolver, new SimpleMeterRegistry());
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                try (var ignored = storage.open(image)) {}
            }).isInstanceOf(IOException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            org.mockito.Mockito.verify(second, org.mockito.Mockito.never()).open(image);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void fallsBackToTheNextControlledSourceWhenAutoSourceFails() throws Exception {
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("AUTO");
        ArchiveImageSource oss = mock(ArchiveImageSource.class);
        ArchiveImageSource local = mock(ArchiveImageSource.class);
        when(oss.describeSource(image)).thenReturn("OSS");
        when(local.describeSource(image)).thenReturn("LOCAL");
        when(oss.open(image)).thenThrow(new IOException("object missing"));
        when(local.open(image)).thenReturn(new ByteArrayInputStream(
                "local".getBytes(StandardCharsets.UTF_8)));
        ArchiveImageSourceResolver resolver = mock(ArchiveImageSourceResolver.class);
        when(resolver.resolveCandidates(image)).thenReturn(List.of(oss, local));
        ResolvedImageStorage storage = new ResolvedImageStorage(resolver, new SimpleMeterRegistry());

        try (var input = storage.open(image)) {
            assertThat(input.readAllBytes()).isEqualTo("local".getBytes(StandardCharsets.UTF_8));
        }
    }
}
