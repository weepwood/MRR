package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.ImageAuditAnalyticsDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditCountDTO;
import com.zjcxph.imgapi.dto.resp.ImageAuditTrendDTO;
import com.zjcxph.imgapi.entity.Log;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

public interface LogMapper {
    String BASE_COLUMNS = "id, request_id AS requestId, username, client_ip AS clientIp, request_uri AS requestUri, endpoint_template AS endpointTemplate, method, user_agent AS userAgent, access_time AS accessTime, query_string AS queryString, request_body AS requestBody, response_status AS responseStatus, execute_time AS executeTime, referer, error_message AS errorMessage, audit_action AS auditAction, audit_target AS auditTarget, audit_description AS auditDescription";

    @Insert("INSERT INTO access_log (request_id, username, client_ip, request_uri, endpoint_template, method, user_agent, access_time, query_string, request_body, response_status, execute_time, referer, error_message, audit_action, audit_target, audit_description) VALUES (#{requestId}, #{username}, #{clientIp}, #{requestUri}, #{endpointTemplate}, #{method}, #{userAgent}, #{accessTime}, #{queryString}, #{requestBody}, #{responseStatus}, #{executeTime}, #{referer}, #{errorMessage}, #{auditAction}, #{auditTarget}, #{auditDescription})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Log log);

    // Match the immutable request identity and timestamp, not just an arbitrary unique-key failure.
    // Works on both the ordinary and partitioned access_log relation.
    @Select("""
            SELECT EXISTS (SELECT 1 FROM access_log WHERE request_id=#{requestId}
                AND access_time IS NOT DISTINCT FROM CAST(#{accessTime,jdbcType=TIMESTAMP} AS timestamp)
                AND username IS NOT DISTINCT FROM #{username,jdbcType=VARCHAR}
                AND method IS NOT DISTINCT FROM #{method,jdbcType=VARCHAR}
                AND request_uri IS NOT DISTINCT FROM #{requestUri,jdbcType=VARCHAR}
                AND response_status IS NOT DISTINCT FROM #{responseStatus,jdbcType=VARCHAR}
                AND audit_action IS NOT DISTINCT FROM #{auditAction,jdbcType=VARCHAR}
                AND audit_target IS NOT DISTINCT FROM #{auditTarget,jdbcType=VARCHAR})
            """)
    @Options(timeout = 3, useCache = false)
    boolean containsAudit(Log log);

    @Select("SELECT count(*) FROM access_log WHERE request_id=#{requestId}")
    @Options(timeout=3, useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    long countRequestIdentity(@Param("requestId") String requestId);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM access_log WHERE request_id=#{requestId}
                AND access_time IS NOT DISTINCT FROM CAST(#{accessTime,jdbcType=TIMESTAMP} AS timestamp)
                AND username IS NOT DISTINCT FROM #{username,jdbcType=VARCHAR}
                AND client_ip IS NOT DISTINCT FROM #{clientIp,jdbcType=VARCHAR}
                AND request_uri IS NOT DISTINCT FROM #{requestUri,jdbcType=VARCHAR}
                AND endpoint_template IS NOT DISTINCT FROM #{endpointTemplate,jdbcType=VARCHAR}
                AND method IS NOT DISTINCT FROM #{method,jdbcType=VARCHAR}
                AND user_agent IS NOT DISTINCT FROM #{userAgent,jdbcType=VARCHAR}
                AND query_string IS NOT DISTINCT FROM #{queryString,jdbcType=VARCHAR}
                AND request_body IS NOT DISTINCT FROM #{requestBody,jdbcType=VARCHAR}
                AND response_status IS NOT DISTINCT FROM #{responseStatus,jdbcType=VARCHAR}
                AND execute_time IS NOT DISTINCT FROM #{executeTime,jdbcType=BIGINT}
                AND referer IS NOT DISTINCT FROM #{referer,jdbcType=VARCHAR}
                AND error_message IS NOT DISTINCT FROM #{errorMessage,jdbcType=VARCHAR}
                AND audit_action IS NOT DISTINCT FROM #{auditAction,jdbcType=VARCHAR}
                AND audit_target IS NOT DISTINCT FROM #{auditTarget,jdbcType=VARCHAR}
                AND audit_description IS NOT DISTINCT FROM #{auditDescription,jdbcType=VARCHAR})
            """)
    @Options(timeout=3, useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    boolean matchesDurableAudit(Log log);

    @Select({"<script>",
            "WITH incoming(request_id,username,client_ip,request_uri,endpoint_template,method,user_agent,access_time,query_string,request_body,response_status,execute_time,referer,error_message,audit_action,audit_target,audit_description) AS (VALUES",
            "<foreach item='row' collection='rows' separator=','>",
            "(CAST(#{row.requestId,jdbcType=VARCHAR} AS text), CAST(#{row.username,jdbcType=VARCHAR} AS text), CAST(#{row.clientIp,jdbcType=VARCHAR} AS text), CAST(#{row.requestUri,jdbcType=VARCHAR} AS text), CAST(#{row.endpointTemplate,jdbcType=VARCHAR} AS text), CAST(#{row.method,jdbcType=VARCHAR} AS text), CAST(#{row.userAgent,jdbcType=VARCHAR} AS text), CAST(#{row.accessTime,jdbcType=TIMESTAMP} AS timestamp), CAST(#{row.queryString,jdbcType=VARCHAR} AS text), CAST(#{row.requestBody,jdbcType=VARCHAR} AS text), CAST(#{row.responseStatus,jdbcType=VARCHAR} AS text), CAST(#{row.executeTime,jdbcType=BIGINT} AS bigint), CAST(#{row.referer,jdbcType=VARCHAR} AS text), CAST(#{row.errorMessage,jdbcType=VARCHAR} AS text), CAST(#{row.auditAction,jdbcType=VARCHAR} AS text), CAST(#{row.auditTarget,jdbcType=VARCHAR} AS text), CAST(#{row.auditDescription,jdbcType=VARCHAR} AS text))",
            "</foreach>", ")",
            "SELECT i.request_id, count(l.id) AS existing_count,",
            "count(l.id) FILTER (WHERE ROW(l.username, l.client_ip, l.request_uri, l.endpoint_template, l.method, l.user_agent, l.access_time, l.query_string, l.request_body, l.response_status, l.execute_time, l.referer, l.error_message, l.audit_action, l.audit_target, l.audit_description) IS NOT DISTINCT FROM ROW(i.username, i.client_ip, i.request_uri, i.endpoint_template, i.method, i.user_agent, i.access_time, i.query_string, i.request_body, i.response_status, i.execute_time, i.referer, i.error_message, i.audit_action, i.audit_target, i.audit_description)) AS matched_count",
            "FROM incoming i LEFT JOIN access_log l ON l.request_id=i.request_id GROUP BY i.request_id",
            "</script>"})
    @Options(timeout=3, useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<java.util.Map<String,Object>> reconcileAuditBatch(@Param("rows") List<Log> rows);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE client_ip = #{clientIp} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE request_uri = #{requestUri} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByRequestUri(@Param("requestUri") String requestUri, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE id = #{id}")
    Log findById(@Param("id") Long id);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
    int countOlderThan(@Param("cutoff") LocalDateTime cutoff);
    List<Log> findOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit, @Param("offset") int offset);

    List<Log> search(@Param("keyword") String keyword, @Param("username") String username, @Param("clientIp") String clientIp,
                     @Param("requestUri") String requestUri, @Param("method") String method, @Param("responseStatus") String responseStatus,
                     @Param("startTime") String startTime, @Param("endTime") String endTime, @Param("limit") int limit, @Param("offset") int offset);

    List<Log> searchAfter(@Param("keyword") String keyword, @Param("username") String username, @Param("clientIp") String clientIp,
                          @Param("requestUri") String requestUri, @Param("method") String method, @Param("responseStatus") String responseStatus,
                          @Param("startTime") String startTime, @Param("endTime") String endTime,
                          @Param("cursorAccessTime") LocalDateTime cursorAccessTime, @Param("cursorId") Long cursorId, @Param("limit") int limit);

    List<Log> searchImageAudit(@Param("keyword") String keyword, @Param("username") String username, @Param("clientIp") String clientIp,
                               @Param("auditAction") String auditAction, @Param("responseStatus") String responseStatus,
                               @Param("startTime") String startTime, @Param("endTime") String endTime, @Param("limit") int limit, @Param("offset") int offset);

    int countImageAudit(@Param("keyword") String keyword, @Param("username") String username, @Param("clientIp") String clientIp,
                        @Param("auditAction") String auditAction, @Param("responseStatus") String responseStatus,
                        @Param("startTime") String startTime, @Param("endTime") String endTime);

    ImageAuditAnalyticsDTO getImageAuditOverview(@Param("keyword") String keyword, @Param("username") String username,
                                                  @Param("clientIp") String clientIp, @Param("auditAction") String auditAction,
                                                  @Param("responseStatus") String responseStatus, @Param("startTime") String startTime,
                                                  @Param("endTime") String endTime);

    List<ImageAuditTrendDTO> getImageAuditTrend(@Param("keyword") String keyword, @Param("username") String username,
                                                 @Param("clientIp") String clientIp, @Param("auditAction") String auditAction,
                                                 @Param("responseStatus") String responseStatus, @Param("startTime") String startTime,
                                                 @Param("endTime") String endTime);

    List<ImageAuditCountDTO> getImageAuditActionDistribution(@Param("keyword") String keyword, @Param("username") String username,
                                                              @Param("clientIp") String clientIp, @Param("auditAction") String auditAction,
                                                              @Param("responseStatus") String responseStatus, @Param("startTime") String startTime,
                                                              @Param("endTime") String endTime);

    List<ImageAuditCountDTO> getTopImageAuditUsers(@Param("keyword") String keyword, @Param("username") String username,
                                                   @Param("clientIp") String clientIp, @Param("auditAction") String auditAction,
                                                   @Param("responseStatus") String responseStatus, @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime);

    List<ImageAuditCountDTO> getTopImageAuditTargets(@Param("keyword") String keyword, @Param("username") String username,
                                                     @Param("clientIp") String clientIp, @Param("auditAction") String auditAction,
                                                     @Param("responseStatus") String responseStatus, @Param("startTime") String startTime,
                                                     @Param("endTime") String endTime);

    int countSearch(@Param("keyword") String keyword, @Param("username") String username, @Param("clientIp") String clientIp,
                    @Param("requestUri") String requestUri, @Param("method") String method, @Param("responseStatus") String responseStatus,
                    @Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("SELECT COUNT(*) FROM access_log")
    int countAll();

    @Select("SELECT COUNT(*) FROM access_log WHERE client_ip = #{clientIp}")
    int countByClientIp(@Param("clientIp") String clientIp);

    @Select("SELECT COUNT(*) FROM access_log WHERE request_uri = #{requestUri}")
    int countByRequestUri(@Param("requestUri") String requestUri);

    @Insert({"<script>",
            "INSERT INTO access_log (request_id, username, client_ip, request_uri, endpoint_template, method, user_agent, access_time, query_string, request_body, response_status, execute_time, referer, error_message, audit_action, audit_target, audit_description) VALUES ",
            "<foreach item='log' collection='list' separator=','>",
            "(#{log.requestId}, #{log.username}, #{log.clientIp}, #{log.requestUri}, #{log.endpointTemplate}, #{log.method}, #{log.userAgent}, #{log.accessTime}, #{log.queryString}, #{log.requestBody}, #{log.responseStatus}, #{log.executeTime}, #{log.referer}, #{log.errorMessage}, #{log.auditAction}, #{log.auditTarget}, #{log.auditDescription})",
            "</foreach>", "</script>"})
    int batchInsert(@Param("list") List<Log> logs);
}
