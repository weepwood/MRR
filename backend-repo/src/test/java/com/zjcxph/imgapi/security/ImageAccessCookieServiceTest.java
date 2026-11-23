package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageAccessCookieService 影像访问 Cookie")
class ImageAccessCookieServiceTest {

    private static final String TOKEN_HASH = "a".repeat(32) + "0123456789abcdef0123456789abcdef";
    private static final long NOW_MILLIS = 1_800_000_000_000L;
    private static final long TOKEN_EXPIRES_MILLIS = NOW_MILLIS + 3_600_000L;

    private final AtomicLong now = new AtomicLong(NOW_MILLIS);
    private ImageAccessCookieService service;

    @BeforeEach
    void setUp() {
        JwtUtil.configure("image-access-cookie-test-secret-0123456789abcdef");
        service = new ImageAccessCookieService(gateway(), now::get);
    }

    @Test
    @DisplayName("档案袋列表请求签发 HttpOnly、SameSite=Strict、与令牌同寿命的 Cookie")
    void issuesCookieForArchiveListRequest() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issueIfApplicable(get("/api/v1/img/search"), response, reader(), TOKEN_HASH, TOKEN_EXPIRES_MILLIS);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(ImageAccessCookieService.COOKIE_NAME + "=v1.7.3." + TOKEN_HASH + ".");
        assertThat(header).contains("HttpOnly", "SameSite=Strict", "Path=/", "Max-Age=3600").doesNotContain("Secure");

        ImageAccessCookieService.Grant grant = service.verify(cookieValue(header));
        assertThat(grant).isEqualTo(new ImageAccessCookieService.Grant(7L, 3, TOKEN_HASH, TOKEN_EXPIRES_MILLIS / 1000L));
    }

    @Test
    @DisplayName("HTTPS 反向代理后签发 Secure Cookie")
    void marksCookieSecureBehindHttpsProxy() {
        MockHttpServletRequest request = get("/api/v1/img/00789508");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issueIfApplicable(request, response, reader(), TOKEN_HASH, TOKEN_EXPIRES_MILLIS);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Secure");
    }

    @Test
    @DisplayName("非签发接口、非 GET、无 record:read、令牌已过期或直连模式均不签发")
    void doesNotIssueOutsideItsScope() {
        AuthSession withoutRead = reader();
        withoutRead.setPermissions(List.of("statistics:read"));
        ImageAccessCookieService direct = new ImageAccessCookieService(new ImageDeliveryProperties(), now::get);
        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/img/search");

        for (Runnable attempt : List.<Runnable>of(
                () -> assertNoCookie(service, get("/api/v1/statistics/dashboard"), reader(), TOKEN_EXPIRES_MILLIS),
                () -> assertNoCookie(service, get("/api/v1/img/content/5"), reader(), TOKEN_EXPIRES_MILLIS),
                () -> assertNoCookie(service, post, reader(), TOKEN_EXPIRES_MILLIS),
                () -> assertNoCookie(service, get("/api/v1/img/search"), withoutRead, TOKEN_EXPIRES_MILLIS),
                () -> assertNoCookie(service, get("/api/v1/img/search"), reader(), NOW_MILLIS),
                () -> assertNoCookie(direct, get("/api/v1/img/search"), reader(), TOKEN_EXPIRES_MILLIS))) {
            attempt.run();
        }
    }

    @Test
    @DisplayName("篡改任一字段、换用其他密钥或格式错误都被拒绝")
    void rejectsTamperedOrMalformedValues() {
        String value = issuedValue();
        String[] parts = value.split("\\.");

        assertThat(service.verify(replacePart(parts, 1, "8"))).isNull();
        assertThat(service.verify(replacePart(parts, 2, "4"))).isNull();
        assertThat(service.verify(replacePart(parts, 3, "b".repeat(64)))).isNull();
        assertThat(service.verify(replacePart(parts, 4, String.valueOf(Long.parseLong(parts[4]) + 3600)))).isNull();
        assertThat(service.verify(replacePart(parts, 0, "v2"))).isNull();
        assertThat(service.verify(value + ".extra")).isNull();
        assertThat(service.verify("not-a-cookie")).isNull();
        assertThat(service.verify("x".repeat(300))).isNull();
        assertThat(service.verify(null)).isNull();

        JwtUtil.configure("another-image-access-secret-0123456789abcdef");
        assertThat(service.verify(value)).isNull();
    }

    @Test
    @DisplayName("到达令牌过期时间后 Cookie 失效")
    void expiresWithTheAccessToken() {
        String value = issuedValue();
        assertThat(service.verify(value)).isNotNull();

        now.set(TOKEN_EXPIRES_MILLIS);

        assertThat(service.verify(value)).isNull();
    }

    @Test
    @DisplayName("只把 GET/HEAD 内容网关请求识别为可用 Cookie 认证")
    void recognisesOnlyContentGatewayRequests() {
        MockHttpServletRequest withContext = get("/mrr/api/v1/img/content/12");
        withContext.setContextPath("/mrr");

        assertThat(service.isContentRequest(get("/api/v1/img/content/12"))).isTrue();
        assertThat(service.isContentRequest(new MockHttpServletRequest("HEAD", "/api/v1/img/content/12"))).isTrue();
        assertThat(service.isContentRequest(withContext)).isTrue();
        assertThat(service.isContentRequest(get("/api/v1/img/content/abc"))).isFalse();
        assertThat(service.isContentRequest(get("/api/v1/img/content/12/extra"))).isFalse();
        assertThat(service.isContentRequest(get("/api/v1/img/search"))).isFalse();
        assertThat(service.isContentRequest(new MockHttpServletRequest("DELETE", "/api/v1/img/content/12"))).isFalse();
        assertThat(new ImageAccessCookieService(new ImageDeliveryProperties(), now::get)
                .isContentRequest(get("/api/v1/img/content/12"))).isFalse();
    }

    @Test
    @DisplayName("读取 Cookie 并在登出时清除")
    void readsAndExpiresCookie() {
        MockHttpServletRequest request = get("/api/v1/img/content/12");
        request.setCookies(new Cookie("other", "x"), new Cookie(ImageAccessCookieService.COOKIE_NAME, "v1.value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.expire(request, response);

        assertThat(service.findCookie(request)).isEqualTo("v1.value");
        assertThat(service.findCookie(get("/api/v1/img/content/12"))).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(ImageAccessCookieService.COOKIE_NAME + "=;")
                .contains("Max-Age=0", "HttpOnly");
    }

    private String issuedValue() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.issueIfApplicable(get("/api/v1/img/search"), response, reader(), TOKEN_HASH, TOKEN_EXPIRES_MILLIS);
        return cookieValue(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    private static void assertNoCookie(ImageAccessCookieService target, MockHttpServletRequest request,
                                       AuthSession session, long tokenExpiresAtMillis) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        target.issueIfApplicable(request, response, session, TOKEN_HASH, tokenExpiresAtMillis);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .as("%s %s", request.getMethod(), request.getRequestURI())
                .isNull();
    }

    private static String cookieValue(String header) {
        String pair = header.substring(0, header.indexOf(';'));
        return pair.substring(pair.indexOf('=') + 1);
    }

    private static String replacePart(String[] parts, int index, String replacement) {
        String[] copy = parts.clone();
        copy[index] = replacement;
        return String.join(".", copy);
    }

    private static MockHttpServletRequest get(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static AuthSession reader() {
        AuthSession session = new AuthSession();
        session.setId(7L);
        session.setUsername("synthetic-reader");
        session.setRoleCode("READER");
        session.setPasswordVersion(3);
        session.setPermissions(List.of("record:read"));
        return session;
    }

    private static ImageDeliveryProperties gateway() {
        ImageDeliveryProperties properties = new ImageDeliveryProperties();
        properties.setMode(ImageDeliveryProperties.Mode.GATEWAY);
        return properties;
    }
}
