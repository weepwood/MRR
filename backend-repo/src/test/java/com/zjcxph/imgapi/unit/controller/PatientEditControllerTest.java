package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.PatientEditController;
import com.zjcxph.imgapi.dto.req.PatientUpdateRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientEditController 患者编辑接口测试")
class PatientEditControllerTest {

    @Mock
    private PatientService patientService;

    @InjectMocks
    private PatientEditController controller;

    @Test
    @DisplayName("PUT /api/v1/patients/{id} — 返回更新后的患者")
    void updatesPatient() {
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah("00789508");
        request.setName("张三");

        Patient updated = new Patient();
        updated.setId(7);
        updated.setBah("00789508");
        updated.setName("张三");
        when(patientService.update(7, request)).thenReturn(updated);

        Result<Patient> result = controller.updatePatient(7, request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getId()).isEqualTo(7);
        assertThat(result.getData().getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("PUT /api/v1/patients/{id} — 患者不存在时返回失败")
    void returnsFailureWhenPatientDoesNotExist() {
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah("00789508");
        when(patientService.update(99, request)).thenReturn(null);

        Result<Patient> result = controller.updatePatient(99, request);

        assertThat(result.getCode()).isNotEqualTo(200);
        assertThat(result.getMessage()).contains("未找到患者记录");
    }
}
