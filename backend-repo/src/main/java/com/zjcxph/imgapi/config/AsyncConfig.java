package com.zjcxph.imgapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 * 配置线程池用于异步日志写入和其他后台任务
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 日志异步写入线程池
     * 专门用于处理日志批量写入,避免阻塞主请求线程
     */
    @Bean("logAsyncExecutor")
    public Executor logAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("log-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务线程池
     */
    @Bean("taskAsyncExecutor")
    public Executor taskAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("task-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }

    /**
     * OSS 迁移是单实例长任务，使用独立单线程执行器，避免占满通用线程池。
     *
     * <p>数据库唯一索引仍是“单活动任务”的最终约束。拒绝策略使用 AbortPolicy，
     * 由业务层把提交失败明确写回任务状态，绝不回退到 HTTP 请求线程执行。</p>
     */
    @Bean("migrationTaskExecutor")
    public Executor migrationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("oss-migration-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(10);

        executor.initialize();
        return executor;
    }
}
