package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.ImageAuditExportController;
import com.zjcxph.imgapi.entity.Log;
import com.zjcxph.imgapi.service.LogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageAuditExportControllerSpreadsheetSafetyTest {

    @Mock
    private LogService logService;

    @Test
    void shouldPreventFormulaInterpretationInAuditCsv() throws Exception {
        Log event = new Log();
        event.setUsername("=2+2");
        event.setAuditTarget("sjh:000123");
        event.setAuditDescription("@NOW()");
        event.setRequestUri("+malicious");
        event.setClientIp("192.0.2.1");
        when(logService.searchImageAuditLogs(
                null, null, null, null, null, null, null, 1, 200))
                .thenReturn(List.of(event));

        ImageAuditExportController controller = new ImageAuditExportController(logService);
        ResponseEntity<StreamingResponseBody> response = controller.export(
                "all", null, null, null, null, null, null, null, null);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        String csv = output.toString(StandardCharsets.UTF_8);
        assertThat(csv).contains(""'=2+2"");
        assertThat(csv).contains(""'000123"");
        assertThat(csv).contains(""'@NOW()"");
        assertThat(csv).contains(""'+malicious"");
        assertThat(csv).contains(""192.0.2.1"");
    }
}
