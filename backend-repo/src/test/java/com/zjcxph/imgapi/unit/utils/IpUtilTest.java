package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.IpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpUtil 可信代理解析测试")
class IpUtilTest {

    @Test
    @DisplayName("直接访问后端时忽略伪造的转发头")
    void directRequestIgnoresForwardedHeaders() {
        MockHttpServletRequest request = request("10.10.20.30");
        request.addHeader("X-Forwarded-For", "192.168.1.10");
        request.addHeader("X-Real-IP", "192.168.1.11");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("10.10.20.30");
        assertThat(IpUtil.getRemoteIp(request)).isEqualTo("10.10.20.30");
    }

    @Test
    @DisplayName("本机 IPv4 Nginx 代理可以传递真实客户端 IP")
    void loopbackProxyUsesForwardedFor() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.20.30.40");
        request.addHeader("X-Real-IP", "10.20.30.41");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("10.20.30.40");
    }

    @Test
    @DisplayName("X-Forwarded-For 只读取首个合法地址")
    void forwardedForUsesFirstAddress() {
        MockHttpServletRequest request = request("::1");
        request.addHeader("X-Forwarded-For", "10.1.2.3, 172.16.0.8");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("10.1.2.3");
    }

    @Test
    @DisplayName("非法 XFF 回退到合法 X-Real-IP")
    void malformedForwardedForFallsBackToRealIp() {
        MockHttpServletRequest request = request("0:0:0:0:0:0:0:1");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        request.addHeader("X-Real-IP", "2001:db8::18");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("2001:db8::18");
    }

    @Test
    @DisplayName("过长或非法转发头不会覆盖代理地址")
    void invalidForwardedHeadersFallBackToRemoteAddress() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1".repeat(300));
        request.addHeader("X-Real-IP", "999.1.1.1");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("非本机代理即使提供合法 IPv6 头也不能伪造来源")
    void nonLoopbackProxyCannotSpoofIpv6() {
        MockHttpServletRequest request = request("192.168.10.20");
        request.addHeader("X-Forwarded-For", "2001:db8::20");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("192.168.10.20");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
