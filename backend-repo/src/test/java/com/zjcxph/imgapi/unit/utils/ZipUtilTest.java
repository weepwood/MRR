package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.ZipUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ZipUtil 压缩工具测试")
class ZipUtilTest {

    @TempDir
    Path tempDir;

    private Path createFile(Path parent, String name) throws IOException {
        Files.createDirectories(parent);
        Path file = parent.resolve(name);
        Files.writeString(file, "fake-image-content-" + name);
        return file;
    }

    private List<String> entryNames(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            return names;
        }
    }

    @Nested
    @DisplayName("zipJpgFiles")
    class ZipJpgFiles {

        @Test
        @DisplayName("只打包 .jpg 文件，忽略 .png/.txt 等其它后缀")
        void onlyJpgFilesIncluded() throws IOException {
            Path src = tempDir.resolve("case");
            createFile(src, "a.jpg");
            createFile(src, "b.jpg");
            createFile(src, "c.png");
            createFile(src, "d.txt");
            Path dest = tempDir.resolve("out.zip");

            ZipUtil.zipJpgFiles(src.toString(), dest.toString());

            // 条目名以源目录名 "case/" 为前缀
            assertThat(entryNames(dest)).containsExactlyInAnyOrder("case/a.jpg", "case/b.jpg");
        }

        @Test
        @DisplayName("后缀大小写不敏感：.JPG 也会被打包")
        void jpgCaseInsensitive() throws IOException {
            Path src = tempDir.resolve("case2");
            createFile(src, "upper.JPG");
            createFile(src, "lower.jpg");
            Path dest = tempDir.resolve("out2.zip");

            ZipUtil.zipJpgFiles(src.toString(), dest.toString());

            assertThat(entryNames(dest)).containsExactlyInAnyOrder("case2/upper.JPG", "case2/lower.jpg");
        }

        @Test
        @DisplayName("递归处理子目录，条目名带相对路径前缀")
        void recursiveSubdirectories() throws IOException {
            Path src = tempDir.resolve("root");
            createFile(src, "top.jpg");
            createFile(src.resolve("sub"), "child.jpg");
            Path dest = tempDir.resolve("out3.zip");

            ZipUtil.zipJpgFiles(src.toString(), dest.toString());

            List<String> names = entryNames(dest);
            assertThat(names).contains("root/top.jpg", "root/sub/child.jpg");
        }

        @Test
        @DisplayName("源目录无 jpg 时，生成空 zip（0 条目）")
        void noJpg_emptyZip() throws IOException {
            Path src = tempDir.resolve("empty");
            createFile(src, "note.txt");
            Path dest = tempDir.resolve("out4.zip");

            ZipUtil.zipJpgFiles(src.toString(), dest.toString());

            assertThat(entryNames(dest)).isEmpty();
        }

        @Test
        @DisplayName("目标文件在已存在目录下时正常写入")
        void destCreated() throws IOException {
            Path src = tempDir.resolve("src5");
            createFile(src, "x.jpg");
            Path dest = tempDir.resolve("out5.zip");

            ZipUtil.zipJpgFiles(src.toString(), dest.toString());

            assertThat(Files.exists(dest)).isTrue();
            assertThat(entryNames(dest)).containsExactly("src5/x.jpg");
        }

        @Test
        @DisplayName("源路径不存在时，不抛异常（生成空 zip）")
        void srcNotExist_producesEmptyZip() throws IOException {
            Path dest = tempDir.resolve("out.zip");

            ZipUtil.zipJpgFiles(tempDir.resolve("nope").toString(), dest.toString());

            assertThat(Files.exists(dest)).isTrue();
            assertThat(entryNames(dest)).isEmpty();
        }
    }
}
