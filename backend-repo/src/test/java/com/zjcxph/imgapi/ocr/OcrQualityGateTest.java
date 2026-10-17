package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrQualityGateTest {
    @TempDir Path root;
    @Test void requiresPinnedIndependentEvidenceAndExactRuleAndModelVersion() throws Exception {
        var properties=new OcrRuntimeProperties();var settings=mock(SystemSettingService.class);
        when(settings.getSetting("classificationAutoApplyEnabled")).thenReturn("true");
        var gate=new OcrQualityGate(properties,settings);
        assertThat(gate.permits("rules","a".repeat(64),3)).isFalse();
        properties.setAutoApplyAllowed(true);properties.setAutoTypes(Set.of(3));
        String report="{\"schemaVersion\":1,\"independent\":true,\"ruleVersion\":\"rules\",\"modelChecksum\":\""+"a".repeat(64)+"\",\"totalPages\":2000,\"autoCandidates\":600,\"autoErrors\":0,\"clearPrintedPages\":100,\"clearPrintedCer\":0.03,\"byType\":{\"3\":{\"count\":600,\"errors\":0}}}";
        Path file=root.resolve("validation.json");Files.writeString(file,report);
        properties.setValidationReport(file.toString());properties.setValidationReportSha256(DigestUtils.sha256Hex(report));
        assertThat(gate.permits("rules","a".repeat(64),3)).isTrue();
        assertThat(gate.permits("changed","a".repeat(64),3)).isFalse();
        assertThat(gate.permits("rules","b".repeat(64),3)).isFalse();
        assertThat(gate.permits("rules","a".repeat(64),11)).isFalse();
        Files.writeString(file,report.replace("\"autoErrors\":0","\"autoErrors\":1"));
        assertThat(gate.permits("rules","a".repeat(64),3)).isFalse();
    }
}
