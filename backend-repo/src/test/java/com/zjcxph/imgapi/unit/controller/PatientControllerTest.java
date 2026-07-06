package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.PatientController;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientController 控制器测试")
class PatientControllerTest {

    @Mock
    private SearchMapper searchMapper;

    @InjectMocks
    private PatientController patientController;

    @Test
    @DisplayName("GET /api/v1/patients — 分页返回患者列表")
    void listPatients() {
        Patient p = new Patient();
        p.setId(1);
        p.setBah("00789508");
        p.setName("张三");
        p.setDepartment("内科");

        when(searchMapper.findAllPaginated(anyInt(), anyInt(), any())).thenReturn(List.of(p));
        when(searchMapper.countAll(any())).thenReturn(1);

        Result<PageResult<Patient>> result = patientController.listPatients(1, 20, null);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getList()).hasSize(1);
        assertThat(result.getData().getList().get(0).getBah()).isEqualTo("00789508");
        assertThat(result.getData().getTotal()).isEqualTo(1);
        assertThat(result.getData().getPage()).isEqualTo(1);
        assertThat(result.getData().getSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("GET /api/v1/patients — 关键词搜索")
    void listPatients_withKeyword() {
        when(searchMapper.findAllPaginated(anyInt(), anyInt(), any())).thenReturn(List.of());
        when(searchMapper.countAll(any())).thenReturn(0);

        Result<PageResult<Patient>> result = patientController.listPatients(1, 20, "张三");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getList()).isEmpty();
        assertThat(result.getData().getTotal()).isEqualTo(0);
    }
}
