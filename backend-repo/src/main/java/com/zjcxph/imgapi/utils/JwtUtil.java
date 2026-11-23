package com.zjcxph.imgapi.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zjcxph.imgapi.common.AuthSession;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class JwtUtil {

    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String DOCUMENTATION_TOKEN_TYPE = "documentation";

    /**
     * 登录令牌的默认有效期（24 小时）。
     * <p>
     * 实际签发的有效期来自系统设置 {@code sessionExpireMinutes}，由调用方显式传入；
     * 该常量用于设置不可用时的兜底，以及不关心会话策略的调用点。
     * </p>
     */
    public static final long DEFAULT_ACCESS_TOKEN_EXPIRE_MILLIS = 24L * 60L * 60L * 1000L;

    private static volatile String secret = normalizeSecret(System.getenv("JWT_SECRET_KEY"));

    private JwtUtil() {
    }

    public static synchronized void configure(String configuredSecret) {
        String normalized = normalizeSecret(configuredSecret);
        if (normalized == null) {
            if (secret == null) {
                throw new IllegalStateException("JWT_SECRET_KEY must be configured");
            }
            return;
        }
        if (normalized.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET_KEY must contain at least 32 characters");
        }
        secret = normalized;
    }

    public static String getToken(String username) {
        AuthSession session = new AuthSession();
        session.setUsername(username);
        return getToken(session);
    }

    public static String getToken(AuthSession session) {
        return getToken(session, DEFAULT_ACCESS_TOKEN_EXPIRE_MILLIS, ACCESS_TOKEN_TYPE);
    }

    public static String getToken(AuthSession session, long expireMillis) {
        return getToken(session, expireMillis, ACCESS_TOKEN_TYPE);
    }

    public static String getToken(AuthSession session, long expireMillis, String tokenType) {
        if (session == null) {
            session = new AuthSession();
        }
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("expireMillis must be greater than 0");
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException("tokenType must not be blank");
        }

        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withClaim("tokenType", tokenType)
                .withExpiresAt(new Date(System.currentTimeMillis() + expireMillis))
                .withJWTId(UUID.randomUUID().toString());

        if (session.getId() != null) {
            builder.withClaim("id", session.getId());
        }
        if (session.getUsername() != null) {
            builder.withClaim("username", session.getUsername());
        }
        if (session.getDisplayName() != null) {
            builder.withClaim("displayName", session.getDisplayName());
        }
        if (session.getRoleCode() != null) {
            builder.withClaim("roleCode", session.getRoleCode());
        }
        if (session.getRoleName() != null) {
            builder.withClaim("roleName", session.getRoleName());
        }
        if (session.getStatus() != null) {
            builder.withClaim("status", session.getStatus());
        }
        builder.withClaim("mustChangePassword", session.isPasswordChangeRequired());
        builder.withClaim("passwordVersion", session.effectivePasswordVersion());

        List<String> permissions = session.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            builder.withArrayClaim("permissions", permissions.toArray(new String[0]));
        }
        return builder.sign(algorithm());
    }

    /**
     * 一次验签后返回令牌的类型、编号、过期时间与会话信息。
     *
     * <p>认证拦截器每个请求都会解析令牌；旧调用方式分别执行 {@link #getTokenType}、
     * {@link #getJti} 和 {@link #parseToken}，同一令牌被完整验签三次。档案袋这类一次
     * 打开数百个受保护请求的页面，应只付一次 HMAC 与 JSON 解析的成本。</p>
     */
    public static VerifiedToken verifyToken(String token) {
        DecodedJWT decodedJWT = verify(token);
        Date expiresAt = decodedJWT.getExpiresAt();
        return new VerifiedToken(
                decodedJWT.getClaim("tokenType").asString(),
                decodedJWT.getId(),
                expiresAt == null ? 0L : expiresAt.getTime(),
                toSession(decodedJWT));
    }

    /** 已验签令牌的认证信息；{@code expiresAtMillis} 为 0 表示令牌未声明过期时间。 */
    public record VerifiedToken(String tokenType, String jti, long expiresAtMillis, AuthSession session) {
    }

    public static AuthSession parseToken(String token) {
        return toSession(verify(token));
    }

    private static AuthSession toSession(DecodedJWT decodedJWT) {
        AuthSession session = new AuthSession();
        session.setId(decodedJWT.getClaim("id").isNull() ? null : decodedJWT.getClaim("id").asLong());
        session.setUsername(decodedJWT.getClaim("username").asString());
        session.setDisplayName(decodedJWT.getClaim("displayName").asString());
        session.setRoleCode(decodedJWT.getClaim("roleCode").asString());
        session.setRoleName(decodedJWT.getClaim("roleName").asString());
        session.setStatus(decodedJWT.getClaim("status").asString());
        session.setMustChangePassword(Boolean.TRUE.equals(decodedJWT.getClaim("mustChangePassword").asBoolean()));
        Integer passwordVersion = decodedJWT.getClaim("passwordVersion").asInt();
        session.setPasswordVersion(passwordVersion == null || passwordVersion < 1 ? 1 : passwordVersion);
        String[] permissions = decodedJWT.getClaim("permissions").asArray(String.class);
        if (permissions != null) {
            session.setPermissions(java.util.Arrays.asList(permissions));
        }
        return session;
    }

    public static String getTokenType(String token) {
        return verify(token).getClaim("tokenType").asString();
    }

    public static String getJti(String token) {
        return verify(token).getId();
    }

    public static long getExpirationMillis(String token) {
        return verify(token).getExpiresAt().getTime();
    }

    /**
     * 从 JWT 签名密钥派生出用途隔离的子密钥（HMAC-SHA256）。
     *
     * <p>影像访问 Cookie 等与访问令牌同生命周期的凭据使用子密钥签名：轮换 {@code JWT_SECRET_KEY}
     * 会同时作废它们，且子密钥与 JWT 签名不会互相替代。</p>
     */
    public static byte[] deriveKey(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must not be blank");
        }
        String currentSecret = secret;
        if (currentSecret == null) {
            throw new IllegalStateException("JWT_SECRET_KEY must be configured before deriving keys");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(currentSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(("MRR-DERIVED-KEY\u0000" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    private static DecodedJWT verify(String token) {
        return JWT.require(algorithm()).build().verify(token);
    }

    private static Algorithm algorithm() {
        String currentSecret = secret;
        if (currentSecret == null) {
            throw new IllegalStateException("JWT_SECRET_KEY must be configured before issuing or verifying tokens");
        }
        return Algorithm.HMAC256(currentSecret);
    }

    private static String normalizeSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
