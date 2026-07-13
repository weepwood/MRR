package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.resp.BAHStatisticsDTO;
import com.zjcxph.imgapi.dto.resp.DateStatisticsDTO;
import com.zjcxph.imgapi.entity.Statistics;
import com.zjcxph.imgapi.mapper.StatisticsMapper;
import com.zjcxph.imgapi.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsServiceImpl 统计服务测试")
class StatisticsServiceImplTest {

    @Mock
    private StatisticsMapper statisticsMapper;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    private Statistics mockStat;

    @BeforeEach
    void setUp() {
        mockStat = new Statistics();
        mockStat.setBah("00789508");
        mockStat.setType("CT");
        mockStat.setPages(10);
    }

    @Nested
    @DisplayName("查询方法")
    class QueryTests {

        @Test
        @DisplayName("findAll — 返回全部统计")
        void findAll() {
            when(statisticsMapper.findAll()).thenReturn(List.of(mockStat));
            assertThat(statisticsService.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("findAllWithPagination — 分页查询")
        void findAllWithPagination() {
            when(statisticsMapper.findAllWithPagination(0, 10)).thenReturn(List.of(mockStat));
            assertThat(statisticsService.findAllWithPagination(1, 10)).hasSize(1);
        }

        @Test
        @DisplayName("findAllWithPagination — 非法参数抛异常")
        void findAllWithPagination_invalidPage() {
            assertThatThrownBy(() -> statisticsService.findAllWithPagination(0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("findByBah — 按病案号查询")
        void findByBah() {
            when(statisticsMapper.findByBah("00789508", "789508")).thenReturn(List.of(mockStat));
            assertThat(statisticsService.findByBah("00789508")).hasSize(1);
        }

        @Test
        @DisplayName("findByDate — 按日期查询")
        void findByDate() {
            when(statisticsMapper.findByDate("2026-01-01")).thenReturn(List.of(mockStat));
            assertThat(statisticsService.findByDate("2026-01-01")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("聚合统计")
    class AggregationTests {

        @Test
        @DisplayName("getTotalCount — 返回总数")
        void getTotalCount() {
            when(statisticsMapper.getTotalCount()).thenReturn(100L);
            assertThat(statisticsService.getTotalCount()).isEqualTo(100L);
        }

        @Test
        @DisplayName("getUniqueBAHCount — 返回唯一BAH数")
        void getUniqueBAHCount() {
            when(statisticsMapper.getUniqueBAHCount()).thenReturn(50L);
            assertThat(statisticsService.getUniqueBAHCount()).isEqualTo(50L);
        }

        @Test
        @DisplayName("getBAHStatistics — 返回BAH统计列表")
        void getBAHStatistics() {
            BAHStatisticsDTO dto = new BAHStatisticsDTO();
            dto.setBah("00789508");
            when(statisticsMapper.getBAHStatistics()).thenReturn(List.of(dto));
            assertThat(statisticsService.getBAHStatistics()).hasSize(1);
        }

        @Test
        @DisplayName("getDateStatistics — 返回日期统计列表")
        void getDateStatistics() {
            DateStatisticsDTO dto = new DateStatisticsDTO();
            dto.setDate("2026-01-01");
            when(statisticsMapper.getDateStatistics()).thenReturn(List.of(dto));
            assertThat(statisticsService.getDateStatistics()).hasSize(1);
        }

        @Test
        @DisplayName("getTotalStatistics — 返回总量统计Map")
        void getTotalStatistics() {
            when(statisticsMapper.getTotalStatistics()).thenReturn(Map.of("total", 100));
            assertThat(statisticsService.getTotalStatistics()).containsEntry("total", 100);
        }

        @Test
        @DisplayName("getTypeStatistics — 返回类型统计")
        void getTypeStatistics() {
            when(statisticsMapper.getTypeStatistics()).thenReturn(List.of(Map.of("type", "CT", "count", 30)));
            assertThat(statisticsService.getTypeStatistics()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("条件查询")
    class ConditionQueryTests {

        @Test
        @DisplayName("findWithConditionAndPagination — 多条件分页查询")
        void findWithConditionAndPagination() {
            when(statisticsMapper.findWithConditionAndPagination(0, 10, "007", null, null, "CT", "2026-01-01", "2026-01-31", "date", "desc"))
                    .thenReturn(List.of(mockStat));
            var result = statisticsService.findWithConditionAndPagination(1, 10, "007", null, null, "CT", "2026-01-01", "2026-01-31", "date", "desc");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("findWithConditionAndPagination — 含 bah/sjh 条件")
        void findWithConditionAndPagination_withBahAndSjh() {
            when(statisticsMapper.findWithConditionAndPagination(0, 10, null, "78", "SJH001", null, null, null, "date", "desc"))
                .thenReturn(List.of(mockStat));
            var result = statisticsService.findWithConditionAndPagination(1, 10, null, "0078", "SJH001", null, null, null, "date", "desc");
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getTotalCountByCondition — 条件总数")
        void getTotalCountByCondition() {
            when(statisticsMapper.getTotalCountByCondition("007", null, null, "CT", null, null)).thenReturn(5L);
            assertThat(statisticsService.getTotalCountByCondition("007", null, null, "CT", null, null)).isEqualTo(5L);
        }

        @Test
        @DisplayName("getDateStatisticsByCondition — 条件日期统计")
        void getDateStatisticsByCondition() {
            DateStatisticsDTO dto = new DateStatisticsDTO();
            dto.setDate("2026-01-01");
            when(statisticsMapper.getDateStatisticsByCondition("2026-01-01", "2026-01-31", "CT")).thenReturn(List.of(dto));
            assertThat(statisticsService.getDateStatisticsByCondition("2026-01-01", "2026-01-31", "CT")).hasSize(1);
        }
    }
}
