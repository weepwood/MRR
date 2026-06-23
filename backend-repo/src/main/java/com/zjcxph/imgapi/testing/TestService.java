package com.zjcxph.imgapi.testing;

import com.zjcxph.imgapi.config.OssProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TestService {

    private static final Logger logger = LoggerFactory.getLogger(TestService.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final OssProperties ossProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public TestService(DataSource dataSource, JdbcTemplate jdbcTemplate, OssProperties ossProperties) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.ossProperties = ossProperties;
    }

    public List<SmokeTestItem> runSmoke() {
        List<SmokeTestItem> results = new ArrayList<>();
        results.add(checkDatabase());
        results.add(checkMemory());
        results.add(checkSystemTime());
        results.add(checkOssConfig());
        results.add(checkApiReachability());
        return results;
    }

    private SmokeTestItem checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            return new SmokeTestItem("数据库连接", valid ? "PASS" : "FAIL",
                    valid ? "连接成功 (" + conn.getMetaData().getURL() + ")" : "连接验证失败");
        } catch (Exception e) {
            return new SmokeTestItem("数据库连接", "FAIL", e.getMessage());
        }
    }

    private SmokeTestItem checkMemory() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        double percent = max > 0 ? used * 100.0 / max : 0;
        String status = percent < 80 ? "PASS" : (percent < 90 ? "WARN" : "FAIL");
        return new SmokeTestItem("JVM 内存", status,
                String.format("已用 %d MB / 最大 %d MB (%.1f%%)", used / 1048576, max / 1048576, percent));
    }

    private SmokeTestItem checkSystemTime() {
        LocalDateTime now = LocalDateTime.now();
        return new SmokeTestItem("系统时间", "PASS",
                "当前时间: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private SmokeTestItem checkOssConfig() {
        if (ossProperties.getAccessKeyId() == null || ossProperties.getAccessKeyId().isBlank()) {
            return new SmokeTestItem("OSS 配置", "SKIP", "未配置 OSS");
        }
        boolean hasEndpoint = ossProperties.getEndpoint() != null && !ossProperties.getEndpoint().isBlank();
        boolean hasBucket = ossProperties.getBucket() != null && !ossProperties.getBucket().isBlank();
        if (hasEndpoint && hasBucket) {
            return new SmokeTestItem("OSS 配置", "PASS",
                    "Endpoint: " + ossProperties.getEndpoint() + ", Bucket: " + ossProperties.getBucket());
        }
        return new SmokeTestItem("OSS 配置", "WARN", "配置不完整: endpoint=" + hasEndpoint + ", bucket=" + hasBucket);
    }

    private SmokeTestItem checkApiReachability() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + getServerPort() + "/api/v1/system/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = resp.statusCode() >= 200 && resp.statusCode() < 500;
            return new SmokeTestItem("API 自检", ok ? "PASS" : "WARN",
                    "HTTP " + resp.statusCode() + (ok ? " — 服务可达" : " — 响应异常"));
        } catch (Exception e) {
            return new SmokeTestItem("API 自检", "WARN", "自检请求失败: " + e.getMessage());
        }
    }

    private int getServerPort() {
        try {
            return Integer.parseInt(System.getProperty("server.port", "8045"));
        } catch (NumberFormatException e) {
            return 8045;
        }
    }

    public ApiTestResponse runApiTest(ApiTestRequest request) {
        ApiTestResponse response = new ApiTestResponse();
        long started = System.nanoTime();
        try {
            URI uri = URI.create(request.getUrl());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(request.getTimeoutMillis()));

            if (request.getHeaders() != null) {
                request.getHeaders().forEach(builder::header);
            }

            String body = request.getBody();
            boolean hasBody = body != null && !body.trim().isEmpty();
            String method = request.getMethod().toUpperCase(Locale.ROOT);

            switch (method) {
                case "POST":
                case "PUT":
                case "PATCH":
                    builder.method(method, hasBody
                            ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                            : HttpRequest.BodyPublishers.noBody());
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                default:
                    builder.GET();
                    break;
            }

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            response.setStatusCode(resp.statusCode());
            response.setBody(truncateBody(resp.body()));
            response.setLatencyMs(latencyMs);
            Map<String, String> headers = new LinkedHashMap<>();
            resp.headers().map().forEach((k, v) -> headers.put(k, String.join(", ", v)));
            response.setResponseHeaders(headers);
        } catch (Exception e) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            response.setError(e.getMessage());
            response.setLatencyMs(latencyMs);
            response.setStatusCode(0);
        }
        return response;
    }

    private String truncateBody(String body) {
        if (body == null) return null;
        return body.length() > 5000 ? body.substring(0, 5000) + "\n... (truncated)" : body;
    }

    public List<DataCheckItem> runDataCheck() {
        List<DataCheckItem> items = new ArrayList<>();
        items.add(checkScansWithoutOss());
        items.add(checkScansWithEmptyBah());
        items.add(checkScansWithNegativePages());
        items.add(checkMigrationStatusAnomaly());
        items.add(checkDuplicateBahSjh());
        items.add(checkOrphanRecords());
        return items;
    }

    private DataCheckItem checkScansWithoutOss() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("未迁移 OSS 记录");
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mr_scan WHERE (oss_url IS NULL OR oss_url = '') AND uploadflag != 0",
                    Long.class);
            item.setIssueCount(count != null ? count : 0);
            if (item.getIssueCount() == 0) {
                item.setStatus("PASS");
                item.setSummary("所有记录均已迁移到 OSS");
            } else {
                item.setStatus("WARN");
                item.setSummary("共 " + item.getIssueCount() + " 条记录尚未迁移到 OSS");
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }

    private DataCheckItem checkScansWithEmptyBah() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("空病案号记录");
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mr_scan WHERE BAH IS NULL OR BAH = ''", Long.class);
            item.setIssueCount(count != null ? count : 0);
            if (item.getIssueCount() == 0) {
                item.setStatus("PASS");
                item.setSummary("无空病案号记录");
            } else {
                item.setStatus("WARN");
                item.setSummary("共 " + item.getIssueCount() + " 条记录病案号为空");
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }

    private DataCheckItem checkScansWithNegativePages() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("异常页数记录");
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mr_scan WHERE pages IS NULL OR pages < 1", Long.class);
            item.setIssueCount(count != null ? count : 0);
            if (item.getIssueCount() == 0) {
                item.setStatus("PASS");
                item.setSummary("所有记录页数正常");
            } else {
                item.setStatus("WARN");
                item.setSummary("共 " + item.getIssueCount() + " 条记录页数异常（空值或小于1）");
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }

    private DataCheckItem checkMigrationStatusAnomaly() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("迁移状态不一致");
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mr_scan WHERE (oss_url IS NOT NULL AND oss_url != '') AND (migration_status IS NULL OR migration_status = 'not_migrated')",
                    Long.class);
            item.setIssueCount(count != null ? count : 0);
            if (item.getIssueCount() == 0) {
                item.setStatus("PASS");
                item.setSummary("迁移状态与 OSS URL 一致");
            } else {
                item.setStatus("FAIL");
                item.setSummary("共 " + item.getIssueCount() + " 条记录有 OSS URL 但状态标记为未迁移");
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }

    private DataCheckItem checkDuplicateBahSjh() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("重复病案号/上架号");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT BAH, SJH, COUNT(*) AS cnt FROM mr_scan WHERE BAH IS NOT NULL AND BAH != '' GROUP BY BAH, SJH HAVING COUNT(*) > 1");
            item.setIssueCount(rows.size());
            if (rows.isEmpty()) {
                item.setStatus("PASS");
                item.setSummary("无重复病案号/上架号组合");
            } else {
                item.setStatus("WARN");
                item.setSummary("共 " + rows.size() + " 组重复的病案号/上架号组合");
                item.setDetails(rows.stream()
                        .map(m -> "BAH=" + m.get("bah") + ", SJH=" + m.get("sjh") + ", 数量=" + m.get("cnt"))
                        .collect(Collectors.toList()));
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }

    private DataCheckItem checkOrphanRecords() {
        DataCheckItem item = new DataCheckItem();
        item.setCheckName("孤影记录");
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM mr_scan WHERE uploadflag = 0 AND (oss_url IS NOT NULL AND oss_url != '')",
                    Long.class);
            item.setIssueCount(count != null ? count : 0);
            if (item.getIssueCount() == 0) {
                item.setStatus("PASS");
                item.setSummary("无孤影记录");
            } else {
                item.setStatus("WARN");
                item.setSummary("共 " + item.getIssueCount() + " 条记录已删除（uploadflag=0）但仍有 OSS URL");
            }
        } catch (Exception e) {
            item.setStatus("ERROR");
            item.setSummary("查询失败: " + e.getMessage());
        }
        return item;
    }
}
