package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.OssProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssArchiveImageSourceTest {

    private static final String ACTUAL_OBJECT_KEY =
            "medical-records/24.04/24.04.07/666666-00789124/0013.jpg";

    @Test
    void preservesActualMedicalRecordsObjectKeyWhenReadingThroughSdk() throws Exception {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        sourceProperties.setPreferOss(true);
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("AUTO");
        image.setOssUrl(ACTUAL_OBJECT_KEY);
        image.setFileSize(3L);
        when(reader.open(ACTUAL_OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("img".getBytes(StandardCharsets.UTF_8));
        assertThat(source.size(image)).isEqualTo(3L);
        verify(reader).open(ACTUAL_OBJECT_KEY);
    }

    @Test
    void extractsActualObjectKeyFromConfiguredFullOssUrl() throws Exception {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("OSS");
        image.setOssUrl("https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com/"
                + ACTUAL_OBJECT_KEY + "?versionId=1");
        when(reader.open(ACTUAL_OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream("oss".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("oss".getBytes(StandardCharsets.UTF_8));
        verify(reader).open(ACTUAL_OBJECT_KEY);
    }

    @Test
    void preservesMedicalRecordsPrefixWhenStoredInSourceRef() throws Exception {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("OSS");
        image.setSourceRef(ACTUAL_OBJECT_KEY);
        when(reader.open(ACTUAL_OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream("oss".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("oss".getBytes(StandardCharsets.UTF_8));
        verify(reader).open(ACTUAL_OBJECT_KEY);
    }

    @Test
    void rejectsFullUrlsOutsideConfiguredOssHost() {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("OSS");
        image.setOssUrl("https://example.com/" + ACTUAL_OBJECT_KEY);

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isFalse();
    }

    @Test
    void rejectsAbsoluteSourceReferences() {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("OSS");
        image.setSourceRef("/" + ACTUAL_OBJECT_KEY);

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isFalse();
    }

    @Test
    void repeatedNetworkTimeoutsOpenCircuitButMissingObjectsDoNot() throws Exception {
        var reader = mock(OssObjectReader.class);
        var source = new OssArchiveImageSource(reader, new ArchiveImageSourceProperties(), ossProperties());
        var image = new PathDO("24.04.07", "0013.jpg", "666666", "00789124");
        image.setSourceType("OSS");
        image.setSourceRef(ACTUAL_OBJECT_KEY);
        when(reader.open(ACTUAL_OBJECT_KEY)).thenThrow(new java.io.IOException("missing",
                software.amazon.awssdk.services.s3.model.S3Exception.builder().statusCode(404).build()));
        for (int i = 0; i < 4; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> source.open(image))
                    .isInstanceOf(java.io.IOException.class).hasMessage("missing");
        }
        org.mockito.Mockito.doThrow(new java.net.SocketTimeoutException("synthetic timeout")).when(reader).open(ACTUAL_OBJECT_KEY);
        for (int i = 0; i < 3; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> source.open(image))
                    .isInstanceOf(java.net.SocketTimeoutException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> source.open(image))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("temporarily unavailable");
        verify(reader, org.mockito.Mockito.times(7)).open(ACTUAL_OBJECT_KEY);
    }

    private OssProperties ossProperties() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("mrr-medical-records");
        properties.setBaseUrl("https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com");
        return properties;
    }
}
