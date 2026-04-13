package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.AsyncLogService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 异步日志服务实现
 * 使用@Async实现非阻塞日志写入,提升请求响应速度
 */
@Service
public class AsyncLogServiceImpl implements AsyncLogService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncLogServiceImpl.class);
    
    private final LogMapper logMapper;
    
    // 线程安全的批量缓冲区
    private final CopyOnWriteArrayList<Log> logBuffer = new CopyOnWriteArrayList<>();
    private static final int BATCH_SIZE = 50; // 批量插入阈值

    public AsyncLogServiceImpl(LogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 异步保存单条日志
     * 实际会先加入缓冲区,达到阈值后批量插入
     */
    @Override
    @Async("logAsyncExecutor")
    public void saveLogAsync(Log log) {
        try {
            logBuffer.add(log);
            
            // 当缓冲区达到阈值时,执行批量插入
            if (logBuffer.size() >= BATCH_SIZE) {
                flushBuffer();
            }
        } catch (Exception e) {
            logger.error("异步保存日志失败", e);
        }
    }

    /**
     * 批量异步保存日志
     */
    @Override
    @Async("logAsyncExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveLogsAsync(List<Log> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        
        try {
            for (Log log : logs) {
                logMapper.insert(log);
            }
            logger.debug("批量保存日志成功, 数量: {}", logs.size());
        } catch (Exception e) {
            logger.error("批量保存日志失败", e);
            throw e;
        }
    }

    /**
     * 刷新缓冲区,批量插入数据库
     */
    private void flushBuffer() {
        if (logBuffer.isEmpty()) {
            return;
        }
        
        List<Log> logsToInsert = new ArrayList<>(logBuffer);
        logBuffer.clear();
        
        try {
            for (Log log : logsToInsert) {
                logMapper.insert(log);
            }
            logger.debug("批量刷新日志缓冲区成功, 数量: {}", logsToInsert.size());
        } catch (Exception e) {
            logger.error("批量刷新日志缓冲区失败, 将逐条重试", e);
            // 失败时逐条重试,避免全部丢失
            retryInsertOneByOne(logsToInsert);
        }
    }

    /**
     * 逐条重试插入失败的日志
     */
    private void retryInsertOneByOne(List<Log> logs) {
        int successCount = 0;
        int failCount = 0;
        
        for (Log log : logs) {
            try {
                logMapper.insert(log);
                successCount++;
            } catch (Exception e) {
                failCount++;
                logger.warn("单条日志插入失败: {}", log.getRequestUri(), e);
            }
        }
        
        logger.info("日志重试插入完成, 成功: {}, 失败: {}", successCount, failCount);
    }

    /**
     * 应用关闭时刷新剩余日志
     */
    @PreDestroy
    public void destroy() {
        if (!logBuffer.isEmpty()) {
            logger.info("应用关闭,刷新剩余 {} 条日志", logBuffer.size());
            flushBuffer();
        }
    }
}
