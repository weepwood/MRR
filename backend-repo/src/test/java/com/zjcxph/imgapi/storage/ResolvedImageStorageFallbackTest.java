package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
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
