package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.mapper.LogMapper;
import com.zjcxph.imgapi.service.impl.AsyncLogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AsyncLogServiceImpl 异步日志服务测试")
class AsyncLogServiceImplTest {

    @Mock
    private LogMapper logMapper;

    @InjectMocks
    private AsyncLogServiceImpl asyncLogService;

    private Log newLog(int i) {
        Log log = new Log();
        log.setRequestUri("/api/" + i);
        return log;
    }

    @Nested
    @DisplayName("saveLogAsync — 缓冲区累积与刷新")
    class SaveLogAsyncBuffer {

        @Test
        @DisplayName("未达批次阈值(50)时不写库")
        void saveLogAsync_underBatchSize_noFlush() {
            for (int i = 0; i < 49; i++) {
                asyncLogService.saveLogAsync(newLog(i));
            }
            verifyNoInteractions(logMapper);
        }

        @Test
        @DisplayName("达到批次阈值(50)时触发批量写入")
        void saveLogAsync_reachesBatchSize_flushes() {
            for (int i = 0; i < 50; i++) {
                asyncLogService.saveLogAsync(newLog(i));
            }
            // 实现调用 batchInsert 一次（传入 50 条），而非逐条 insert
            verify(logMapper).batchInsert(anyList());
            verify(logMapper, never()).insert(any(Log.class));
        }

        @Test
        @DisplayName("destroy 刷新缓冲区剩余日志")
        void destroy_flushesRemaining() {
            for (int i = 0; i < 10; i++) {
                asyncLogService.saveLogAsync(newLog(i));
            }
            verifyNoInteractions(logMapper);

            asyncLogService.destroy();

            // destroy 调用 batchInsertLogs -> batchInsert 一次（传入 10 条）
            verify(logMapper).batchInsert(anyList());
            verify(logMapper, never()).insert(any(Log.class));
        }

        @Test
        @DisplayName("缓冲区为空时 destroy 不写库")
        void destroy_emptyBuffer_noFlush() {
            asyncLogService.destroy();
            verifyNoInteractions(logMapper);
        }

        @Test
        @DisplayName("批量插入失败时逐条重试，不抛出（saveLogAsync 吞异常）")
        void saveLogAsync_batchFails_retriesOneByOne() {
            // batchInsert 抛异常 -> 触发 retryInsertOneByOne 逐条 insert（50 次）
            when(logMapper.batchInsert(anyList()))
                    .thenThrow(new RuntimeException("db down"));

            for (int i = 0; i < 50; i++) {
                asyncLogService.saveLogAsync(newLog(i));
            }

            // batchInsert 失败 1 次 → retryInsertOneByOne 逐条 insert 50 次
            verify(logMapper).batchInsert(anyList());
            verify(logMapper, times(50)).insert(any(Log.class));
        }
    }

    @Nested
    @DisplayName("batchSaveLogsAsync — 显式批量")
    class BatchSaveLogsAsync {

        @Test
        @DisplayName("空列表直接短路返回")
        void batchSaveLogsAsync_emptyList_returns() {
            asyncLogService.batchSaveLogsAsync(new ArrayList<>());
            verifyNoInteractions(logMapper);
        }

        @Test
        @DisplayName("null 列表直接短路返回")
        void batchSaveLogsAsync_nullList_returns() {
            asyncLogService.batchSaveLogsAsync(null);
            verifyNoInteractions(logMapper);
        }

        @Test
        @DisplayName("非空列表批量 insert")
        void batchSaveLogsAsync_insertsEach() {
            List<Log> logs = List.of(newLog(1), newLog(2), newLog(3));

            asyncLogService.batchSaveLogsAsync(logs);

            // 实现调用 batchInsert 一次（传入 3 条）
            verify(logMapper).batchInsert(anyList());
            verify(logMapper, never()).insert(any(Log.class));
        }

        @Test
        @DisplayName("batchInsert 抛异常时向上抛出（不吞异常）")
        void batchSaveLogsAsync_propagatesException() {
            List<Log> logs = List.of(newLog(1));
            when(logMapper.batchInsert(anyList())).thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> asyncLogService.batchSaveLogsAsync(logs))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("boom");
        }
    }
}
