package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void opensFileInsideConfiguredRoot() throws Exception {
        ImageProperties properties = new ImageProperties();
        properties.setBasePath(tempDir.toString());
        LocalImageStorage storage = new LocalImageStorage(properties);
        PathDO image = image("25.03.15", "605746", "00789508", "page-1.jpg");

        Path file = tempDir.resolve("25.03/25.03.15/605746-00789508/page-1.jpg");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "image", StandardCharsets.UTF_8);

        try (var input = storage.open(image)) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("image");
        }
    }

    @Test
    void rejectsPathTraversalInFilename() {
        ImageProperties properties = new ImageProperties();
        properties.setBasePath(tempDir.toString());
        LocalImageStorage storage = new LocalImageStorage(properties);
        PathDO image = image("25.03.15", "605746", "00789508", "../secret.txt");

        assertThatThrownBy(() -> storage.resolve(image))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("非法路径字符");
    }

    @Test
    void rejectsMissingBasePath() {
        LocalImageStorage storage = new LocalImageStorage(new ImageProperties());

        assertThatThrownBy(() -> storage.resolve(image("25.03.15", "605746", "00789508", "page.jpg")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("image.basePath");
    }

    private PathDO image(String folder, String brxh, String bah, String filename) {
        return new PathDO(folder, filename, brxh, bah);
    }
}
