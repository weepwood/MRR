package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.IntegrationProperties;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveIntegrationStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalArchiveIntegrationStatusControllerTest {

    @Test
    void returnsSafeConfigurationDiagnosticsWithoutSecretPlaintext() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setEnabled(true);
        properties.setTicketTtlSeconds(90);
        properties.setSessionTtlSeconds(1800);
        properties.setTimestampToleranceSeconds(300);
        properties.setMaxArchivesPerTicket(100);

        IntegrationProperties.Client client = new IntegrationProperties.Client();
        client.setClientId("his-system");
        client.setSecret("super-secret-value-that-must-not-be-returned");
        client.setEnabled(true);
        client.setAllowedIps(List.of("10.10.20.15"));
        properties.setClients(List.of(client));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.10.20.15, 10.10.20.1");

        ExternalArchiveIntegrationStatusController controller =
                new ExternalArchiveIntegrationStatusController(properties);
        Result<ExternalArchiveIntegrationStatusResponse> result = controller.status(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().enabled()).isTrue();
        assertThat(result.getData().requestIp()).isEqualTo("10.10.20.15");
        assertThat(result.getData().clients()).singleElement().satisfies(status -> {
            assertThat(status.clientId()).isEqualTo("his-system");
            assertThat(status.secretConfigured()).isTrue();
            assertThat(status.requestIpAllowed()).isTrue();
        });
        assertThat(result.getData().toString()).doesNotContain("super-secret-value-that-must-not-be-returned");
    }

    @Test
    void reportsDisabledIntegrationAndRejectedIp() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setEnabled(false);

        IntegrationProperties.Client client = new IntegrationProperties.Client();
        client.setClientId("his-system");
        client.setSecret("");
        client.setEnabled(true);
        client.setAllowedIps(List.of("10.10.20.15"));
        properties.setClients(List.of(client));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.10.20.99");

        ExternalArchiveIntegrationStatusResponse data =
                new ExternalArchiveIntegrationStatusController(properties).status(request).getData();

        assertThat(data.enabled()).isFalse();
        assertThat(data.clients()).singleElement().satisfies(status -> {
            assertThat(status.secretConfigured()).isFalse();
            assertThat(status.requestIpAllowed()).isFalse();
        });
    }
}
