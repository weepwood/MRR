package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.pojo.BAHStatisticsDTO;
import com.zjcxph.imgapi.pojo.DateStatisticsDTO;
import com.zjcxph.imgapi.pojo.Statistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    // 查询所有统计数据
    @Select("SELECT * FROM mr_statistics ORDER BY date")
    List<Statistics> findAll();

    // 查询所有统计数据（带分页）
    @Select("SELECT * FROM mr_statistics ORDER BY date LIMIT #{limit} OFFSET #{offset}")
    List<Statistics> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT * FROM mr_statistics " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (" +
            " bah LIKE '%' || #{keyword} || '%' " +
            " OR cid LIKE '%' || #{keyword} || '%' " +
            " OR openerno LIKE '%' || #{keyword} || '%' " +
            " OR date LIKE '%' || #{keyword} || '%' " +
            " OR type LIKE '%' || #{keyword} || '%' " +
            ")" +
            "</if>" +
            "<if test='type != null and type != \"\"'>" +
            " AND type = #{type}" +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            " AND REPLACE(date, '/', '-') &gt;= #{startDate}" +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            " AND REPLACE(date, '/', '-') &lt;= #{endDate}" +
            "</if>" +
            "</where>" +
            "ORDER BY " +
            "<choose>" +
            "<when test='sortBy == \"bah\"'>bah</when>" +
            "<when test='sortBy == \"cid\"'>cid</when>" +
            "<when test='sortBy == \"openerNo\"'>openerno</when>" +
            "<when test='sortBy == \"date\"'>REPLACE(date, '/', '-')</when>" +
            "<when test='sortBy == \"type\"'>type</when>" +
            "<when test='sortBy == \"pages\"'>pages</when>" +
            "<otherwise>REPLACE(date, '/', '-')</otherwise>" +
            "</choose>" +
            "<choose>" +
            "<when test='sortOrder == \"asc\"'> ASC </when>" +
            "<otherwise> DESC </otherwise>" +
            "</choose>" +
            ", bah ASC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Statistics> findWithConditionAndPagination(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder
    );

    // 获取总记录数
    @Select("SELECT COUNT(*) FROM mr_statistics")
    Long getTotalCount();

    @Select("<script>" +
            "SELECT COUNT(*) FROM mr_statistics " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (" +
            " bah LIKE '%' || #{keyword} || '%' " +
            " OR cid LIKE '%' || #{keyword} || '%' " +
            " OR openerno LIKE '%' || #{keyword} || '%' " +
            " OR date LIKE '%' || #{keyword} || '%' " +
            " OR type LIKE '%' || #{keyword} || '%' " +
            ")" +
            "</if>" +
            "<if test='type != null and type != \"\"'>" +
            " AND type = #{type}" +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            " AND REPLACE(date, '/', '-') &gt;= #{startDate}" +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            " AND REPLACE(date, '/', '-') &lt;= #{endDate}" +
            "</if>" +
            "</where>" +
            "</script>")
    Long getTotalCountByCondition(
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    // 根据病案号查询
    @Select("SELECT * FROM mr_statistics WHERE bah = #{bah} ORDER BY date")
    List<Statistics> findByBah(@Param("bah") String bah);

    // 根据日期查询（使用 LIKE 模糊匹配，支持多种格式）
    @Select("SELECT * FROM mr_statistics WHERE date LIKE '%' || #{date} || '%' ORDER BY bah")
    List<Statistics> findByDate(@Param("date") String date);

    // 统计每个病案号的记录数和总页数
    @Select("SELECT bah, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "GROUP BY bah " +
            "ORDER BY bah")
    List<BAHStatisticsDTO> getBAHStatistics();

    // 统计每个日期的记录数和总页数
    @Select("SELECT date, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "GROUP BY date " +
            "ORDER BY date")
    List<DateStatisticsDTO> getDateStatistics();

    // 按日期范围和类型统计
    @Select("<script>" +
            "SELECT date, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "<where>" +
            "<if test='startDate != null and startDate != \"\"'>" +
            " AND date &gt;= #{startDate}" +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            " AND date &lt;= #{endDate}" +
            "</if>" +
            "<if test='type != null and type != \"\"'>" +
            " AND type = #{type}" +
            "</if>" +
            "</where>" +
            "GROUP BY date " +
            "ORDER BY date" +
            "</script>")
    List<DateStatisticsDTO> getDateStatisticsByCondition(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("type") String type);

    // 获取总记录数和总页数
    @Select("SELECT COUNT(*) AS \"totalRecords\", COALESCE(SUM(pages), 0) AS \"totalPages\" FROM mr_statistics")
    Map<String, Object> getTotalStatistics();

    // 获取不同病案号的数量
    @Select("SELECT COUNT(DISTINCT bah) as uniqueBAHCount FROM mr_statistics")
    Long getUniqueBAHCount();

    // 按类型统计
    @Select("SELECT type, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "GROUP BY type " +
            "ORDER BY type")
    List<Map<String, Object>> getTypeStatistics();
}
