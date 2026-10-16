package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.ClientEnvironmentResponse;
import com.zjcxph.imgapi.utils.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 登录页匿名客户端环境。
 *
 * <p>登录页需要在这些信息进入认证流程前展示「我这台机器从哪接进来」。客户端无法自行得知
 * 服务端看到的是哪个地址，因此必须由服务端回显。返回内容严格限于本次请求的观察结果，
 * 便于用户把地址报给管理员做接入排查。</p>
 *
 * <p>路径落在既有匿名前缀 {@code /api/v1/public/config/**} 之下，因此不额外扩大
 * {@code ApiAccessPolicy} 的公开面，也不需要声明方法级认证策略。</p>
 */
@RestController
@RequestMapping("/api/v1/public/config")
@Tag(name = "Public Client Environment", description = "登录页客户端接入环境")
public class PublicClientEnvironmentController {

    private static final String IP_VERSION_IPV4 = "IPV4";
    private static final String IP_VERSION_IPV6 = "IPV6";
    private static final String IP_VERSION_UNKNOWN = "UNKNOWN";

    @Operation(summary = "获取客户端接入环境")
    @GetMapping("/client-environment")
    public Result<ClientEnvironmentResponse> clientEnvironment(HttpServletRequest request) {
        String effectiveIp = IpUtil.getClientIp(request);
        String remoteIp = IpUtil.getRemoteIp(request);
        return Result.success(new ClientEnvironmentResponse(
                effectiveIp,
                resolveIpVersion(effectiveIp),
                effectiveIp != null && !effectiveIp.equals(remoteIp),
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ZoneId.systemDefault().getId()
        ));
    }

    private String resolveIpVersion(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return IP_VERSION_UNKNOWN;
        }
        return ip.indexOf(':') >= 0 ? IP_VERSION_IPV6 : IP_VERSION_IPV4;
    }
}
