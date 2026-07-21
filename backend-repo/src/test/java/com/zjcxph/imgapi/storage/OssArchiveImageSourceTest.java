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

    @Test
    void opensObjectThroughBackendSdkReaderWithoutPresignedUrl() throws Exception {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        sourceProperties.setPreferOss(true);
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("AUTO");
        image.setOssUrl("archive/00789508/page.jpg");
        image.setFileSize(3L);
        when(reader.open("archive/00789508/page.jpg"))
                .thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("img".getBytes(StandardCharsets.UTF_8));
        assertThat(source.size(image)).isEqualTo(3L);
        verify(reader).open("archive/00789508/page.jpg");
    }

    @Test
    void extractsObjectKeyFromConfiguredFullOssUrl() throws Exception {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("OSS");
        image.setOssUrl("https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com/archive/00789508/page.jpg?versionId=1");
        when(reader.open("archive/00789508/page.jpg"))
                .thenReturn(new ByteArrayInputStream("oss".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("oss".getBytes(StandardCharsets.UTF_8));
        verify(reader).open("archive/00789508/page.jpg");
    }

    @Test
    void rejectsFullUrlsOutsideConfiguredOssHost() {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("OSS");
        image.setOssUrl("https://example.com/archive/00789508/page.jpg");

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isFalse();
    }

    @Test
    void rejectsAbsoluteSourceReferences() {
        OssObjectReader reader = mock(OssObjectReader.class);
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        OssProperties ossProperties = ossProperties();
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("OSS");
        image.setSourceRef("/archive/00789508/page.jpg");

        OssArchiveImageSource source = new OssArchiveImageSource(reader, sourceProperties, ossProperties);

        assertThat(source.supports(image)).isFalse();
    }

    private OssProperties ossProperties() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("mrr-medical-records");
        properties.setBaseUrl("https://mrr-medical-records.oss-cn-hangzhou.aliyuncs.com");
        return properties;
    }
}
