package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.pojo.BAHStatisticsDTO;
import com.zjcxph.imgapi.pojo.DateStatisticsDTO;
import com.zjcxph.imgapi.pojo.Statistics;

import java.util.List;
import java.util.Map;

public interface StatisticsService {

    // 查询所有统计数据
    List<Statistics> findAll();

    // 查询所有统计数据（带分页）
    List<Statistics> findAllWithPagination(int page, int size);
    List<Statistics> findWithConditionAndPagination(
            int page,
            int size,
            String keyword,
            String type,
            String startDate,
            String endDate,
            String sortBy,
            String sortOrder
    );

    // 获取总记录数
    Long getTotalCount();
    Long getTotalCountByCondition(String keyword, String type, String startDate, String endDate);

    // 根据病案号查询
    List<Statistics> findByBah(String bah);

    // 根据日期查询
    List<Statistics> findByDate(String date);

    // 统计每个病案号的记录数和总页数
    List<BAHStatisticsDTO> getBAHStatistics();

    // 统计每个日期的记录数和总页数
    List<DateStatisticsDTO> getDateStatistics();

    // 按条件统计（日期范围和类型）
    List<DateStatisticsDTO> getDateStatisticsByCondition(String startDate, String endDate, String type);

    // 获取总统计信息
    Map<String, Object> getTotalStatistics();

    // 获取不同病案号的数量
    Long getUniqueBAHCount();

    // 按类型统计
    List<Map<String, Object>> getTypeStatistics();
}
