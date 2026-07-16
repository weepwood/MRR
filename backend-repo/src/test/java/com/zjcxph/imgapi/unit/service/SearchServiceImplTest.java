package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.impl.SearchServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchServiceImpl 搜索服务测试")
class SearchServiceImplTest {

    @Mock
    private SearchMapper searchMapper;

    @InjectMocks
    private SearchServiceImpl searchService;

    @Test
    @DisplayName("getBAHByID — 返回匹配的患者列表")
    void getBAHByID_found() {
        Patient patient = new Patient();
        patient.setId(1);
        patient.setIdCard("110101199001011234");
        patient.setBah("00789508");
        patient.setName("张三");
        patient.setDepartment("内科");

        when(searchMapper.findBAHByIDCard("110101199001011234")).thenReturn(List.of(patient));

        List<Patient> result = searchService.getBAHByID("110101199001011234");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getBah()).isEqualTo("00789508");
        assertThat(result.getFirst().getName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("身份证档案查询 — 返回入院日期、病区和床位")
    void getArchiveCasesByID_includesWardFields() {
        Patient patient = new Patient();
        patient.setId(1);
        patient.setIdCard("110101199001011234");
        patient.setBah("789508");
        patient.setName("张三");
        patient.setRuyuan(LocalDate.of(2026, 7, 16));
        patient.setAdmissiontime("2026-07-16 09:30:00");
        patient.setDepartment("内科");
        patient.setBingqu("内科一病区");
        patient.setChuangwei("08床");

        when(searchMapper.findBAHByIDCard("110101199001011234")).thenReturn(List.of(patient));
        when(searchMapper.findSjhByBah("00789508", "789508")).thenReturn(List.of("10001"));

        List<IdCardArchiveSearchResponse.ArchiveCase> result =
                searchService.getArchiveCasesByID("110101199001011234");

        assertThat(result).singleElement().satisfies(archiveCase -> {
            assertThat(archiveCase.getRuyuan()).isEqualTo(LocalDate.of(2026, 7, 16));
            assertThat(archiveCase.getBingqu()).isEqualTo("内科一病区");
            assertThat(archiveCase.getChuangwei()).isEqualTo("08床");
        });
    }

    @Test
    @DisplayName("getBAHByID — 无匹配返回空列表")
    void getBAHByID_notFound() {
        when(searchMapper.findBAHByIDCard("000000000000000000")).thenReturn(List.of());
        assertThat(searchService.getBAHByID("000000000000000000")).isEmpty();
    }
}
