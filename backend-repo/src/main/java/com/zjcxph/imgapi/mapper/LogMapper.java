package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditCountDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditTrendDTO;
import com.zjcxph.imgapi.entity.Log;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

public interface LogMapper {

    String BASE_COLUMNS = "id, " +
            "username, " +
            "client_ip AS clientIp, " +
            "request_uri AS requestUri, " +
            "method, " +
            "user_agent AS userAgent, " +
            "access_time AS accessTime, " +
            "query_string AS queryString, " +
            "request_body AS requestBody, " +
            "response_status AS responseStatus, " +
            "execute_time AS executeTime, " +
            "referer, " +
            "audit_action AS auditAction, " +
            "audit_target AS auditTarget, " +
            "audit_description AS auditDescription";
    
    @Insert("INSERT INTO access_log (username, client_ip, request_uri, method, user_agent, access_time, query_string, request_body, response_status, execute_time, referer, audit_action, audit_target, audit_description) " +
            "VALUES (#{username}, #{clientIp}, #{requestUri}, #{method}, #{userAgent}, #{accessTime}, #{queryString}, #{requestBody}, #{responseStatus}, #{executeTime}, #{referer}, #{auditAction}, #{auditTarget}, #{auditDescription})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Log log);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE client_ip = #{clientIp} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE request_uri = #{requestUri} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByRequestUri(@Param("requestUri") String requestUri, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE id = #{id}")
    Log findById(@Param("id") Long id);

    // 删除指定时间之前的日志（分批删除）- XML 实现
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    // 统计指定时间之前的日志数量 - XML 实现
    int countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    // 查询指定时间之前的日志（带分页）- XML 实现
    List<Log> findOlderThan(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    // 动态搜索日志 - XML 实现
    List<Log> search(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("requestUri") String requestUri,
            @Param("method") String method,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<Log> searchAfter(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("requestUri") String requestUri,
            @Param("method") String method,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("cursorAccessTime") LocalDateTime cursorAccessTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    // 敏感病案图片访问审计搜索 - XML 实现
    List<Log> searchImageAudit(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    // 敏感病案图片访问审计总数 - XML 实现
    int countImageAudit(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    ImageAuditAnalyticsDTO getImageAuditOverview(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    List<ImageAuditTrendDTO> getImageAuditTrend(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    List<ImageAuditCountDTO> getImageAuditActionDistribution(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    List<ImageAuditCountDTO> getTopImageAuditUsers(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("auditAction") String auditAction,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    // 动态搜索日志总数 - XML 实现
    int countSearch(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("clientIp") String clientIp,
            @Param("requestUri") String requestUri,
            @Param("method") String method,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime
    );

    @Select("SELECT COUNT(*) FROM access_log")
    int countAll();

    @Select("SELECT COUNT(*) FROM access_log WHERE client_ip = #{clientIp}")
    int countByClientIp(@Param("clientIp") String clientIp);

    @Select("SELECT COUNT(*) FROM access_log WHERE request_uri = #{requestUri}")
    int countByRequestUri(@Param("requestUri") String requestUri);

    @Insert({"<script>",
             "INSERT INTO access_log (username, client_ip, request_uri, method, user_agent, access_time, ",
             "query_string, request_body, response_status, execute_time, referer, ",
             "audit_action, audit_target, audit_description) VALUES ",
             "<foreach item='log' collection='list' separator=','>",
             "(#{log.username}, #{log.clientIp}, #{log.requestUri}, #{log.method}, #{log.userAgent}, ",
             "#{log.accessTime}, #{log.queryString}, #{log.requestBody}, #{log.responseStatus}, ",
             "#{log.executeTime}, #{log.referer}, #{log.auditAction}, #{log.auditTarget}, #{log.auditDescription})",
             "</foreach>",
             "</script>"})
    int batchInsert(@Param("list") List<Log> logs);
    
}
