package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.pojo.Log;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LogMapper {
    
    @Insert("INSERT INTO access_log (client_ip, request_uri, method, user_agent, access_time, query_string, request_body, response_status, execute_time, referer) " +
            "VALUES (#{clientIp}, #{requestUri}, #{method}, #{userAgent}, #{accessTime}, #{queryString}, #{requestBody}, #{responseStatus}, #{executeTime}, #{referer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Log log);

    @Select("SELECT * FROM access_log ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM access_log WHERE client_ip = #{clientIp} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByClientIp(@Param("clientIp") String clientIp, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM access_log WHERE request_uri = #{requestUri} ORDER BY access_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Log> findByRequestUri(@Param("requestUri") String requestUri, @Param("limit") int limit, @Param("offset") int offset);
    

    @Select("SELECT COUNT(*) FROM access_log")
    int countAll();

    @Select("SELECT COUNT(*) FROM access_log WHERE client_ip = #{clientIp}")
    int countByClientIp(@Param("clientIp") String clientIp);

    @Select("SELECT COUNT(*) FROM access_log WHERE request_uri = #{requestUri}")
    int countByRequestUri(@Param("requestUri") String requestUri);
    
}