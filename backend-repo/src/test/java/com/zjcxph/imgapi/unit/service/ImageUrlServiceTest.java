package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ImageUrlService 图片 URL 构建服务测试")
@ExtendWith(MockitoExtension.class)
class ImageUrlServiceTest {

    @Mock
    private OssService ossService;

    private ImageUrlService service;
    private Method determineImageUrlMethod;

    @BeforeEach
    void setUp() throws Exception {
        ImageProperties props = new ImageProperties();
        props.setServerUrlDefault("http://default");
        props.setServerUrlBa03("http://ba03");
        props.setServerUrlBa02("http://ba02");
        props.setServerUrlBa01("http://ba01");
        props.setUrl("http://url-default");
        service = new ImageUrlService(props, ossService);

        determineImageUrlMethod = ImageUrlService.class.getDeclaredMethod("determineImageUrl", String.class);
        determineImageUrlMethod.setAccessible(true);
    }

    @Test
    @DisplayName("normalizeCode — 不足 8 位左侧补零")
    void normalizeCode_shouldPadTo8() {
        assertThat(ImageUrlService.normalizeCode("123")).isEqualTo("00000123");
        assertThat(ImageUrlService.normalizeCode("12345678")).isEqualTo("12345678");
        assertThat(ImageUrlService.normalizeCode(null)).isEmpty();
        assertThat(ImageUrlService.normalizeCode("")).isEmpty();
    }

    @Test
    @DisplayName("normalizeCode — 非纯数字不补零")
    void normalizeCode_nonNumericShouldNotPad() {
        assertThat(ImageUrlService.normalizeCode("ABC")).isEqualTo("ABC");
    }

    @Test
    @DisplayName("extractYearMonth — 从日期文件夹提取年月")
    void extractYearMonth_shouldExtract() {
        assertThat(ImageUrlService.extractYearMonth("2026.06.05")).isEqualTo("2026.06");
        assertThat(ImageUrlService.extractYearMonth("2025.08")).isEqualTo("2025.08");
    }

    @Test
    @DisplayName("extractYearMonth — null 抛异常")
    void extractYearMonth_nullShouldThrow() {
        assertThatThrownBy(() -> ImageUrlService.extractYearMonth(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("extractYearMonth — 格式错误抛异常")
    void extractYearMonth_invalidFormatShouldThrow() {
        assertThatThrownBy(() -> ImageUrlService.extractYearMonth("nodot"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("determineImageUrl — null 或空白返回默认")
    void determineImageUrl_nullOrBlank_returnsDefault() throws Exception {
        assertThat(determineImageUrlMethod.invoke(service, (String) null)).isEqualTo("http://default");
        assertThat(determineImageUrlMethod.invoke(service, "")).isEqualTo("http://default");
        assertThat(determineImageUrlMethod.invoke(service, "   ")).isEqualTo("http://default");
    }

    @Test
    @DisplayName("determineImageUrl — 精确匹配 baImg03")
    void determineImageUrl_exactMatchReturnsBa03() throws Exception {
        assertThat(determineImageUrlMethod.invoke(service, "2026.06.05")).isEqualTo("http://ba03");
    }

    @Test
    @DisplayName("determineImageUrl — 按年月匹配 baImg02")
    void determineImageUrl_yearMonthMatchReturnsBa02() throws Exception {
        assertThat(determineImageUrlMethod.invoke(service, "2026.01.15")).isEqualTo("http://ba02");
    }

    @Test
    @DisplayName("determineImageUrl — 无匹配时回退默认")
    void determineImageUrl_noMatchFallsBackToDefault() throws Exception {
        assertThat(determineImageUrlMethod.invoke(service, "2099.01.01")).isEqualTo("http://url-default");
    }
}
