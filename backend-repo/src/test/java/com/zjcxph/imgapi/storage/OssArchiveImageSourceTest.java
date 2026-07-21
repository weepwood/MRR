package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
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
        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        properties.setPreferOss(true);
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        image.setSourceType("AUTO");
        image.setOssUrl("archive/00789508/page.jpg");
        image.setFileSize(3L);
        when(reader.open("archive/00789508/page.jpg"))
                .thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));

        OssArchiveImageSource source = new OssArchiveImageSource(reader, properties);

        assertThat(source.supports(image)).isTrue();
        assertThat(source.open(image).readAllBytes()).isEqualTo("img".getBytes(StandardCharsets.UTF_8));
        assertThat(source.size(image)).isEqualTo(3L);
        verify(reader).open("archive/00789508/page.jpg");
    }
}
