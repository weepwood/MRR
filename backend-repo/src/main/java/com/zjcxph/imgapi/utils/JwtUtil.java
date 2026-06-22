package com.zjcxph.imgapi.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zjcxph.imgapi.common.AuthSession;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JwtUtil {

    private static final String SECRET = Optional.ofNullable(System.getenv("JWT_SECRET_KEY"))
            .filter(value -> !value.isBlank())
            .orElse("sbkedbkvuirkhkpwzetralhtaenrqlhio");
    private static final long EXPIRE_MILLIS = 24L * 60L * 60L * 1000L;

    private JwtUtil() {
    }

    public static String getToken(String username) {
        AuthSession session = new AuthSession();
        session.setUsername(username);
        return getToken(session);
    }

    public static String getToken(AuthSession session) {
        if (session == null) {
            session = new AuthSession();
        }

        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
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
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
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

    /**
     * 从原始 token 字符串中提取 jti，用于黑名单检查。
     */
    public static String getJti(String token) {
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
        return decodedJWT.getId();
    }

    /**
     * 获取 token 的过期时间戳（毫秒），用于黑名单条目 TTL。
     */
    public static long getExpirationMillis(String token) {
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(SECRET)).build().verify(token);
        return decodedJWT.getExpiresAt().getTime();
    }
}
