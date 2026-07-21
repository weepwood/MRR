package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveImagePathSupportTest {

    @Test
    void rejectsAbsoluteWindowsPathsAndUrls() {
        PathDO windows = new PathDO();
        windows.setSourceRef("C:\\medical\\page.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(windows))
                .isInstanceOf(InvalidImagePathException.class);

        PathDO url = new PathDO();
        url.setSourceRef("http://evil.example/image.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(url))
                .isInstanceOf(InvalidImagePathException.class);
    }

    @Test
    void rejectsParentDirectoryTraversal() {
        PathDO image = new PathDO();
        image.setSourceRef("../outside/page.jpg");
        assertThatThrownBy(() -> ArchiveImagePathSupport.relativePath(image))
                .isInstanceOf(InvalidImagePathException.class);
    }
}
