package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.OssMigrationManagementController;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.ImageMigrationLog;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.OssMigrationManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OSS 迁移管理查询接口测试")
class OssMigrationManagementControllerTest {

    @Mock
    private OssMigrationManagementService managementService;

    @InjectMocks
    private OssMigrationManagementController controller;

    @Test
    @DisplayName("等待上架号查询支持目录和病案号筛选")
    void returnsWaitingSjhRecordsWithFilters() {
        Scan record = new Scan();
        record.setId(7);
        record.setBah("00789124");
        when(managementService.getWaitingSjh("25.03.15", "00789124", null, 101))
                .thenReturn(List.of(record));

        Result<Map<String, Object>> result = controller.getWaitingSjh(
                100,
                "25.03.15",
                "00789124",
                null
        );

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("returned", 1);
        assertThat(result.getData()).containsEntry("hasMore", false);
        assertThat(result.getData().get("list")).isEqualTo(List.of(record));
        verify(managementService).getWaitingSjh("25.03.15", "00789124", null, 101);
    }

    @Test
    @DisplayName("待迁移查询通过额外一条记录判断是否还有更多")
    void reportsHasMoreWithoutFullCount() {
        Scan first = new Scan();
        first.setId(1);
        Scan second = new Scan();
        second.setId(2);
        when(managementService.getPending(null, null, null, 2))
                .thenReturn(List.of(first, second));

        Result<Map<String, Object>> result = controller.getPending(1, null, null, null);

        assertThat(result.getData()).containsEntry("returned", 1);
        assertThat(result.getData()).containsEntry("limit", 1);
        assertThat(result.getData()).containsEntry("hasMore", true);
        assertThat(result.getData().get("list")).isEqualTo(List.of(first));
    }

    @Test
    @DisplayName("迁移日志支持按 Scan ID 查询")
    void filtersLogsByScanId() {
        ImageMigrationLog log = new ImageMigrationLog();
        log.setScanId(88);
        when(managementService.getLogs("failed", 88, 1, 20)).thenReturn(List.of(log));
        when(managementService.countLogs("failed", 88)).thenReturn(1L);

        Result<PageResult<ImageMigrationLog>> result = controller.getLogs("failed", 88, 1, 20);

        assertThat(result.getData().getList()).containsExactly(log);
        assertThat(result.getData().getTotal()).isEqualTo(1L);
        verify(managementService).getLogs("failed", 88, 1, 20);
        verify(managementService).countLogs("failed", 88);
    }
}
