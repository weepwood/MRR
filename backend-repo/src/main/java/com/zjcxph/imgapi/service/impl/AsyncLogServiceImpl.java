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
import java.util.LinkedList;
import java.util.List;

@Service
public class AsyncLogServiceImpl implements AsyncLogService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncLogServiceImpl.class);

    private final LogMapper logMapper;

    private final List<Log> logBuffer = new LinkedList<>();
    private static final int BATCH_SIZE = 50;
    private final Object bufferLock = new Object();

    public AsyncLogServiceImpl(LogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Override
    @Async("logAsyncExecutor")
    public void saveLogAsync(Log log) {
        try {
            List<Log> toFlush = null;
            synchronized (bufferLock) {
                logBuffer.add(log);
                if (logBuffer.size() >= BATCH_SIZE) {
                    toFlush = new ArrayList<>(logBuffer);
                    logBuffer.clear();
                }
            }
            if (toFlush != null) {
                batchInsertLogs(toFlush);
            }
        } catch (Exception e) {
            logger.error("异步保存日志失败", e);
        }
    }

    @Override
    @Async("logAsyncExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveLogsAsync(List<Log> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        try {
            logMapper.batchInsert(logs);
            logger.debug("批量保存日志成功, 数量: {}", logs.size());
        } catch (Exception e) {
            logger.error("批量保存日志失败", e);
            throw e;
        }
    }

    private void batchInsertLogs(List<Log> logs) {
        try {
            logMapper.batchInsert(logs);
            logger.debug("批量刷新日志缓冲区成功, 数量: {}", logs.size());
        } catch (Exception e) {
            logger.error("批量刷新日志缓冲区失败, 将逐条重试", e);
            retryInsertOneByOne(logs);
        }
    }

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

    @PreDestroy
    public void destroy() {
        List<Log> remaining;
        synchronized (bufferLock) {
            if (logBuffer.isEmpty()) {
                return;
            }
            remaining = new ArrayList<>(logBuffer);
            logBuffer.clear();
        }
        logger.info("应用关闭,刷新剩余 {} 条日志", remaining.size());
        batchInsertLogs(remaining);
    }
}
