package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.LogService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LogServiceImpl implements LogService {

    private final LogMapper logMapper;

    public LogServiceImpl(LogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLog(Log log) {
        logMapper.insert(log);
    }

    @Override
    public Log getLogById(Long id) {
        return logMapper.findById(id);
    }

    @Override
    public List<Log> getAllLogs(int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return logMapper.findAll(size, offset);
    }

    @Override
    public List<Log> getLogsByClientIp(String clientIp, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return logMapper.findByClientIp(clientIp, size, offset);
    }

    @Override
    public List<Log> getLogsByRequestUri(String requestUri, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return logMapper.findByRequestUri(requestUri, size, offset);
    }

    @Override
    public List<Log> searchLogs(String keyword, String username, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return logMapper.search(keyword, username, clientIp, requestUri, method, responseStatus, startTime, endTime, size, offset);
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
    public int countSearchLogs(String keyword, String username, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime) {
        return logMapper.countSearch(keyword, username, clientIp, requestUri, method, responseStatus, startTime, endTime);
    }

    @Override
    public List<Log> searchImageAuditLogs(String keyword, String username, String clientIp, String auditAction, String responseStatus, String startTime, String endTime, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return logMapper.searchImageAudit(keyword, username, clientIp, auditAction, responseStatus, startTime, endTime, size, offset);
    }

    @Override
    public int countImageAuditLogs(String keyword, String username, String clientIp, String auditAction, String responseStatus, String startTime, String endTime) {
        return logMapper.countImageAudit(keyword, username, clientIp, auditAction, responseStatus, startTime, endTime);
    }
}
