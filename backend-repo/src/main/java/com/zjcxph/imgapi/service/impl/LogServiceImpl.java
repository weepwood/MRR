package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.pojo.Log;
import com.zjcxph.imgapi.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogServiceImpl implements LogService {

    @Autowired
    private LogMapper logMapper;

    @Override
    public void saveLog(Log log) {
        logMapper.insert(log);
    }

    @Override
    public List<Log> getAllLogs(int page, int size) {
        int offset = (page - 1) * size;
        return logMapper.findAll(size, offset);
    }

    @Override
    public List<Log> getLogsByClientIp(String clientIp, int page, int size) {
        int offset = (page - 1) * size;
        return logMapper.findByClientIp(clientIp, size, offset);
    }

    @Override
    public List<Log> getLogsByRequestUri(String requestUri, int page, int size) {
        int offset = (page - 1) * size;
        return logMapper.findByRequestUri(requestUri, size, offset);
    }

    @Override
    public List<Log> searchLogs(String keyword, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime, int page, int size) {
        int offset = (page - 1) * size;
        return logMapper.search(keyword, clientIp, requestUri, method, responseStatus, startTime, endTime, size, offset);
    }

    @Override
    public int getTotalLogCount() {
        return logMapper.countAll();
    }

    @Override
    public int getLogCountByClientIp(String clientIp) {
        return logMapper.countByClientIp(clientIp);
    }

    @Override
    public int getLogCountByRequestUri(String requestUri) {
        return logMapper.countByRequestUri(requestUri);
    }

    @Override
    public int countSearchLogs(String keyword, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime) {
        return logMapper.countSearch(keyword, clientIp, requestUri, method, responseStatus, startTime, endTime);
    }
}
