package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveImagePathSupportTest {

    @Test
    void rejectsAbsoluteWindowsPathsAndUrls() {
        PathDO windows = new PathDO();
        windows.setSourceType("LOCAL");
        windows.setSourceRef("C:\\medical\\page.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(windows))
                .isInstanceOf(InvalidImagePathException.class);

        PathDO url = new PathDO();
        url.setSourceType("HTTP");
        url.setSourceRef("http://evil.example/image.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(url))
                .isInstanceOf(InvalidImagePathException.class);
    }

    @Test
    void rejectsParentDirectoryTraversal() {
        PathDO image = new PathDO();
        image.setSourceType("NAS");
        image.setSourceRef("../outside/page.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(image))
                .isInstanceOf(InvalidImagePathException.class);
    }

    @Test
    void usesSjhForHighBahLegacyPaths() throws Exception {
        PathDO image = new PathDO(
                13,
                "24.04.07",
                "0013.jpg",
                "666666",
                "10000000",
                "00789124",
                "AUTO",
                null,
                null,
                null,
                null);

        assertThat(ArchiveImagePathSupport.relativePath(image))
                .isEqualTo(Path.of("24.04", "24.04.07", "00789124-10000000", "0013.jpg"));
    }

    @Test
    void ignoresOssObjectKeyWhenFallingBackToLegacyPath() throws Exception {
        PathDO image = new PathDO(
                13,
                "24.04.07",
                "0013.jpg",
                "666666",
                "00789124",
                null,
                "OSS",
                null,
                "medical-records/24.04/24.04.07/666666-00789124/0013.jpg",
                null,
                null);

        assertThat(ArchiveImagePathSupport.relativePath(image))
                .isEqualTo(Path.of("24.04", "24.04.07", "666666-00789124", "0013.jpg"));
    }
}
