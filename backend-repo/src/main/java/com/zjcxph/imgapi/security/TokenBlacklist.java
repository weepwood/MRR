package com.zjcxph.imgapi.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Timestamp;

/** PostgreSQL 是撤销事实来源；查询故障向上传播，认证拦截器按 503 拒绝。 */
@Component
public class TokenBlacklist {
    private final JdbcTemplate jdbc;
    public TokenBlacklist(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void revoke(String jti, long expiryTimestamp) {
        if (jti == null || jti.isBlank()) throw new IllegalArgumentException("Token identifier missing");
        jdbc.update("INSERT INTO app.token_revocation(token_hash,expires_at) VALUES (?,?) "
                + "ON CONFLICT(token_hash) DO UPDATE SET expires_at=GREATEST(token_revocation.expires_at,EXCLUDED.expires_at)",
                hash(jti), new Timestamp(expiryTimestamp));
    }

    /** 令牌编号在撤销表中的存储形式（SHA-256 十六进制），供不携带原始 jti 的派生凭据引用。 */
    public static String tokenHash(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Token identifier missing");
        }
        return hash(jti);
    }

    /** 按 {@link #tokenHash(String)} 查询撤销状态；格式非法按已撤销处理（fail-closed）。 */
    public boolean isRevokedHash(String tokenHash) {
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.token_revocation "
                + "WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP)", Boolean.class, tokenHash));
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app.token_revocation "
                + "WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP)", Boolean.class, hash(jti)));
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanExpired() {
        jdbc.update("DELETE FROM app.token_revocation WHERE token_hash IN "
                + "(SELECT token_hash FROM app.token_revocation WHERE expires_at<CURRENT_TIMESTAMP LIMIT 1000)");
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
