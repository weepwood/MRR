package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "mrr.integration")
public class IntegrationProperties {

    private boolean enabled = false;
    private int ticketTtlSeconds = 90;
    private int sessionTtlSeconds = 1800;
    private int timestampToleranceSeconds = 300;
    private int maxArchivesPerTicket = 100;
    private List<Client> clients = new ArrayList<>();

    @Data
    public static class Client {
        private String clientId;
        private String secret;
        private boolean enabled = true;
        private List<String> allowedIps = new ArrayList<>();
    }
}
