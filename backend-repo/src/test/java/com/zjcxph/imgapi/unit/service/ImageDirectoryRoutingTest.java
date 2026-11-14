package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.SystemSettingService;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageDirectoryRoutingTest {
    private static final String KEY = "imageDirectoryRoutingRules";
    private final ImageProperties properties = properties();
    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final ImageUrlService service = new ImageUrlService(properties, mock(OssService.class), settings);
    private final SettingRegistry registry = new SettingRegistry(new LogRetentionProperties());

    @Test
    void exactDirectoryWinsRegardlessOfArrayOrderAndOriginalPathIsPreserved() {
        override("[{\"type\":\"month\",\"value\":\"2026.06\",\"node\":\"ba01\"},"
                + "{\"type\":\"exact\",\"value\":\"2026.06.05\",\"node\":\"ba02\"}]");
        assertThat(service.buildImageUrl(scan("2026.06.05"))).isEqualTo("http://ba02/2026.06/2026.06.05/42-123/page.jpg");
        assertThat(service.buildImageUrl(scan("2026.06.06"))).startsWith("http://ba01/");
    }

    @Test
    void emptyOverrideDisablesBuiltInRoutesButMissingOverridePreservesThem() {
        assertThat(service.buildImageUrl(scan("2026.06.05"))).startsWith("http://ba03/");
        override("[]");
        assertThat(service.buildImageUrl(scan("2026.06.05"))).startsWith("http://fallback/");
        assertThat(service.buildImageUrl(scan(null))).isEqualTo("http://default/42-123/page.jpg");
        override(" ");
        assertThat(service.buildImageUrl(scan("2026.06.05"))).startsWith("http://ba03/");
    }

    @Test
    void twoDigitYearDoesNotMatchFourDigitYear() {
        override("[{\"type\":\"month\",\"value\":\"24.04\",\"node\":\"ba02\"}]");
        assertThat(service.buildImageUrl(scan("24.04.07"))).startsWith("http://ba02/24.04/24.04.07/");
        assertThat(service.buildImageUrl(scan("2024.04.07"))).startsWith("http://fallback/");
    }

    @Test
    void batchUsesOneRoutingSnapshotInsteadOfOneQueryPerImage() {
        override("[]");
        assertThat(service.toDtoList(List.of(scan("2026.06.05"), scan("2026.06.06")))).hasSize(2);
        verify(settings, times(1)).getSetting(KEY);
    }

    @Test
    void brokenOrUnavailableConfigurationMustNotSilentlyChooseAnotherSource() {
        override("not-json");
        assertThatThrownBy(() -> service.buildImageUrl(scan("2026.06.05"))).isInstanceOf(BusinessException.class);
        when(settings.getSetting(KEY)).thenThrow(new IllegalStateException("synthetic database failure"));
        assertThatThrownBy(() -> service.buildImageUrl(scan("2026.06.05"))).isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("synthetic");
    }

    @Test
    void customRouteToMissingNodeIsRejectedInsteadOfFallingBack() {
        properties.setServerUrlBa01("");
        override("[{\"type\":\"exact\",\"value\":\"2026.06.05\",\"node\":\"ba01\"}]");
        assertThatThrownBy(() -> service.buildImageUrl(scan("2026.06.05"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void validOssDoesNotNeedLocalRoutingConfiguration() {
        when(settings.getSetting("imageSource")).thenReturn("oss");
        Scan scan = scan("2026.06.05");
        scan.setOssUrl("synthetic/object");
        assertThat(service.toDtoList(List.of(scan), ignored -> "https://oss.invalid/image").getFirst().getImg_url())
                .isEqualTo("https://oss.invalid/image");
        verify(settings, never()).getSetting(KEY);
    }

    @Test
    void emptyOssSignatureDoesNotRetryFailedLocalRoutingRead() {
        var oss = mock(OssService.class);
        var preferred = new ImageUrlService(properties, oss, settings);
        when(settings.getSetting("imageSource")).thenReturn("oss");
        when(settings.getSetting(KEY)).thenThrow(new IllegalStateException("synthetic")).thenReturn("[]");
        Scan scan = scan("2026.06.05");
        scan.setOssUrl("synthetic/object");
        when(oss.generatePresignedUrl("synthetic/object")).thenReturn("");
        assertThatThrownBy(() -> preferred.buildPreferredImageUrl(scan)).isInstanceOf(BusinessException.class);
        verify(settings, times(1)).getSetting(KEY);
    }

    @Test
    void nginxSourceRejectsBrokenRoutingBeforeOpeningAnyNetworkStream() {
        override("broken");
        var source = new com.zjcxph.imgapi.storage.NginxArchiveImageSource(service, properties,
                new com.zjcxph.imgapi.config.ArchiveImageSourceProperties());
        var image = new com.zjcxph.imgapi.entity.PathDO("2026.06.05", "page.jpg", "42", "123");
        assertThat(source.supports(image)).isFalse();
        assertThatThrownBy(() -> source.open(image)).isInstanceOf(java.io.IOException.class)
                .hasMessageNotContaining("broken");
    }

    @Test
    void duplicateJsonFieldsAndTrailingDocumentsAreRejected() {
        for (String raw : List.of("[] []", "[{\"type\":\"exact\",\"value\":\"2026.06.05\",\"node\":\"ba01\",\"node\":\"ba02\"}]")) {
            assertThatThrownBy(() -> registry.validate(KEY, raw)).isInstanceOf(BusinessException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[null]", "[1]", "[{}]",
            "[{\"type\":\"regex\",\"value\":\".*\",\"node\":\"ba01\"}]",
            "[{\"type\":\"exact\",\"value\":\"../2026.06.05\",\"node\":\"ba01\"}]",
            "[{\"type\":\"exact\",\"value\":\"2026.02.30\",\"node\":\"ba01\"}]",
            "[{\"type\":\"month\",\"value\":\"2026.13\",\"node\":\"ba01\"}]",
            "[{\"type\":\"month\",\"value\":\"2026.06\",\"node\":\"http://example.invalid\"}]",
            "[{\"type\":\"month\",\"value\":\"2026.06\",\"node\":\"ba01\",\"url\":\"secret\"}]",
            "[{\"type\":\"month\",\"value\":\"2026.06\",\"node\":\"ba01\"},{\"type\":\"month\",\"value\":\"2026.06\",\"node\":\"ba02\"}]"})
    void invalidRulesAreRejectedAtExistingSettingWriteBoundary(String value) {
        assertThatThrownBy(() -> registry.validate(KEY, value)).isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("secret").hasMessageNotContaining("example.invalid");
    }

    @Test
    void ruleCountAndPayloadAreBounded() {
        assertThatThrownBy(() -> registry.validate(KEY, "[" + "{},".repeat(200) + "{}]"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> registry.validate(KEY, " ".repeat(32769)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void schemaExposesSensitiveDefaultRulesForEditorAndAudit() {
        var definition = registry.find(KEY).orElseThrow();
        assertThat(definition.sensitive()).isTrue();
        assertThat(definition.defaultRawValue()).contains("2026.06.05", "24.04");
        assertThatCode(() -> registry.validate(KEY, definition.defaultRawValue())).doesNotThrowAnyException();
    }

    private void override(String json) { when(settings.getSetting(KEY)).thenReturn(json); }
    private static Scan scan(String folder) {
        Scan scan = new Scan();
        scan.setFolder(folder); scan.setFilename("page.jpg"); scan.setBrxh("42"); scan.setBah("123");
        return scan;
    }
    private static ImageProperties properties() {
        var p = new ImageProperties();
        p.setUrl("http://fallback"); p.setServerUrlDefault("http://default");
        p.setServerUrlBa01("http://ba01"); p.setServerUrlBa02("http://ba02"); p.setServerUrlBa03("http://ba03");
        return p;
    }
}
