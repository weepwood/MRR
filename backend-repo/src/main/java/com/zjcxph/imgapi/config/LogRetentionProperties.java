package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.log-retention")
public class LogRetentionProperties {

    private boolean enabled = false;
    private String cron = "0 30 2 * * ?";
    private int retentionDays = 1095;
    private int batchSize = 5000;
    private int maxBatchesPerRun = 20;

}
