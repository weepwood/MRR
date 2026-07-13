package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.BAHStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.DateStatisticsDTO;
import com.zjcxph.imgapi.entity.Statistics;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface StatisticsMapper {

    String BAH_SEARCH_EXPRESSION = "CASE WHEN bah ~ '^[0-9]+$' " +
            "THEN COALESCE(NULLIF(LTRIM(bah, '0'), ''), '0') ELSE bah END";
    String CANONICAL_BAH_EXPRESSION = "CASE WHEN bah ~ '^[0-9]{1,8}$' " +
            "THEN LPAD(bah, 8, '0') ELSE bah END";

    // 查询所有统计数据
    @Select("SELECT * FROM mr_statistics ORDER BY date")
    List<Statistics> findAll();

    // 查询所有统计数据（带分页）
    @Select("SELECT * FROM mr_statistics ORDER BY date LIMIT #{limit} OFFSET #{offset}")
    List<Statistics> findAllWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    // 动态条件查询（带分页和排序）- XML 实现
    List<Statistics> findWithConditionAndPagination(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyword") String keyword,
            @Param("bah") String bah,
            @Param("sjh") String sjh,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder
    );

    // 获取总记录数
    @Select("SELECT COUNT(*) FROM mr_statistics")
    Long getTotalCount();

    // 获取总记录数（根据条件）- XML 实现
    Long getTotalCountByCondition(
            @Param("keyword") String keyword,
            @Param("bah") String bah,
            @Param("sjh") String sjh,
            @Param("type") String type,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    // 根据病案号查询，兼容历史短值
    @Select("SELECT * FROM mr_statistics WHERE bah = #{normalizedBah} " +
            "OR " + BAH_SEARCH_EXPRESSION + " = #{searchCode} ORDER BY date")
    List<Statistics> findByBah(
            @Param("normalizedBah") String normalizedBah,
            @Param("searchCode") String searchCode
    );

    /**
     * 保留旧的一参数调用方式，内部自动兼容有无前导零的病案号。
     */
    default List<Statistics> findByBah(String bah) {
        return findByBah(
                MedicalRecordCodeUtils.normalizeOrEmpty(bah),
                MedicalRecordCodeUtils.toSearchTerm(bah)
        );
    }

    // 根据日期查询（使用 LIKE 模糊匹配，支持多种格式）
    @Select("SELECT * FROM mr_statistics WHERE date LIKE '%' || #{date} || '%' ORDER BY bah")
    List<Statistics> findByDate(@Param("date") String date);

    // 统计每个病案号的记录数和总页数，合并短值与补零值
    @Select("SELECT " + CANONICAL_BAH_EXPRESSION + " AS bah, " +
            "COUNT(*) AS recordCount, SUM(pages) AS totalPages " +
            "FROM mr_statistics " +
            "GROUP BY " + CANONICAL_BAH_EXPRESSION + " " +
            "ORDER BY bah")
    List<BAHStatisticsDTO> getBAHStatistics();

    // 统计每个日期的记录数和总页数
    @Select("SELECT date, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "GROUP BY date " +
            "ORDER BY date")
    List<DateStatisticsDTO> getDateStatistics();

    // 按日期范围和类型统计 - XML 实现
    List<DateStatisticsDTO> getDateStatisticsByCondition(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("type") String type);

    // 获取总记录数和总页数
    @Select("SELECT COUNT(*) AS \"totalRecords\", COALESCE(SUM(pages), 0) AS \"totalPages\" FROM mr_statistics")
    Map<String, Object> getTotalStatistics();

    // 获取规范化后不同病案号的数量
    @Select("SELECT COUNT(DISTINCT " + CANONICAL_BAH_EXPRESSION + ") " +
            "AS uniqueBAHCount FROM mr_statistics")
    Long getUniqueBAHCount();

    // 按类型统计
    @Select("SELECT type, COUNT(*) as recordCount, SUM(pages) as totalPages " +
            "FROM mr_statistics " +
            "GROUP BY type " +
            "ORDER BY type")
    List<Map<String, Object>> getTypeStatistics();
}
