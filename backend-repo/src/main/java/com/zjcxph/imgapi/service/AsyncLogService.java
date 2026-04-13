package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.Log;

import java.util.List;

/**
 * 异步日志服务接口
 */
public interface AsyncLogService {
    
    /**
     * 异步保存日志(批量)
     * @param log 日志对象
     */
    void saveLogAsync(Log log);
    
    /**
     * 批量异步保存日志
     * @param logs 日志列表
     */
    void batchSaveLogsAsync(List<Log> logs);
}
