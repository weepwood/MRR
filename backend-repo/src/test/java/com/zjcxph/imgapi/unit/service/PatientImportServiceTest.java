package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.resp.PatientImportResult;
import com.zjcxph.imgapi.service.PatientImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientImportService 患者导入测试")
class PatientImportServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PatientImportService patientImportService;

    @BeforeEach
    void setUp() {
        patientImportService = new PatientImportService(jdbcTemplate);
    }

    @Test
    @DisplayName("缺少必需字段时整批拒绝且不访问数据库")
    void rejectsMissingHeaders() throws Exception {
        MockMultipartFile file = csv("bah,name\n00789508,张三\n", StandardCharsets.UTF_8);

        PatientImportResult result = patientImportService.importPatients(file, true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(result.errors())
                .extracting("field")
                .contains("idcard", "ruyuan", "admissiontime", "department", "bingqu", "chuangwei");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("错误日期和科学计数法会被拦截且身份证号保持脱敏")
    void rejectsInvalidFormatsAndMasksIdCard() throws Exception {
        String content = "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\n"
                + "7.89508E+5,张三,3.3000019900101E+17,2026-99-01,2026-07-01,内科,一病区,12A\n";
        MockMultipartFile file = csv(content, StandardCharsets.UTF_8);

        PatientImportResult result = patientImportService.importPatients(file, true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(result.errors()).extracting("field")
                .contains("bah", "idcard", "ruyuan", "admissiontime");
        assertThat(result.errors().stream()
                .filter(error -> "idcard".equals(error.field()))
                .findFirst()
                .orElseThrow()
                .value())
                .contains("*")
                .doesNotContain("3.3000019900101E+17");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("GB18030 CSV 可以识别编码并返回行级错误")
    void detectsGb18030Encoding() throws Exception {
        String content = "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei\n"
                + "00789508,张三,,错误日期,,内科,一病区,12A\n";
        MockMultipartFile file = csv(content, Charset.forName("GB18030"));

        PatientImportResult result = patientImportService.importPatients(file, true);

        assertThat(result.encoding()).isEqualTo("GB18030");
        assertThat(result.canImport()).isFalse();
        assertThat(result.errors()).extracting("field").contains("ruyuan");
        verifyNoInteractions(jdbcTemplate);
    }

    private MockMultipartFile csv(String content, Charset charset) {
        return new MockMultipartFile(
                "file",
                "patients.csv",
                "text/csv",
                content.getBytes(charset)
        );
    }
}
