package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.PdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("合法图片列表 — 成功生成 PDF 且文件非空")
    void createPdfFromImages_validImages() throws Exception {
        Path img1 = createRealJpeg(tempDir, "a.jpg");
        Path img2 = createRealJpeg(tempDir, "b.jpg");
        Path output = tempDir.resolve("result.pdf");

        pdfService.createPdfFromImages(
                output.toString(),
                List.of(img1.toString(), img2.toString()));

        assertThat(Files.exists(output)).isTrue();
        assertThat(Files.size(output)).isPositive();
        // PDF 文件头魔数
        assertThat(Files.readAllBytes(output)[0]).isEqualTo((byte) '%');
    }

    @Test
    @DisplayName("非法图片路径 — 抛出 BusinessException")
    void createPdfFromImages_invalidPath_throwsBusinessException() {
        Path output = tempDir.resolve("fail.pdf");

        assertThatThrownBy(() -> pdfService.createPdfFromImages(
                output.toString(),
                List.of(tempDir.resolve("not-exist.jpg").toString())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 创建失败");
    }

    @Test
    @DisplayName("空图片列表 — 成功生成空白 PDF")
    void createPdfFromImages_emptyList() {
        Path output = tempDir.resolve("empty.pdf");

        pdfService.createPdfFromImages(output.toString(), List.of());

        assertThat(Files.exists(output)).isTrue();
    }
}
