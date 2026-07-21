package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasArchiveImageSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsSymlinkThatEscapesTheConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("page.jpg"), "image");
        try {
            Files.createSymbolicLink(root.resolve("linked"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "当前运行环境不支持创建符号链接");
        }

        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        ArchiveImageSourceProperties.NasNode node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("archive01", node));
        PathDO image = new PathDO();
        image.setSourceType("NAS");
        image.setSourceNode("archive01");
        image.setSourceRef("linked/page.jpg");
        NasArchiveImageSource source = new NasArchiveImageSource(properties);

        assertThatThrownBy(() -> source.open(image))
                .isInstanceOf(InvalidImagePathException.class)
                .hasMessageContaining("符号链接越过");
    }
}
