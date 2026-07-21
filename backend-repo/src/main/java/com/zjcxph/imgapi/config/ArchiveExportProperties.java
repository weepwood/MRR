package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "archive.export")
public class ArchiveExportProperties {

    private String tempDirectory = Path.of(
            System.getProperty("java.io.tmpdir"), "mrr-archive-exports").toString();
    private long maxTotalBytes = 20L * 1024 * 1024 * 1024;
    private long maxFileBytes = 10L * 1024 * 1024 * 1024;
    private Duration retention = Duration.ofHours(24);
    private Duration cleanupInterval = Duration.ofMinutes(15);
    private int workerCoreSize = 1;
    private int workerMaxSize = 2;
    private int workerQueueCapacity = 20;
    private int asyncItemThreshold = 500;
    private long asyncEstimatedBytesThreshold = 1024L * 1024 * 1024;
    private int asyncSourceCountThreshold = 2;
    private long fallbackBytesPerImage = 5L * 1024 * 1024;
}
