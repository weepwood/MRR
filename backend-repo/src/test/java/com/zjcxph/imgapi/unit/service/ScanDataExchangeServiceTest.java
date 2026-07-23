package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.resp.DataExchangeImportResult;
import com.zjcxph.imgapi.service.ScanDataExchangeService;
import com.zjcxph.imgapi.service.importer.TabularImportFileReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanDataExchangeService 扫描记录交换测试")
class ScanDataExchangeServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ScanDataExchangeService service;

    @BeforeEach
    void setUp() {
        service = new ScanDataExchangeService(jdbcTemplate, new TabularImportFileReader());
    }

    @Test
    @DisplayName("缺少必需字段时整批拒绝且不访问数据库")
    void rejectsMissingHeaders() throws Exception {
        MockMultipartFile file = csv("""
                sjh,bah,folder,filename
                24.04.07,00789124,24.04/24.04.07/00789124,0001.jpg
                """);

        DataExchangeImportResult result = service.importScans(file, true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errors()).extracting("field").contains("brxh", "btype", "filesize");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("目录文件名缺失、非法图片类型和单位化文件大小会被拒绝")
    void rejectsInvalidValues() throws Exception {
        MockMultipartFile file = csv("""
                sjh,bah,brxh,folder,filename,btype,filesize
                ,10000000,1,,,16,12MB
                """);

        DataExchangeImportResult result = service.importScans(file, true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(result.errors()).extracting("field")
                .contains("sjh", "folder", "filename", "btype", "filesize");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("错误超过展示上限时仍准确统计全部错误行")
    void countsErrorsBeyondDisplayLimit() throws Exception {
        StringBuilder content = new StringBuilder(
                "sjh,bah,brxh,folder,filename,btype,filesize\n"
        );
        for (int index = 1; index <= 201; index++) {
            content.append("SJH")
                    .append(index)
                    .append(",BAH")
                    .append(index)
                    .append(",BRXH")
                    .append(index)
                    .append(",folder")
                    .append(index)
                    .append(",")
                    .append(index)
                    .append(".jpg,99,100\n");
        }

        DataExchangeImportResult result = service.importScans(csv(content.toString()), true);

        assertThat(result.canImport()).isFalse();
        assertThat(result.errorRows()).isEqualTo(201);
        assertThat(result.errors()).hasSize(200);
        assertThat(result.errorsTruncated()).isTrue();
        verifyNoInteractions(jdbcTemplate);
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "mr_scan.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
