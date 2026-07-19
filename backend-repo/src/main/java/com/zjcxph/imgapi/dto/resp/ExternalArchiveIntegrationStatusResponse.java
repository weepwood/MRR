package com.zjcxph.imgapi.dto.resp;

import java.util.List;

/**
 * 管理员认证测试台使用的外部影像集成状态。
 *
 * <p>只返回是否配置等诊断信息，绝不返回 HMAC Secret 明文。</p>
 */
public record ExternalArchiveIntegrationStatusResponse(
        boolean enabled,
        String requestIp,
        int ticketTtlSeconds,
        int sessionTtlSeconds,
        int timestampToleranceSeconds,
        int maxArchivesPerTicket,
        List<ClientStatus> clients
) {

    public record ClientStatus(
            String clientId,
            boolean enabled,
            boolean secretConfigured,
            List<String> allowedIps,
            boolean requestIpAllowed
    ) {
    }
}
