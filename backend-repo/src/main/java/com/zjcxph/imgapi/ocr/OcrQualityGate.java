package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.List;

/** Evidence lives in an immutable operator-controlled local file, never in a web approval checkbox. */
@Component
public class OcrQualityGate {
    private final OcrRuntimeProperties properties;private final SystemSettingService settings;
    public OcrQualityGate(OcrRuntimeProperties properties,SystemSettingService settings) {this.properties=properties;this.settings=settings;}
    public boolean enabled() {return properties.isAutoApplyAllowed()&&"true".equalsIgnoreCase(settings.getSetting("classificationAutoApplyEnabled"));}
    public String checksum() {return properties.getValidationReportSha256();}
    public List<Integer> types(String rule,String model) {return properties.getAutoTypes().stream().filter(t->permits(rule,model,t)).sorted().toList();}
    public boolean permits(String rule,String model,int type) {
        if(!enabled()||!properties.getAutoTypes().contains(type)||type<1||type>15) return false;
        try {
            if(properties.getValidationReportSha256()==null||!properties.getValidationReportSha256().matches("[a-fA-F0-9]{64}")) return false;
            Path path=Path.of(properties.getValidationReport());
            if(!Files.isRegularFile(path)||Files.size(path)>65536) return false;
            byte[] bytes;
            try(var input=Files.newInputStream(path)) {bytes=input.readNBytes(65537);}
            if(bytes.length>65536||!DigestUtils.sha256Hex(bytes).equalsIgnoreCase(properties.getValidationReportSha256())) return false;
            var report=OcrProtocol.JSON.readTree(bytes);double cer=report.path("clearPrintedCer").asDouble(Double.NaN);
            var category=report.path("byType").path(String.valueOf(type));
            return report.path("schemaVersion").asInt()==1&&report.path("independent").asBoolean()
                    &&rule.equals(report.path("ruleVersion").asText())&&model.equals(report.path("modelChecksum").asText())
                    &&report.path("totalPages").asInt()>=2000&&report.path("autoCandidates").asInt()>=600
                    &&report.path("autoErrors").asInt(-1)==0&&report.path("clearPrintedPages").asInt()>=100
                    &&Double.isFinite(cer)&&cer>=0&&cer<=.05&&category.path("count").asInt()>=30&&category.path("errors").asInt(-1)==0;
        } catch(Exception failure) {return false;}
    }
}
