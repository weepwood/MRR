package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.impl.LogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogServiceImpl 日志服务测试")
class LogServiceImplTest {

    @Mock
    private LogMapper logMapper;

    @InjectMocks
    private LogServiceImpl logService;

    @Nested
    @DisplayName("写入与单条查询")
    class WriteAndSingleQuery {

        @Test
        @DisplayName("saveLog — 委托 mapper.insert")
        void saveLog_delegates() {
            Log log = new Log();
            log.setRequestUri("/api/test");

            logService.saveLog(log);

            verify(logMapper).insert(log);
        }

        @Test
        @DisplayName("getLogById — 命中返回 Log，未命中返回 null")
        void getLogById_foundAndMiss() {
            Log log = new Log();
            log.setId(1L);
            when(logMapper.findById(1L)).thenReturn(log);
            when(logMapper.findById(2L)).thenReturn(null);

            assertThat(logService.getLogById(1L)).isSameAs(log);
            assertThat(logService.getLogById(2L)).isNull();
        }
    }

    @Nested
    @DisplayName("分页查询 — 校验参数")
    class PaginationValidation {

        @Test
        @DisplayName("getAllLogs — 合法参数计算 offset 并委托 mapper")
        void getAllLogs_validPaging() {
            when(logMapper.findAll(10, 20)).thenReturn(List.of());
            // page=3, size=10 -> offset = (3-1)*10 = 20
            logService.getAllLogs(3, 10);
            verify(logMapper).findAll(10, 20);
        }

        @Test
        @DisplayName("getAllLogs — page<1 抛 IllegalArgumentException")
        void getAllLogs_invalidPage() {
            assertThatThrownBy(() -> logService.getAllLogs(0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getAllLogs — size>1000 抛 IllegalArgumentException")
        void getAllLogs_sizeTooLarge() {
            assertThatThrownBy(() -> logService.getAllLogs(1, 1001))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("getLogsByClientIp — 传递 clientIp 与 offset")
        void getLogsByClientIp_delegates() {
            when(logMapper.findByClientIp(eq("1.2.3.4"), eq(20), anyInt())).thenReturn(List.of());
            logService.getLogsByClientIp("1.2.3.4", 3, 20);
            // page=3,size=20 -> offset=(3-1)*20=40
            verify(logMapper).findByClientIp("1.2.3.4", 20, 40);
        }

        @Test
        @DisplayName("getLogsByRequestUri — 传递 requestUri 与 offset")
        void getLogsByRequestUri_delegates() {
            when(logMapper.findByRequestUri(eq("/api/v1/x"), anyInt(), anyInt())).thenReturn(List.of());
            logService.getLogsByRequestUri("/api/v1/x", 1, 50);
            verify(logMapper).findByRequestUri("/api/v1/x", 50, 0);
        }
    }

    @Nested
    @DisplayName("搜索查询")
    class Search {

        @Test
        @DisplayName("searchLogs — 全量参数透传给 mapper")
        void searchLogs_passesAllArgs() {
            when(logMapper.search(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of());
            logService.searchLogs("kw", "user", "1.1.1.1", "/x", "GET", "200", "s", "e", 2, 5);
            verify(logMapper).search("kw", "user", "1.1.1.1", "/x", "GET", "200", "s", "e", 5, 5);
        }

        @Test
        @DisplayName("searchLogs — page<1 抛异常，不调 mapper")
        void searchLogs_invalidPage_noMapperCall() {
            assertThatThrownBy(() -> logService.searchLogs(null, null, null, null, null, null, null, null, 0, 5))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(logMapper);
        }

        @Test
        @DisplayName("searchLogs — 游标查询透传 accessTime 与 id 且不计算 OFFSET")
        void searchLogs_cursorDelegatesWithoutOffset() {
            LocalDateTime cursorAccessTime = LocalDateTime.of(2026, 7, 13, 10, 20, 30);
            when(logMapper.searchAfter(any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), anyInt())).thenReturn(List.of());

            logService.searchLogs("kw", null, null, null, null, null, null, null,
                    99, 21, cursorAccessTime, 42L);

            verify(logMapper).searchAfter("kw", null, null, null, null, null, null, null,
                    cursorAccessTime, 42L, 21);
        }
    }

    @Nested
    @DisplayName("计数方法")
    class Count {

        @Test
        @DisplayName("getTotalLogCount — 透传 countAll")
        void getTotalLogCount() {
            when(logMapper.countAll()).thenReturn(42);
            assertThat(logService.getTotalLogCount()).isEqualTo(42);
        }

        @Test
        @DisplayName("getLogCountByClientIp — 透传 countByClientIp")
        void getLogCountByClientIp() {
            when(logMapper.countByClientIp("1.2.3.4")).thenReturn(7);
            assertThat(logService.getLogCountByClientIp("1.2.3.4")).isEqualTo(7);
        }

        @Test
        @DisplayName("getLogCountByRequestUri — 透传 countByRequestUri")
        void getLogCountByRequestUri() {
            when(logMapper.countByRequestUri("/api/x")).thenReturn(3);
            assertThat(logService.getLogCountByRequestUri("/api/x")).isEqualTo(3);
        }

        @Test
        @DisplayName("countSearchLogs — 全量参数透传")
        void countSearchLogs() {
            when(logMapper.countSearch(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(9);
            assertThat(logService.countSearchLogs("kw", "u", "ip", "uri", "GET", "200", "s", "e")).isEqualTo(9);
            verify(logMapper).countSearch("kw", "u", "ip", "uri", "GET", "200", "s", "e");
        }
    }
}
