package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MixedArchiveImageSourceFallbackTest {

    @Test
    void readsEachImageFromOssOrLocalAccordingToItsOwnMigrationState() throws Exception {
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = new OssProperties();
        ossProperties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        ossProperties.setBucket("mrr-medical-records");
        ossProperties.setBaseUrl("https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com");
        OssObjectReader objectReader = mock(OssObjectReader.class);
        LocalImageStorage localStorage = mock(LocalImageStorage.class);
        OssArchiveImageSource ossSource = new OssArchiveImageSource(
                objectReader, sourceProperties, ossProperties);
        LocalArchiveImageSource localSource = new LocalArchiveImageSource(localStorage, sourceProperties);
        ArchiveImageSourceResolver resolver = new ArchiveImageSourceResolver(List.of(ossSource, localSource));
        ResolvedImageStorage storage = new ResolvedImageStorage(resolver, new SimpleMeterRegistry());

        PathDO migrated = image("001.jpg");
        migrated.setSourceType("OSS");
        migrated.setSourceRef("25.03/001.jpg");
        when(objectReader.open("25.03/001.jpg")).thenReturn(stream("oss"));

        PathDO notMigrated = image("002.jpg");
        notMigrated.setSourceType("OSS");
        when(localStorage.open(notMigrated)).thenReturn(stream("local"));

        try (var input = storage.open(migrated)) {
            assertThat(input.readAllBytes()).isEqualTo("oss".getBytes(StandardCharsets.UTF_8));
        }
        try (var input = storage.open(notMigrated)) {
            assertThat(input.readAllBytes()).isEqualTo("local".getBytes(StandardCharsets.UTF_8));
        }

        verify(objectReader).open("25.03/001.jpg");
        verify(localStorage).open(notMigrated);
        assertThat(resolver.resolveCandidates(migrated))
                .extracting(source -> source.describeSource(migrated))
                .containsExactly("OSS", "LOCAL");
        assertThat(resolver.resolveCandidates(notMigrated))
                .extracting(source -> source.describeSource(notMigrated))
                .containsExactly("LOCAL");
    }

    private PathDO image(String filename) {
        return new PathDO("25.03.15", filename, "605746", "00789508");
    }

    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
