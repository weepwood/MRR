package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.StatisticsMapper;
import com.zjcxph.imgapi.pojo.BAHStatisticsDTO;
import com.zjcxph.imgapi.pojo.DateStatisticsDTO;
import com.zjcxph.imgapi.pojo.Statistics;
import com.zjcxph.imgapi.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsMapper statisticsMapper;

    @Autowired
    public StatisticsServiceImpl(StatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    @Override
    public List<Statistics> findAll() {
        return statisticsMapper.findAll();
    }

    @Override
    public List<Statistics> findAllWithPagination(int page, int size) {
        int offset = (page - 1) * size;
        return statisticsMapper.findAllWithPagination(offset, size);
    }

    @Override
    public List<Statistics> findWithConditionAndPagination(int page, int size, String keyword, String type, String startDate, String endDate) {
        int offset = (page - 1) * size;
        return statisticsMapper.findWithConditionAndPagination(offset, size, keyword, type, startDate, endDate);
    }

    @Override
    public Long getTotalCount() {
        return statisticsMapper.getTotalCount();
    }

    @Override
    public Long getTotalCountByCondition(String keyword, String type, String startDate, String endDate) {
        return statisticsMapper.getTotalCountByCondition(keyword, type, startDate, endDate);
    }

    @Override
    public List<Statistics> findByBah(String bah) {
        return statisticsMapper.findByBah(bah);
    }

    @Override
    public List<Statistics> findByDate(String date) {
        return statisticsMapper.findByDate(date);
    }

    @Override
    public List<BAHStatisticsDTO> getBAHStatistics() {
        return statisticsMapper.getBAHStatistics();
    }

    @Override
    public List<DateStatisticsDTO> getDateStatistics() {
        return statisticsMapper.getDateStatistics();
    }

    @Override
    public List<DateStatisticsDTO> getDateStatisticsByCondition(String startDate, String endDate, String type) {
        return statisticsMapper.getDateStatisticsByCondition(startDate, endDate, type);
    }

    @Override
    public Map<String, Object> getTotalStatistics() {
        return statisticsMapper.getTotalStatistics();
    }

    @Override
    public Long getUniqueBAHCount() {
        return statisticsMapper.getUniqueBAHCount();
    }

    @Override
    public List<Map<String, Object>> getTypeStatistics() {
        return statisticsMapper.getTypeStatistics();
    }
}
