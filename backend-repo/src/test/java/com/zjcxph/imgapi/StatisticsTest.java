package com.zjcxph.imgapi;

import com.zjcxph.imgapi.mapper.StatisticsMapper;
import com.zjcxph.imgapi.pojo.BAHStatisticsDTO;
import com.zjcxph.imgapi.pojo.DateStatisticsDTO;
import com.zjcxph.imgapi.pojo.Statistics;
import com.zjcxph.imgapi.service.StatisticsService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Statistics 统计功能测试类
 */
@SpringBootTest
public class StatisticsTest {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsTest.class);

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private StatisticsMapper statisticsMapper;

    /**
     * 测试查询所有统计数据
     */
    @Test
    public void testFindAll() {
        logger.info("========== 测试查询所有统计数据 ==========");
        List<Statistics> allStatistics = statisticsService.findAll();
        
        assertNotNull(allStatistics, "返回结果不应为 null");
        logger.info("查询到 {} 条记录", allStatistics.size());
        
        if (!allStatistics.isEmpty()) {
            Statistics first = allStatistics.get(0);
            logger.info("第一条记录：bah={}, date={}, pages={}", 
                first.getBah(), first.getDate(), first.getPages());
        }
    }

    /**
     * 测试根据病案号查询
     */
    @Test
    public void testFindByBah() {
        logger.info("========== 测试根据病案号查询 ==========");
        
        // 先查询所有数据，获取一个存在的病案号
        List<Statistics> allStatistics = statisticsService.findAll();
        if (allStatistics.isEmpty()) {
            logger.warn("数据库中没有数据，跳过此测试");
            return;
        }
        
        String testBah = allStatistics.get(0).getBah();
        logger.info("使用病案号进行测试：{}", testBah);
        
        List<Statistics> result = statisticsService.findByBah(testBah);
        assertNotNull(result, "返回结果不应为 null");
        logger.info("查询到 {} 条记录", result.size());
        
        // 验证所有返回记录的 bah 都匹配
        for (Statistics stat : result) {
            assertEquals(testBah, stat.getBah(), "病案号应该匹配");
        }
    }

    /**
     * 测试根据日期查询
     */
    @Test
    public void testFindByDate() {
        logger.info("========== 测试根据日期查询 ==========");
        
        // 先查询所有数据，获取一个存在的日期
        List<Statistics> allStatistics = statisticsService.findAll();
        if (allStatistics.isEmpty()) {
            logger.warn("数据库中没有数据，跳过此测试");
            return;
        }
        
        String testDate = allStatistics.get(0).getDate();
        logger.info("使用日期进行测试：{}", testDate);
        
        List<Statistics> result = statisticsService.findByDate(testDate);
        assertNotNull(result, "返回结果不应为 null");
        logger.info("查询到 {} 条记录", result.size());
        
        // 打印匹配的日期格式
        if (!result.isEmpty()) {
            logger.info("日期字段格式示例：{}", result.get(0).getDate());
        }
    }

    /**
     * 测试统计每个病案号的记录数和总页数
     */
    @Test
    public void testGetBAHStatistics() {
        logger.info("========== 测试统计每个病案号的记录数和总页数 ==========");
        List<BAHStatisticsDTO> bahStats = statisticsService.getBAHStatistics();
        
        assertNotNull(bahStats, "返回结果不应为 null");
        logger.info("共有 {} 个不同的病案号", bahStats.size());
        
        // 打印前 5 个统计结果
        int count = Math.min(5, bahStats.size());
        for (int i = 0; i < count; i++) {
            BAHStatisticsDTO stat = bahStats.get(i);
            logger.info("病案号：{} | 记录数：{} | 总页数：{}", 
                stat.getBah(), stat.getRecordCount(), stat.getTotalPages());
        }
    }

    /**
     * 测试统计每个日期的记录数和总页数
     */
    @Test
    public void testGetDateStatistics() {
        logger.info("========== 测试统计每个日期的记录数和总页数 ==========");
        List<DateStatisticsDTO> dateStats = statisticsService.getDateStatistics();
        
        assertNotNull(dateStats, "返回结果不应为 null");
        logger.info("共有 {} 个不同的日期", dateStats.size());
        
        // 打印前 5 个统计结果
        int count = Math.min(5, dateStats.size());
        for (int i = 0; i < count; i++) {
            DateStatisticsDTO stat = dateStats.get(i);
            logger.info("日期：{} | 记录数：{} | 总页数：{}", 
                stat.getDate(), stat.getRecordCount(), stat.getTotalPages());
        }
    }

    /**
     * 测试按条件统计（日期范围和类型）
     */
    @Test
    public void testGetDateStatisticsByCondition() {
        logger.info("========== 测试按条件统计 ==========");
        
        // 测试 1: 不传任何条件
        logger.info("--- 测试 1: 不传任何条件 ---");
        List<DateStatisticsDTO> result1 = statisticsService.getDateStatisticsByCondition(null, null, null);
        assertNotNull(result1);
        logger.info("返回 {} 条记录", result1.size());
        
        // 测试 2: 只传开始日期
        logger.info("--- 测试 2: 只传开始日期 ---");
        result1 = statisticsService.getDateStatisticsByCondition("2024-01-01", null, null);
        assertNotNull(result1);
        logger.info("从 2024-01-01 开始，返回 {} 条记录", result1.size());
        
        // 测试 3: 只传结束日期
        logger.info("--- 测试 3: 只传结束日期 ---");
        result1 = statisticsService.getDateStatisticsByCondition(null, "2024-12-31", null);
        assertNotNull(result1);
        logger.info("到 2024-12-31 为止，返回 {} 条记录", result1.size());
        
        // 测试 4: 传入日期范围
        logger.info("--- 测试 4: 传入完整日期范围 ---");
        result1 = statisticsService.getDateStatisticsByCondition("2024-01-01", "2024-12-31", null);
        assertNotNull(result1);
        logger.info("2024 年整年，返回 {} 条记录", result1.size());
    }

    /**
     * 测试获取总体统计信息
     */
    @Test
    public void testGetTotalStatistics() {
        logger.info("========== 测试获取总体统计信息 ==========");
        Map<String, Object> totalStats = statisticsService.getTotalStatistics();
        
        assertNotNull(totalStats, "返回结果不应为 null");
        logger.info("总体统计信息：{}", totalStats);
        
        // 验证关键字段存在
        assertTrue(totalStats.containsKey("totalRecords"), "应包含 totalRecords 字段");
        assertTrue(totalStats.containsKey("totalPages"), "应包含 totalPages 字段");
        
        logger.info("总记录数：{}", totalStats.get("totalRecords"));
        logger.info("总页数：{}", totalStats.get("totalPages"));
    }

    /**
     * 测试获取不同病案号的数量
     */
    @Test
    public void testGetUniqueBAHCount() {
        logger.info("========== 测试获取不同病案号的数量 ==========");
        Long uniqueCount = statisticsService.getUniqueBAHCount();
        
        assertNotNull(uniqueCount, "返回结果不应为 null");
        logger.info("不同病案号的数量：{}", uniqueCount);
        assertTrue(uniqueCount >= 0, "病案号数量应该大于等于 0");
    }

    /**
     * 测试按类型统计
     */
    @Test
    public void testGetTypeStatistics() {
        logger.info("========== 测试按类型统计 ==========");
        List<Map<String, Object>> typeStats = statisticsService.getTypeStatistics();
        
        assertNotNull(typeStats, "返回结果不应为 null");
        logger.info("共有 {} 种不同的类型", typeStats.size());
        
        // 打印所有类型统计
        for (Map<String, Object> stat : typeStats) {
            logger.info("类型统计：{}", stat);
        }
    }

    /**
     * 测试分页查询
     */
    @Test
    public void testFindAllWithPagination() {
        logger.info("========== 测试分页查询 ==========");
        
        // 第 1 页，每页 10 条
        List<Statistics> page1 = statisticsService.findAllWithPagination(1, 10);
        assertNotNull(page1);
        logger.info("第 1 页（10 条/页）：返回 {} 条记录", page1.size());
        
        // 第 2 页，每页 10 条
        List<Statistics> page2 = statisticsService.findAllWithPagination(2, 10);
        assertNotNull(page2);
        logger.info("第 2 页（10 条/页）：返回 {} 条记录", page2.size());
        
        // 第 1 页，每页 50 条
        List<Statistics> page3 = statisticsService.findAllWithPagination(1, 50);
        assertNotNull(page3);
        logger.info("第 1 页（50 条/页）：返回 {} 条记录", page3.size());
        
        // 验证分页数据不重复
        if (!page1.isEmpty() && !page2.isEmpty()) {
            assertNotEquals(page1.get(0), page2.get(0), "不同页的数据不应该相同");
        }
    }

    /**
     * 测试数据完整性
     */
    @Test
    public void testDataIntegrity() {
        logger.info("========== 测试数据完整性 ==========");
        List<Statistics> allStatistics = statisticsService.findAll();
        
        if (allStatistics.isEmpty()) {
            logger.warn("数据库中没有数据");
            return;
        }
        
        logger.info("检查 {} 条记录的数据完整性", allStatistics.size());
        
        int validCount = 0;
        for (Statistics stat : allStatistics) {
            boolean isValid = true;
            
            // 检查必填字段
            if (stat.getBah() == null || stat.getBah().isEmpty()) {
                logger.warn("发现空病案号的记录");
                isValid = false;
            }
            
            // 检查页数是否为正数
            if (stat.getPages() != null && stat.getPages() < 0) {
                logger.warn("发现负页数的记录：{}", stat);
                isValid = false;
            }
            
            if (isValid) {
                validCount++;
            }
        }
        
        logger.info("有效记录数：{}/{}", validCount, allStatistics.size());
        assertEquals(allStatistics.size(), validCount, "所有记录都应该有效");
    }
}
