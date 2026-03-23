package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.pojo.Log;

import java.util.List;

public interface LogService {
    void saveLog(Log log);
    List<Log> getAllLogs(int page, int size);
    List<Log> getLogsByClientIp(String clientIp, int page, int size);
    List<Log> getLogsByRequestUri(String requestUri, int page, int size);
    int getTotalLogCount();
    int getLogCountByClientIp(String clientIp);
    int getLogCountByRequestUri(String requestUri);
}