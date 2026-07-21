package com.zjcxph.imgapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ArchiveExportExecutorConfig {

    @Bean(name = "archiveExportExecutor")
    public Executor archiveExportExecutor(ArchiveExportProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, properties.getWorkerCoreSize()));
        executor.setMaxPoolSize(Math.max(
                Math.max(1, properties.getWorkerCoreSize()),
                properties.getWorkerMaxSize()));
        executor.setQueueCapacity(Math.max(1, properties.getWorkerQueueCapacity()));
        executor.setThreadNamePrefix("archive-export-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
