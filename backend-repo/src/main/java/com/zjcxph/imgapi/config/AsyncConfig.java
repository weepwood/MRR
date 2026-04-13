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
        
        // 核心线程数
        executor.setCorePoolSize(2);
        // 最大线程数
        executor.setMaxPoolSize(5);
        // 队列容量
        executor.setQueueCapacity(500);
        // 线程名前缀
        executor.setThreadNamePrefix("log-async-");
        // 拒绝策略: 由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }

    /**
     * 通用异步任务线程池
     * 用于其他异步业务逻辑
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
}
