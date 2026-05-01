package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ScanController;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanController 扫描控制器测试")
class ScanControllerTest {

    @Mock
    private ScanService scanService;

    @InjectMocks
    private ScanController scanController;

    private Scan mockScan;
    private ScanRequest validRequest;

    @BeforeEach
    void setUp() {
        mockScan = new Scan();
        mockScan.setId(1);
        mockScan.setBah("00789508");
        mockScan.setBrxh("605746");
        mockScan.setFilename("test.jpg");
        mockScan.setBtype(1);
        mockScan.setPages(2);

        validRequest = new ScanRequest();
        validRequest.setBah("00789508");
        validRequest.setBrxh("605746");
        validRequest.setFilename("new.jpg");
        validRequest.setBtype(2);
        validRequest.setPages(1);
    }

    @Test
    @DisplayName("GET findAll — 返回全部记录")
    void findAll() {
        when(scanService.findAll()).thenReturn(List.of(mockScan));

        Result<List<Scan>> result = scanController.findAll();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("GET findById — 存在返回记录")
    void findById_found() {
        when(scanService.findById(1)).thenReturn(mockScan);

        Result<Scan> result = scanController.findById(1);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getBah()).isEqualTo("00789508");
    }

    @Test
    @DisplayName("GET findById — 不存在返回失败")
    void findById_notFound() {
        when(scanService.findById(999)).thenReturn(null);

        Result<Scan> result = scanController.findById(999);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("未找到");
    }

    @Test
    @DisplayName("POST create — 成功创建")
    void create_success() {
        when(scanService.create(any())).thenReturn(mockScan);

        Result<Scan> result = scanController.create(validRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("POST create — 失败返回错误")
    void create_fail() {
        when(scanService.create(any())).thenReturn(null);

        Result<Scan> result = scanController.create(validRequest);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("失败");
    }

    @Test
    @DisplayName("DELETE deleteById — 删除成功")
    void deleteById_success() {
        when(scanService.softDeleteById(1)).thenReturn(true);

        Result<String> result = scanController.deleteById(1);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).contains("删除成功");
    }

    @Test
    @DisplayName("DELETE deleteById — 记录不存在")
    void deleteById_notFound() {
        when(scanService.softDeleteById(999)).thenReturn(false);

        Result<String> result = scanController.deleteById(999);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("不存在");
    }

    @Test
    @DisplayName("DELETE deleteById — ID为空返回失败")
    void deleteById_nullId() {
        Result<String> result = scanController.deleteById(null);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("不能为空");
    }

    @Test
    @DisplayName("PUT update — 更新成功")
    void update_success() {
        when(scanService.update(any())).thenReturn(mockScan);

        Result<Scan> result = scanController.update(1, validRequest);

        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("PUT update — 更新失败返回错误")
    void update_fail() {
        when(scanService.update(any())).thenReturn(null);

        Result<Scan> result = scanController.update(999, validRequest);

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET findByBah — 按病案号查询")
    void findByBah() {
        when(scanService.findByBah("00789508")).thenReturn(List.of(mockScan));

        Result<List<Scan>> result = scanController.findByBah("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("GET findByBah — 空病案号返回失败")
    void findByBah_empty() {
        Result<List<Scan>> result = scanController.findByBah("");

        assertThat(result.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("GET findByBrxh — 按病人序号查询")
    void findByBrxh() {
        when(scanService.findByBrxh("605746")).thenReturn(List.of(mockScan));

        Result<List<Scan>> result = scanController.findByBrxh("605746");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("GET findAllWithPagination — 分页查询")
    void findAllWithPagination() {
        when(scanService.findAllWithPagination(eq(1), eq(10))).thenReturn(List.of(mockScan));
        when(scanService.countByCondition(any())).thenReturn(1L);

        Result<PageResult<Scan>> result = scanController.findAllWithPagination(1, 10);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getTotal()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST findByCondition — 条件查询")
    void findByCondition() {
        when(scanService.findByCondition(any())).thenReturn(List.of(mockScan));

        Result<List<Scan>> result = scanController.findByCondition(validRequest);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }
}
