package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.PatientUpdateRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientService 患者编辑服务测试")
class PatientServiceTest {

    @Mock
    private SearchMapper searchMapper;

    @InjectMocks
    private PatientService patientService;

    @Test
    @DisplayName("按记录 ID 更新患者并清理空白字段")
    void updatesPatientById() {
        Patient existing = new Patient();
        existing.setId(7);
        Patient updated = new Patient();
        updated.setId(7);
        updated.setBah("00789508");
        updated.setName("张三");

        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah(" 789508 ");
        request.setName(" 张三 ");
        request.setIdCard(" ");
        request.setRuyuan(LocalDate.of(2026, 7, 22));
        request.setAdmissiontime("2026-07-22 08:30");
        request.setDepartment(" 内科 ");
        request.setBingqu("一病区");
        request.setChuangwei("12A");

        when(searchMapper.findPatientById(7)).thenReturn(existing, updated);
        when(searchMapper.updatePatient(any(Patient.class))).thenReturn(1);

        Patient result = patientService.update(7, request);

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(searchMapper).updatePatient(captor.capture());
        Patient saved = captor.getValue();
        assertThat(saved.getBah()).isEqualTo("00789508");
        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getIdCard()).isNull();
        assertThat(saved.getAdmissiontime()).isEqualTo("2026-07-22 08:30:00");
        assertThat(saved.getDepartment()).isEqualTo("内科");
        assertThat(result).isSameAs(updated);
    }

    @Test
    @DisplayName("患者不存在时不执行更新")
    void returnsNullWhenPatientDoesNotExist() {
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah("00789508");
        when(searchMapper.findPatientById(99)).thenReturn(null);

        assertThat(patientService.update(99, request)).isNull();
        verify(searchMapper, never()).updatePatient(any(Patient.class));
    }

    @Test
    @DisplayName("拒绝无法解析的入院时间")
    void rejectsInvalidAdmissionTime() {
        Patient existing = new Patient();
        existing.setId(7);
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah("00789508");
        request.setAdmissiontime("2026-99-99");
        when(searchMapper.findPatientById(7)).thenReturn(existing);

        assertThatThrownBy(() -> patientService.update(7, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("入院时间");
    }

    @Test
    @DisplayName("拒绝保存为完全重复的患者记录")
    void rejectsDuplicatePatient() {
        Patient existing = new Patient();
        existing.setId(7);
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setBah("00789508");
        request.setName("张三");
        when(searchMapper.findPatientById(7)).thenReturn(existing);
        when(searchMapper.existsDuplicatePatient(any(Patient.class))).thenReturn(true);

        assertThatThrownBy(() -> patientService.update(7, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("完全重复");
        verify(searchMapper, never()).updatePatient(any(Patient.class));
    }
}
