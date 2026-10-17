package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.ClientEnvironmentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("登录页客户端环境接口")
class PublicClientEnvironmentControllerTest {

    private final PublicClientEnvironmentController controller = new PublicClientEnvironmentController();

    @Test
    @DisplayName("直连客户端回显自身地址且不标记为代理接入")
    void shouldReportDirectClientAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.10.24");

        ClientEnvironmentResponse data = controller.clientEnvironment(request).getData();

        assertThat(data.clientIp()).isEqualTo("192.168.10.24");
        assertThat(data.ipVersion()).isEqualTo("IPV4");
        assertThat(data.behindProxy()).isFalse();
    }

    @Test
    @DisplayName("经回环反向代理接入时优先使用转发头中的首个地址")
    void shouldPreferForwardedAddressBehindLoopbackProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "10.20.30.40, 127.0.0.1");

        ClientEnvironmentResponse data = controller.clientEnvironment(request).getData();

        assertThat(data.clientIp()).isEqualTo("10.20.30.40");
        assertThat(data.behindProxy()).isTrue();
    }

    @Test
    @DisplayName("IPv6 客户端被识别为 IPv6 地址类型")
    void shouldDetectIpv6Client() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("fe80::a4c1:9ff:fe12:3456");

        ClientEnvironmentResponse data = controller.clientEnvironment(request).getData();

        assertThat(data.ipVersion()).isEqualTo("IPV6");
        assertThat(data.behindProxy()).isFalse();
    }

    @Test
    @DisplayName("地址不可识别时回退为 UNKNOWN 而不是抛出异常")
    void shouldFallbackToUnknownWhenAddressMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("unknown");

        ClientEnvironmentResponse data = controller.clientEnvironment(request).getData();

        assertThat(data.clientIp()).isEqualTo("unknown");
        assertThat(data.ipVersion()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("返回可解析的服务器时间与非空时区，且响应封装为成功")
    void shouldReturnParseableServerTimeAndZone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.5.9");

        Result<ClientEnvironmentResponse> result = controller.clientEnvironment(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().serverZoneId()).isNotBlank();
        assertThat(OffsetDateTime.parse(result.getData().serverTime())).isNotNull();
    }
}
