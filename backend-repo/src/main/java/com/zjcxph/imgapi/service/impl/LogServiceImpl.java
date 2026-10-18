package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.LogService;
import com.zjcxph.imgapi.utils.PaginationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        return privateLog(logMapper.findById(id));
    }

    @Override
    public List<Log> getAllLogs(int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return privateLogs(logMapper.findAll(size, offset));
    }

    @Override
    public List<Log> getLogsByClientIp(String clientIp, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return privateLogs(logMapper.findByClientIp(clientIp, size, offset));
    }

    @Override
    public List<Log> getLogsByRequestUri(String requestUri, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return privateLogs(logMapper.findByRequestUri(requestUri, size, offset));
    }

    @Override
    public List<Log> searchLogs(String keyword, String username, String clientIp, String requestUri, String method, String responseStatus, String startTime, String endTime, int page, int size) {
        PaginationUtils.validatePageParams(page, size);
        int offset = PaginationUtils.calculateOffset(page, size);
        return privateLogs(logMapper.search(keyword, username, clientIp, requestUri, method, responseStatus, startTime, endTime, size, offset));
    }

    @Override
    public List<Log> searchLogs(String keyword, String username, String clientIp, String requestUri, String method,
                                String responseStatus, String startTime, String endTime, int page, int size,
                                LocalDateTime cursorAccessTime, Long cursorId) {
        PaginationUtils.validatePageParams(page, size);
        if (cursorAccessTime == null || cursorId == null) {
            throw new IllegalArgumentException("游标时间与游标 ID 必须成对传入");
        }
        return privateLogs(logMapper.searchAfter(keyword, username, clientIp, requestUri, method, responseStatus,
                startTime, endTime, cursorAccessTime, cursorId, size));
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
        return privateLogs(logMapper.searchImageAudit(keyword, username, clientIp, auditAction, responseStatus, startTime, endTime, size, offset));
    }

    @Override
    public int countImageAuditLogs(String keyword, String username, String clientIp, String auditAction, String responseStatus, String startTime, String endTime) {
        return logMapper.countImageAudit(keyword, username, clientIp, auditAction, responseStatus, startTime, endTime);
    }

    @Override
    public ImageAuditAnalyticsDTO getImageAuditAnalytics(String keyword, String username, String clientIp,
                                                         String auditAction, String responseStatus,
                                                         String startTime, String endTime) {
        ImageAuditAnalyticsDTO analytics = logMapper.getImageAuditOverview(
                keyword, username, clientIp, auditAction, responseStatus, startTime, endTime);
        analytics.setTrend(logMapper.getImageAuditTrend(
                keyword, username, clientIp, auditAction, responseStatus, startTime, endTime));
        analytics.setActionDistribution(logMapper.getImageAuditActionDistribution(
                keyword, username, clientIp, auditAction, responseStatus, startTime, endTime));
        analytics.setTopUsers(logMapper.getTopImageAuditUsers(
                keyword, username, clientIp, auditAction, responseStatus, startTime, endTime));
        analytics.setTopTargets(logMapper.getTopImageAuditTargets(
                keyword, username, clientIp, auditAction, responseStatus, startTime, endTime));
        if (analytics.getTopTargets() != null) analytics.getTopTargets().forEach(target ->
                target.setLabel(com.zjcxph.imgapi.utils.AuditPayloadSanitizer.protectedTarget(target.getLabel())));
        return analytics;
    }

    @Override
    public int countOlderThan(LocalDateTime cutoff) {
        return logMapper.countOlderThan(cutoff);
    }

    @Override
    public List<Log> findOlderThan(LocalDateTime cutoff, int batchSize, int offset) {
        return privateLogs(logMapper.findOlderThan(cutoff, batchSize, offset));
    }
    private List<Log> privateLogs(List<Log> rows) {
        if (rows == null) return List.of();
        rows.forEach(this::privateLog);
        return rows;
    }

    private Log privateLog(Log log) {
        if (log == null) return null;
        log.setQueryString(com.zjcxph.imgapi.utils.AuditPayloadSanitizer.query(log.getQueryString()));
        log.setRequestBody(com.zjcxph.imgapi.utils.AuditPayloadSanitizer.json(log.getRequestBody()));
        log.setRequestUri(log.getEndpointTemplate() == null ? "UNKNOWN" : log.getEndpointTemplate());
        log.setReferer("[REFERER OMITTED]");
        log.setUserAgent("[USER AGENT OMITTED]");
        if (log.getAuditAction() == null || !log.getAuditAction().startsWith("SETTING_")) {
            log.setAuditTarget(com.zjcxph.imgapi.utils.AuditPayloadSanitizer.protectedTarget(log.getAuditTarget()));
        }
        log.setBah(null); log.setSjh(null); log.setPatientId(null);
        log.setErrorMessage(log.getErrorMessage() == null ? null : "[ERROR MESSAGE OMITTED]");
        return log;
    }

}
