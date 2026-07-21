package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageSymlinkTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsImageSymlinkThatEscapesTheConfiguredRoot() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path outsideImage = outside.resolve("page.jpg");
        Files.writeString(outsideImage, "image");

        Path imageDirectory = Files.createDirectories(
                root.resolve("25.03").resolve("25.03.15").resolve("605746-00789508"));
        try {
            Files.createSymbolicLink(imageDirectory.resolve("page.jpg"), outsideImage);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "当前运行环境不支持创建符号链接");
        }

        ImageProperties properties = new ImageProperties();
        properties.setBasePath(root.toString());
        LocalImageStorage storage = new LocalImageStorage(properties);
        PathDO image = new PathDO("25.03.15", "page.jpg", "605746", "00789508");

        assertThatThrownBy(() -> storage.open(image))
                .isInstanceOf(InvalidImagePathException.class)
                .hasMessageContaining("符号链接越过");
    }

    @Test
    void resolvesHighBahBySjhInsteadOfBrxh() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("high-bah-root"));
        ImageProperties properties = new ImageProperties();
        properties.setBasePath(root.toString());
        LocalImageStorage storage = new LocalImageStorage(properties);
        PathDO image = new PathDO(
                1,
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

        assertThat(storage.resolve(image)).isEqualTo(
                root.resolve("24.04")
                        .resolve("24.04.07")
                        .resolve("00789124-10000000")
                        .resolve("0013.jpg")
                        .toAbsolutePath()
                        .normalize());
    }
}
