package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "ocr.runtime")
public class OcrRuntimeProperties {
    private String tempDirectory = "./data/ocr-temp";
    private int maxWorkers = 1;
    private long maxInputBytes = 50L * 1024 * 1024;
    private Map<String, Profile> profiles = new LinkedHashMap<>();
    @Data public static class Profile {
        private String executable;
        private String script;
        private String modelDirectory;
        private String manifestSha256;
    }
}
