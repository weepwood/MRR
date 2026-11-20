package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NginxManagementServiceTest {
    @TempDir
    Path root;

    @Test
    void disabledByDefaultIsVisibleButAllOperationalActionsAreRejected() {
        NginxManagementService service = new NginxManagementService(false, root.toString());

        assertThat(service.status()).containsEntry("enabled", false);
        assertThatThrownBy(service::configFiles).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(service::testConfig).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.tailLog("access", 20)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationListReturnsMetadataOnly() throws Exception {
        Path configDir = Files.createDirectories(root.resolve("config/nginx"));
        Files.writeString(configDir.resolve("nginx.conf"), "server { listen 80; }", StandardCharsets.UTF_8);
        NginxManagementService service = new NginxManagementService(true, root.toString());

        List<Map<String, Object>> files = service.configFiles();
        assertThat(files).hasSize(5);
        assertThat(files.get(0)).containsEntry("name", "nginx.conf").containsEntry("present", true);
        assertThat(files.get(0)).containsKey("bytes").doesNotContainKey("content");
        assertThat(files.get(1)).containsEntry("present", false);
    }

    @Test
    void logTailIsBoundedWhitelistedAndRedactsIdentifiers() throws Exception {
        Path logDir = Files.createDirectories(root.resolve("logs/nginx"));
        Files.writeString(logDir.resolve("access.log"), String.join("\n",
                "line-one",
                "GET /archive/external/sensitive-id?ticket=secret HTTP/1.1 authorization: Bearer secret-token",
                "GET /idcard/123456789012345678"),
                StandardCharsets.UTF_8);
        NginxManagementService service = new NginxManagementService(true, root.toString());

        Map<String, Object> result = service.tailLog("access", 2);
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertThat(lines).hasSize(2);
        assertThat(String.join("\n", lines)).doesNotContain("secret", "123456789012345678");
        assertThat(lines.get(0)).contains("[REDACTED]");
        assertThat(result).containsEntry("truncated", true);

        assertThatThrownBy(() -> service.tailLog("../../secrets/application-secrets.properties", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.tailLog("access", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.tailLog("access", 201)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void largeFileOnlyReadsBoundedTail() throws Exception {
        Path logDir = Files.createDirectories(root.resolve("logs/nginx"));
        String lines = ("access " + "x".repeat(1024) + "\n").repeat(500);
        Files.writeString(logDir.resolve("access.log"), lines, StandardCharsets.UTF_8);
        NginxManagementService service = new NginxManagementService(true, root.toString());

        Map<String, Object> result = service.tailLog("access", 200);
        @SuppressWarnings("unchecked")
        List<String> entries = (List<String>) result.get("lines");
        assertThat(entries).hasSizeLessThanOrEqualTo(200);
        assertThat(result).containsEntry("truncated", true);
        assertThat(entries).allMatch(line -> line.length() <= 2049);
    }

    @Test
    void unavailableBinaryNeverStartsArbitraryCommand() {
        NginxManagementService service = new NginxManagementService(true, root.toString());
        assertThatThrownBy(service::testConfig).isInstanceOf(IllegalStateException.class);
    }
}
