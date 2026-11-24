package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void diagnosticClosesNasStreamAndReleasesPermitForNextRead() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("diagnostic-nas"));
        Files.writeString(root.resolve("page.jpg"), "synthetic");
        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        properties.setNasMaxConcurrency(1);
        properties.setAcquireTimeout(Duration.ofMillis(100));
        var node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("synthetic-node", node));
        PathDO image = new PathDO();
        image.setSourceType("NAS");
        image.setSourceNode("synthetic-node");
        image.setSourceRef("page.jpg");
        var source = new NasArchiveImageSource(properties);
        var storage = new ResolvedImageStorage(new ArchiveImageSourceResolver(List.of(source)), new SimpleMeterRegistry());

        var probe = storage.probe(image);
        assertThat(probe.available()).isTrue();
        assertThat(probe.selectedSource()).isEqualTo("NAS");
        assertThat(probe.toString()).doesNotContain(root.toString(), "synthetic-node", "page.jpg");
        try (var stream = source.open(image)) {
            assertThat(stream.readAllBytes()).isEqualTo("synthetic".getBytes(StandardCharsets.UTF_8));
        }
        Files.delete(root.resolve("page.jpg"));
        assertThat(storage.probe(image).attempts()).containsExactly(
                new ResolvedImageStorage.SourceAttempt("NAS", false, "NOT_FOUND"));
    }

    @Test
    void readsHighBahFromSjhDirectoryWhenSourceRefIsAbsent() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("high-bah-root"));
        Path file = Files.createDirectories(
                        root.resolve("24.04")
                                .resolve("24.04.07")
                                .resolve("00789124-10000000"))
                .resolve("0013.jpg");
        Files.writeString(file, "nas-image", StandardCharsets.UTF_8);

        ArchiveImageSourceProperties properties = new ArchiveImageSourceProperties();
        ArchiveImageSourceProperties.NasNode node = new ArchiveImageSourceProperties.NasNode();
        node.setRoot(root.toString());
        properties.setNasNodes(Map.of("archive01", node));
        PathDO image = new PathDO(
                13,
                "24.04.07",
                "0013.jpg",
                "666666",
                "10000000",
                "00789124",
                "NAS",
                "archive01",
                null,
                null,
                null);
        NasArchiveImageSource source = new NasArchiveImageSource(properties);

        try (var input = source.open(image)) {
            assertThat(input.readAllBytes()).isEqualTo("nas-image".getBytes(StandardCharsets.UTF_8));
        }
    }
}
