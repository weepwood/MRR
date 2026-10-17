package com.zjcxph.imgapi.dto.resp;

/**
 * 登录页匿名展示的客户端接入环境。
 *
 * <p>只包含服务端在本次请求中实际观察到的事实：客户端地址、地址类型、是否经反向代理转发，
 * 以及服务器当前时间与时区。不含任何系统配置、账号信息或内部网络拓扑细节。</p>
 *
 * @param clientIp     服务端识别到的有效客户端地址（已按 {@code IpUtil} 规则处理转发头）
 * @param ipVersion    地址类型，取值 {@code IPV4} / {@code IPV6} / {@code UNKNOWN}
 * @param behindProxy  有效地址是否来自转发头（即请求经由回环地址上的反向代理接入）
 * @param serverTime   服务器当前时间，ISO-8601 带偏移量
 * @param serverZoneId 服务器时区标识，例如 {@code Asia/Shanghai}
 */
public record ClientEnvironmentResponse(
        String clientIp,
        String ipVersion,
        boolean behindProxy,
        String serverTime,
        String serverZoneId
) {
}
