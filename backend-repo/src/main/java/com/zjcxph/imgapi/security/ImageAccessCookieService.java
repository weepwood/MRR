package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.config.ImageDeliveryProperties;
import com.zjcxph.imgapi.utils.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 档案袋影像访问 Cookie（ADR-0009）。
 *
 * <p>{@code <img>} 无法携带 Bearer 请求头。网关模式下，后端在影像列表接口的响应中签发一个
 * HttpOnly Cookie，浏览器取图时自动携带，内容网关据此认证：</p>
 *
 * <pre>
 * MRR_IMAGE_ACCESS = v1.{userId}.{passwordVersion}.{tokenHash}.{expEpochSeconds}.{base64url(HMAC-SHA256)}
 * </pre>
 *
 * <ul>
 *     <li>签名子密钥由 {@link JwtUtil#deriveKey(String)} 从 JWT 密钥派生，轮换 JWT 密钥即全部作废；</li>
 *     <li>{@code tokenHash} 是签发时访问令牌的撤销表键：登出或强制下线后立即失效；</li>
 *     <li>{@code passwordVersion} 与数据库比对：改密、重置密码后立即失效；</li>
 *     <li>过期时间等于访问令牌过期时间，不会比令牌活得更久；</li>
 *     <li>只在 {@code GET/HEAD /api/v1/img/content/{id}} 上被接受，不能用于其他任何接口；</li>
 *     <li>{@code SameSite=Strict}：第三方页面嵌入影像或跨站跳转时浏览器不会携带。</li>
 * </ul>
 *
 * <p>本类只做格式、签名和过期校验，不访问数据库；撤销、账号状态与密码版本由
 * {@code LoginInterceptor} 复用现有认证链路检查。</p>
 */
@Component
public class ImageAccessCookieService {

    public static final String COOKIE_NAME = "MRR_IMAGE_ACCESS";
    public static final String ACCESS_MODE = "image-access-cookie";

    static final String VERSION = "v1";
    private static final String KEY_PURPOSE = "MRR-IMAGE-ACCESS-V1";
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_COOKIE_LENGTH = 256;
    private static final Pattern CONTENT_PATH = Pattern.compile("^/api/v1/img/content/\\d{1,10}$");
    /** 档案袋列表、单图地址与按病案号查询；只有这些接口会返回网关地址，因此只在这里签发。 */
    private static final Pattern ISSUING_PATH = Pattern.compile("^/api/v1/img/(search|url/\\d{1,10}|\\d{1,8})$");
    private static final Pattern TOKEN_HASH = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern POSITIVE_NUMBER = Pattern.compile("^[1-9]\\d{0,18}$");

    private final ImageDeliveryProperties properties;
    private final LongSupplier currentTimeMillis;

    @Autowired
    public ImageAccessCookieService(ImageDeliveryProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    ImageAccessCookieService(ImageDeliveryProperties properties, LongSupplier currentTimeMillis) {
        this.properties = properties == null ? new ImageDeliveryProperties() : properties;
        this.currentTimeMillis = currentTimeMillis;
    }

    /** 直连模式实例：不签发、不接受 Cookie。用于未注入配置的构造路径与测试。 */
    public static ImageAccessCookieService disabled() {
        return new ImageAccessCookieService(new ImageDeliveryProperties());
    }

    /** 已验证的影像访问凭据。 */
    public record Grant(long userId, int passwordVersion, String tokenHash, long expiresAtEpochSeconds) {
    }

    public boolean isEnabled() {
        return properties.isGateway();
    }

    /** 是否为可用 Cookie 认证的内容网关请求。 */
    public boolean isContentRequest(HttpServletRequest request) {
        if (!isEnabled() || request == null) {
            return false;
        }
        String method = request.getMethod();
        return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                && CONTENT_PATH.matcher(normalizedPath(request)).matches();
    }

    public String findCookie(HttpServletRequest request) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 在 Bearer 认证成功后为档案袋列表类请求签发 Cookie。
     *
     * @param tokenHash            当前访问令牌的 {@link TokenBlacklist#tokenHash(String)}
     * @param tokenExpiresAtMillis 当前访问令牌过期时间；不晚于该时间失效
     */
    public void issueIfApplicable(HttpServletRequest request,
                                  HttpServletResponse response,
                                  AuthSession session,
                                  String tokenHash,
                                  long tokenExpiresAtMillis) {
        if (!isEnabled() || request == null || response == null || session == null
                || !"GET".equalsIgnoreCase(request.getMethod())
                || !ISSUING_PATH.matcher(normalizedPath(request)).matches()
                || session.getId() == null || session.getId() <= 0
                || !session.hasPermission(Permissions.RECORD_READ)
                || tokenHash == null || !TOKEN_HASH.matcher(tokenHash).matches()) {
            return;
        }
        long nowSeconds = currentTimeMillis.getAsLong() / 1000L;
        long expiresAtSeconds = tokenExpiresAtMillis / 1000L;
        if (expiresAtSeconds <= nowSeconds) {
            return;
        }
        String payload = String.join(".", VERSION, String.valueOf(session.getId()),
                String.valueOf(session.effectivePasswordVersion()), tokenHash, String.valueOf(expiresAtSeconds));
        String value = payload + "." + sign(payload);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, value,
                Duration.ofSeconds(expiresAtSeconds - nowSeconds)).toString());
    }

    /** 校验格式、签名与过期时间；任何不符合均返回 {@code null}。 */
    public Grant verify(String cookieValue) {
        if (!StringUtils.hasText(cookieValue) || cookieValue.length() > MAX_COOKIE_LENGTH) {
            return null;
        }
        String[] parts = cookieValue.split("\\.", -1);
        if (parts.length != 6 || !VERSION.equals(parts[0])
                || !POSITIVE_NUMBER.matcher(parts[1]).matches()
                || !POSITIVE_NUMBER.matcher(parts[2]).matches() || parts[2].length() > 9
                || !TOKEN_HASH.matcher(parts[3]).matches()
                || !POSITIVE_NUMBER.matcher(parts[4]).matches()) {
            return null;
        }
        String payload = String.join(".", parts[0], parts[1], parts[2], parts[3], parts[4]);
        byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, parts[5].getBytes(StandardCharsets.US_ASCII))) {
            return null;
        }
        long expiresAtSeconds = Long.parseLong(parts[4]);
        if (expiresAtSeconds <= currentTimeMillis.getAsLong() / 1000L) {
            return null;
        }
        return new Grant(Long.parseLong(parts[1]), Integer.parseInt(parts[2]), parts[3], expiresAtSeconds);
    }

    /** 登出时清除 Cookie。即使未清除，令牌撤销也会使其失效。 */
    public void expire(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(HttpServletRequest request, String value, Duration maxAge) {
        boolean secure = request != null && (request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        // Path=/：开发环境经 /proxy 前缀转发、反向代理加前缀时路径不同；Cookie 只在内容网关路径被接受。
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(JwtUtil.deriveKey(KEY_PURPOSE), MAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }
}
