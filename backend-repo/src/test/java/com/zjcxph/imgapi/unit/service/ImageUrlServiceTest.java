package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ImageUrlService 图片 URL 构建服务测试")
@ExtendWith(MockitoExtension.class)
class ImageUrlServiceTest {

    @Mock
    private OssService ossService;

    @Mock
    private SystemSettingService systemSettingService;

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
        service = new ImageUrlService(props, ossService, systemSettingService);

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

    @Test
    @DisplayName("图片来源未配置时默认使用本地")
    void preferredUrl_defaultsToLocal() {
        Scan scan = sampleScan();

        assertThat(service.getEffectiveImageSource()).isEqualTo(ImageUrlService.IMAGE_SOURCE_LOCAL);
        assertThat(service.buildPreferredImageUrl(scan))
                .isEqualTo("http://url-default/25.03/25.03.15/605746-00789508/page.jpg");
        verifyNoInteractions(ossService);
    }

    @Test
    @DisplayName("OSS 模式使用签名地址")
    void preferredUrl_usesOssWhenConfigured() {
        Scan scan = sampleScan();
        when(systemSettingService.getSetting(ImageUrlService.IMAGE_SOURCE_SETTING_KEY)).thenReturn("oss");
        when(ossService.generatePresignedUrl("archive/page.jpg")).thenReturn("https://oss/signed");

        assertThat(service.buildPreferredImageUrl(scan)).isEqualTo("https://oss/signed");
    }

    @Test
    @DisplayName("OSS 模式下未迁移记录回退本地")
    void preferredUrl_fallsBackToLocalWhenOssKeyMissing() {
        Scan scan = sampleScan();
        scan.setOssUrl(null);
        when(systemSettingService.getSetting(ImageUrlService.IMAGE_SOURCE_SETTING_KEY)).thenReturn("oss");

        assertThat(service.buildPreferredImageUrl(scan))
                .isEqualTo("http://url-default/25.03/25.03.15/605746-00789508/page.jpg");
        verifyNoInteractions(ossService);
    }

    @Test
    @DisplayName("本地模式 DTO 不生成 OSS 签名")
    void dtoList_localModeDoesNotGenerateOssUrl() {
        BAHDataResponseDTO dto = service.toDtoList(List.of(sampleScan())).getFirst();

        assertThat(dto.getImg_url())
                .isEqualTo("http://url-default/25.03/25.03.15/605746-00789508/page.jpg");
        assertThat(dto.getOssUrl()).isNull();
        verifyNoInteractions(ossService);
    }

    @Test
    @DisplayName("非法图片来源配置回退本地")
    void invalidImageSourceFallsBackToLocal() {
        when(systemSettingService.getSetting(ImageUrlService.IMAGE_SOURCE_SETTING_KEY)).thenReturn("unknown");

        assertThat(service.getEffectiveImageSource()).isEqualTo(ImageUrlService.IMAGE_SOURCE_LOCAL);
    }

    private Scan sampleScan() {
        Scan scan = new Scan();
        scan.setId(1);
        scan.setBah("00789508");
        scan.setBrxh("605746");
        scan.setFolder("25.03.15");
        scan.setFilename("page.jpg");
        scan.setOssUrl("archive/page.jpg");
        return scan;
    }
}
