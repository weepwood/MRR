package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.PdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfService PDF 生成测试")
class PdfServiceTest {

    @TempDir
    Path tempDir;

    private final PdfService pdfService = new PdfService();

    private Path createRealJpeg(Path dir, String name) throws Exception {
        Files.createDirectories(dir);
        Path img = dir.resolve(name);
        BufferedImage bi = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bi, "jpg", img.toFile());
        return img;
    }

    @Test
    @DisplayName("合法图片列表 — 生成 PDF 返回 true 且文件非空")
    void createPdfFromImages_validImages() throws Exception {
        Path img1 = createRealJpeg(tempDir, "a.jpg");
        Path img2 = createRealJpeg(tempDir, "b.jpg");
        Path output = tempDir.resolve("result.pdf");

        boolean ok = pdfService.createPdfFromImages(
                output.toString(),
                List.of(img1.toString(), img2.toString()));

        assertThat(ok).isTrue();
        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isPositive();
        // PDF 文件头魔数
        assertThat(Files.readAllBytes(output)[0]).isEqualTo((byte) '%');
    }

    @Test
    @DisplayName("非法图片路径 — 返回 false 不抛异常")
    void createPdfFromImages_invalidPath_returnsFalse() {
        Path output = tempDir.resolve("fail.pdf");

        boolean ok = pdfService.createPdfFromImages(
                output.toString(),
                List.of(tempDir.resolve("not-exist.jpg").toString()));

        assertThat(ok).isFalse();
    }

    @Test
    @DisplayName("空图片列表 — 返回 true（生成空白 PDF）")
    void createPdfFromImages_emptyList() {
        Path output = tempDir.resolve("empty.pdf");

        boolean ok = pdfService.createPdfFromImages(output.toString(), List.of());

        assertThat(ok).isTrue();
        assertThat(Files.exists(output)).isTrue();
    }
}
