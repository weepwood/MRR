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

    @Select({"<script>",
            "SELECT audit_target AS label, COUNT(*) AS count FROM access_log WHERE audit_action IN ('LIST','DOWNLOAD','VIEW_IMAGE','VIEW_OSS_IMAGE') AND NULLIF(TRIM(audit_target), '') IS NOT NULL",
            "<if test='keyword != null and keyword != \"\"'> AND (COALESCE(username,'') || CHR(1) || COALESCE(client_ip,'') || CHR(1) || COALESCE(request_uri,'') || CHR(1) || COALESCE(query_string,'') || CHR(1) || COALESCE(audit_target,'')) LIKE '%' || #{keyword} || '%'</if>",
            "<if test='username != null and username != \"\"'> AND username LIKE '%' || #{username} || '%'</if>",
            "<if test='clientIp != null and clientIp != \"\"'> AND client_ip LIKE '%' || #{clientIp} || '%'</if>",
            "<if test='auditAction != null and auditAction != \"\"'> AND audit_action = #{auditAction}</if>",
            "<if test='responseStatus != null and responseStatus != \"\"'> AND response_status LIKE #{responseStatus} || '%'</if>",
            "<if test='startTime != null and startTime != \"\"'> AND access_time &gt;= CAST(#{startTime} AS timestamp)</if>",
            "<if test='endTime != null and endTime != \"\"'> AND access_time &lt;= CAST(#{endTime} AS timestamp)</if>",
            "GROUP BY audit_target ORDER BY COUNT(*) DESC, audit_target ASC LIMIT 10",
            "</script>"})
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
