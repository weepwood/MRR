package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.config.IntegrationProperties;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveIntegrationStatusResponse;
import com.zjcxph.imgapi.utils.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 只面向 MRR 管理员的外部影像集成诊断接口。
 *
 * <p>该接口用于告诉认证测试台“是否启用、客户端是否配置、当前来源 IP 是否允许”，
 * 不返回或校验 HMAC Secret 明文。</p>
 */
@RestController
@RequestMapping("/api/v1/integration/archive")
public class ExternalArchiveIntegrationStatusController {

    private final IntegrationProperties properties;

    public ExternalArchiveIntegrationStatusController(IntegrationProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/status")
    @RequirePermissions({"user:manage"})
    public Result<ExternalArchiveIntegrationStatusResponse> status(HttpServletRequest request) {
        String requestIp = IpUtil.getClientIp(request);
        List<ExternalArchiveIntegrationStatusResponse.ClientStatus> clients = safeClients().stream()
                .map(client -> new ExternalArchiveIntegrationStatusResponse.ClientStatus(
                        client.getClientId(),
                        client.isEnabled(),
                        StringUtils.hasText(client.getSecret()),
                        safeAllowedIps(client),
                        isIpAllowed(client, requestIp)
                ))
                .toList();

        return Result.success(new ExternalArchiveIntegrationStatusResponse(
                properties.isEnabled(),
                requestIp,
                properties.getTicketTtlSeconds(),
                properties.getSessionTtlSeconds(),
                properties.getTimestampToleranceSeconds(),
                properties.getMaxArchivesPerTicket(),
                clients
        ));
    }

    private List<IntegrationProperties.Client> safeClients() {
        return properties.getClients() == null ? List.of() : properties.getClients();
    }

    private List<String> safeAllowedIps(IntegrationProperties.Client client) {
        return client.getAllowedIps() == null
                ? List.of()
                : client.getAllowedIps().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean isIpAllowed(IntegrationProperties.Client client, String requestIp) {
        List<String> allowedIps = safeAllowedIps(client);
        if (allowedIps.isEmpty()) {
            return true;
        }
        return allowedIps.stream().anyMatch(value -> "*".equals(value) || value.equals(requestIp));
    }
}
