package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.migration")
public class MigrationProperties {

    /** Number of files claimed from PostgreSQL in one coordinator cycle. */
    private int claimBatchSize = 200;

    /** Number of concurrent OSS upload workers. */
    private int workerCount = 16;

    /** Maximum attempts before a file is marked as permanently failed. */
    private int maxAttempts = 6;

    /** Lease duration for claimed rows. Expired leases can be reclaimed safely. */
    private int leaseSeconds = 300;

    /** Base delay used by exponential retry backoff. */
    private int retryBaseSeconds = 10;

    /** Upper bound for retry backoff. */
    private int maxRetryDelaySeconds = 3600;

    /** Minimum interval between migration_job progress updates. */
    private int progressFlushSeconds = 3;

    /** Delay used when only future retry rows remain. */
    private int idlePollSeconds = 5;
}
