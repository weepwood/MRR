package com.zjcxph.imgapi.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zjcxph.imgapi.common.AuthSession;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class JwtUtil {

    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String DOCUMENTATION_TOKEN_TYPE = "documentation";

    private static final String SECRET;
    private static final long EXPIRE_MILLIS = 24L * 60L * 60L * 1000L;

    static {
        String env = System.getenv("JWT_SECRET_KEY");
        if (env == null || env.isBlank()) {
            throw new ExceptionInInitializerError("JWT_SECRET_KEY environment variable must be set");
        }
        SECRET = env;
    }

    private JwtUtil() {
    }

    public static String getToken(String username) {
        AuthSession session = new AuthSession();
        session.setUsername(username);
        return getToken(session);
    }

    public static String getToken(AuthSession session) {
        return getToken(session, EXPIRE_MILLIS, ACCESS_TOKEN_TYPE);
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
                .withJWTId(UUID.randomUUID().toString()); // 用于登出撤销

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

        List<String> permissions = session.getPermissions();
        if (permissions != null && !permissions.isEmpty()) {
            builder.withArrayClaim("permissions", permissions.toArray(new String[0]));
        }
        return builder.sign(Algorithm.HMAC256(SECRET));
    }

    public static AuthSession parseToken(String token) {
        DecodedJWT decodedJWT = verify(token);
        AuthSession session = new AuthSession();
        session.setId(decodedJWT.getClaim("id").isNull() ? null : decodedJWT.getClaim("id").asLong());
        session.setUsername(decodedJWT.getClaim("username").asString());
        session.setDisplayName(decodedJWT.getClaim("displayName").asString());
        session.setRoleCode(decodedJWT.getClaim("roleCode").asString());
        session.setRoleName(decodedJWT.getClaim("roleName").asString());
        session.setStatus(decodedJWT.getClaim("status").asString());
        String[] permissions = decodedJWT.getClaim("permissions").asArray(String.class);
        if (permissions != null) {
            session.setPermissions(java.util.Arrays.asList(permissions));
        }
        return session;
    }

    public static String getTokenType(String token) {
        return verify(token).getClaim("tokenType").asString();
    }

    /**
     * 从原始 token 字符串中提取 jti，用于黑名单检查。
     */
    public static String getJti(String token) {
        return verify(token).getId();
    }

    /**
     * 获取 token 的过期时间戳（毫秒），用于黑名单条目 TTL。
     */
    public static long getExpirationMillis(String token) {
        return verify(token).getExpiresAt().getTime();
    }

    private static DecodedJWT verify(String token) {
        return JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
    }
}
