package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchiveImageSourceResolverTest {

    @Test
    void resolvesTheFirstSupportingSourceInConfiguredOrder() throws Exception {
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        ArchiveImageSource local = mock(ArchiveImageSource.class);
        ArchiveImageSource oss = mock(ArchiveImageSource.class);
        when(local.supports(image)).thenReturn(false);
        when(oss.supports(image)).thenReturn(true);
        when(oss.describeSource(image)).thenReturn("OSS");

        ArchiveImageSourceResolver resolver = new ArchiveImageSourceResolver(List.of(local, oss));

        assertThat(resolver.resolve(image)).isSameAs(oss);
        assertThat(resolver.describeSource(image)).isEqualTo("OSS");
    }

    @Test
    void rejectsImagesThatHaveNoControlledSource() {
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");
        ArchiveImageSource unsupported = mock(ArchiveImageSource.class);
        when(unsupported.supports(image)).thenReturn(false);

        ArchiveImageSourceResolver resolver = new ArchiveImageSourceResolver(List.of(unsupported));

        assertThatThrownBy(() -> resolver.resolve(image))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("没有可用的受控图片来源");
    }
}
