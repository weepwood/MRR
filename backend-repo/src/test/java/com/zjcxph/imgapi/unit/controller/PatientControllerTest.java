package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.PatientController;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.resp.PatientAnalyticsSummary;
import com.zjcxph.imgapi.dto.resp.PatientImportResult;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.PatientAnalyticsService;
import com.zjcxph.imgapi.service.PatientImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientController 控制器测试")
class PatientControllerTest {

    @Mock
    private SearchMapper searchMapper;

    @Mock
    private PatientImportService patientImportService;

    @Mock
    private PatientAnalyticsService patientAnalyticsService;

    @InjectMocks
    private PatientController patientController;

    @Test
    @DisplayName("GET /api/v1/patients — 分页返回患者列表")
    void listPatients() {
        Patient patient = new Patient();
        patient.setId(1);
        patient.setBah("00789508");
        patient.setName("张三");
        patient.setDepartment("内科");

        when(searchMapper.findAllPaginated(anyInt(), anyInt(), any())).thenReturn(List.of(patient));
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

    @Test
    @DisplayName("GET /api/v1/patients/analytics/summary — 返回患者统计汇总")
    void getAnalyticsSummary() {
        PatientAnalyticsSummary summary = new PatientAnalyticsSummary(
                2026,
                100,
                90,
                35,
                8,
                4,
                2,
                List.of(new PatientAnalyticsSummary.DateCount("2026-01-01", 3)),
                List.of(new PatientAnalyticsSummary.DepartmentCount("内科", 10))
        );
        when(patientAnalyticsService.getSummary(2026)).thenReturn(summary);

        Result<PatientAnalyticsSummary> result = patientController.getAnalyticsSummary(2026);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().yearArchives()).isEqualTo(35);
        assertThat(result.getData().missingIdCardRecords()).isEqualTo(8);
    }

    @Test
    @DisplayName("POST /api/v1/patients/import — 返回患者文件校验结果")
    void importPatients() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "patients.csv",
                "text/csv",
                "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\n00789508,张三,,,,,,".getBytes()
        );
        PatientImportResult importResult = new PatientImportResult(
                "patients.csv", "UTF-8", true, true,
                1, 1, 0, 0, 0, false, List.of()
        );
        when(patientImportService.importPatients(any(), anyBoolean())).thenReturn(importResult);

        Result<PatientImportResult> result = patientController.importPatients(file, true);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().canImport()).isTrue();
        assertThat(result.getData().totalRows()).isEqualTo(1);
    }
}
