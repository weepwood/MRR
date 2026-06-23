package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.Log;

import java.util.List;

public interface LogService {
    void saveLog(Log log);
    Log getLogById(Long id);
    List<Log> getAllLogs(int page, int size);
    List<Log> getLogsByClientIp(String clientIp, int page, int size);
    List<Log> getLogsByRequestUri(String requestUri, int page, int size);
    List<Log> searchLogs(String keyword, String username, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime, int page, int size);
    int getTotalLogCount();
    int getLogCountByClientIp(String clientIp);
    int getLogCountByRequestUri(String requestUri);
    int countSearchLogs(String keyword, String username, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime);
    List<Log> searchImageAuditLogs(String keyword, String username, String clientIp, String auditAction, String responseStatus, String startTime, String endTime, int page, int size);
    int countImageAuditLogs(String keyword, String username, String clientIp, String auditAction, String responseStatus, String startTime, String endTime);
}
