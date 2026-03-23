package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.pojo.Log;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LogMapper {

    String BASE_COLUMNS = "id, " +
            "client_ip AS clientIp, " +
            "request_uri AS requestUri, " +
            "method, " +
            "user_agent AS userAgent, " +
            "access_time AS accessTime, " +
            "query_string AS queryString, " +
            "request_body AS requestBody, " +
            "response_status AS responseStatus, " +
            "execute_time AS executeTime, " +
            "referer";
    
    @Insert("INSERT INTO access_log (client_ip, request_uri, method, user_agent, access_time, query_string, request_body, response_status, execute_time, referer) " +
            "VALUES (#{clientIp}, #{requestUri}, #{method}, #{userAgent}, #{accessTime}, #{queryString}, #{requestBody}, #{responseStatus}, #{executeTime}, #{referer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Log log);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE client_ip = #{clientIp} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT " + BASE_COLUMNS + " FROM access_log WHERE request_uri = #{requestUri} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByRequestUri(@Param("requestUri") String requestUri, @Param("limit") int limit, @Param("offset") int offset);

    @Select({"<script>",
            "SELECT " + BASE_COLUMNS + " FROM access_log",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (client_ip LIKE '%' || #{keyword} || '%'",
            "      OR request_uri LIKE '%' || #{keyword} || '%'",
            "      OR query_string LIKE '%' || #{keyword} || '%'",
            "      OR user_agent LIKE '%' || #{keyword} || '%'",
            "      OR request_body LIKE '%' || #{keyword} || '%'",
            "      OR referer LIKE '%' || #{keyword} || '%')",
            "  </if>",
            "  <if test='clientIp != null and clientIp != \"\"'>",
            "    AND client_ip LIKE '%' || #{clientIp} || '%'",
            "  </if>",
            "  <if test='requestUri != null and requestUri != \"\"'>",
            "    AND request_uri LIKE '%' || #{requestUri} || '%'",
            "  </if>",
            "  <if test='method != null and method != \"\"'>",
            "    AND method = #{method}",
            "  </if>",
            "  <if test='responseStatus != null and responseStatus != \"\"'>",
            "    AND response_status LIKE #{responseStatus} || '%'",
            "  </if>",
            "  <if test='startTime != null and startTime != \"\"'>",
            "    AND datetime(access_time) &gt;= datetime(#{startTime})",
            "  </if>",
            "  <if test='endTime != null and endTime != \"\"'>",
            "    AND datetime(access_time) &lt;= datetime(#{endTime})",
            "  </if>",
            "</where>",
            "ORDER BY access_time DESC",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<Log> search(
            @Param("keyword") String keyword,
            @Param("clientIp") String clientIp,
            @Param("requestUri") String requestUri,
            @Param("method") String method,
            @Param("responseStatus") String responseStatus,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select({"<script>",
            "SELECT COUNT(*) FROM access_log",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (client_ip LIKE '%' || #{keyword} || '%'",
            "      OR request_uri LIKE '%' || #{keyword} || '%'",
            "      OR query_string LIKE '%' || #{keyword} || '%'",
            "      OR user_agent LIKE '%' || #{keyword} || '%'",
            "      OR request_body LIKE '%' || #{keyword} || '%'",
            "      OR referer LIKE '%' || #{keyword} || '%')",
            "  </if>",
            "  <if test='clientIp != null and clientIp != \"\"'>",
            "    AND client_ip LIKE '%' || #{clientIp} || '%'",
            "  </if>",
            "  <if test='requestUri != null and requestUri != \"\"'>",
            "    AND request_uri LIKE '%' || #{requestUri} || '%'",
            "  </if>",
            "  <if test='method != null and method != \"\"'>",
            "    AND method = #{method}",
            "  </if>",
            "  <if test='responseStatus != null and responseStatus != \"\"'>",
            "    AND response_status LIKE #{responseStatus} || '%'",
            "  </if>",
            "  <if test='startTime != null and startTime != \"\"'>",
            "    AND datetime(access_time) &gt;= datetime(#{startTime})",
            "  </if>",
            "  <if test='endTime != null and endTime != \"\"'>",
            "    AND datetime(access_time) &lt;= datetime(#{endTime})",
            "  </if>",
            "</where>",
            "</script>"})
    int countSearch(
            @Param("keyword") String keyword,
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
    
}
