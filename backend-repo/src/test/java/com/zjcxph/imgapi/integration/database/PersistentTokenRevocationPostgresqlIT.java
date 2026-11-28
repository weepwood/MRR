package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.security.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistentTokenRevocationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void revokedTokenRemainsRevokedAfterRecreatingTheService() {
        new TokenBlacklist(jdbc).revoke("synthetic-token-id", System.currentTimeMillis() + 60000);
        assertThat(new TokenBlacklist(jdbc).isRevoked("synthetic-token-id")).isTrue();
        assertThat(new TokenBlacklist(jdbc).isRevoked("other-synthetic-id")).isFalse();
    }

    @Autowired com.zjcxph.imgapi.mapper.AuthUserMapper users;

    @Test void credentialWritesRequireCurrentVersionAndActiveState() {
        String username = "synthetic-credential-" + java.util.UUID.randomUUID();
        Long id = jdbc.queryForObject("""
                INSERT INTO app.mr_auth_user(username,password_hash,role_code,status,password_version)
                VALUES (?, 'synthetic-placeholder', 'READER', 'active', 4) RETURNING id
                """, Long.class, username);
        var expires = java.time.LocalDateTime.now().plusHours(1);
        assertThat(users.resetPassword(id, "synthetic-reset-placeholder", expires, id, 4)).isEqualTo(1);
        assertThat(users.changePassword(id, "synthetic-stale-placeholder", 4, false)).isZero();
        assertThat(users.resetPassword(id, "synthetic-stale-placeholder", expires, id, 4)).isZero();
        assertThat(users.findById(id).effectivePasswordVersion()).isEqualTo(5);
        assertThat(users.findById(id).isPasswordChangeRequired()).isTrue();
        assertThat(users.changePassword(id, "synthetic-wrong-mode", 5, false)).isZero();
        assertThat(users.changePassword(id, "synthetic-first-placeholder", 5, true)).isEqualTo(1);
        assertThat(users.findById(id).effectivePasswordVersion()).isEqualTo(6);
        assertThat(users.findById(id).isPasswordChangeRequired()).isFalse();
        assertThat(users.changePassword(id, "synthetic-normal-placeholder", 6, false)).isEqualTo(1);
        assertThat(users.findById(id).effectivePasswordVersion()).isEqualTo(7);
        assertThat(users.resetPassword(id, "synthetic-expired-placeholder",
                java.time.LocalDateTime.now().minusMinutes(1), id, 7)).isEqualTo(1);
        assertThat(users.changePassword(id, "synthetic-late-placeholder", 8, true)).isZero();
        assertThat(users.updateStatus(id, "disabled")).isEqualTo(1);
        assertThat(users.resetPassword(id, "synthetic-disabled-placeholder", expires, id, 9)).isZero();
        assertThat(users.changePassword(id, "synthetic-disabled-placeholder", 9, true)).isZero();
        var unchanged = users.findById(id);
        assertThat(unchanged.effectivePasswordVersion()).isEqualTo(9);
        assertThat(unchanged.getStatus()).isEqualTo("disabled");
        assertThat(unchanged.getPasswordHash()).isEqualTo("synthetic-expired-placeholder");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void actualStatusTransitionsInvalidatePreviousCredentialVersions(boolean editProfile) {
        String username = "synthetic-state-" + java.util.UUID.randomUUID();
        Long id = jdbc.queryForObject("""
                INSERT INTO app.mr_auth_user(username,password_hash,role_code,status,password_version)
                VALUES (?, 'synthetic-placeholder', 'READER', 'active', 4) RETURNING id
                """, Long.class, username);
        var user = users.findById(id);
        user.setDisplayName("synthetic-display");
        assertThat(users.updateUser(user)).isEqualTo(1);
        assertThat(users.findById(id).effectivePasswordVersion()).isEqualTo(4);
        if (editProfile) {
            user.setStatus("disabled");
            assertThat(users.updateUser(user)).isEqualTo(1);
        } else {
            assertThat(users.updateStatus(id, "disabled")).isEqualTo(1);
        }
        var disabled = users.findById(id);
        assertThat(disabled.getStatus()).isEqualTo("disabled");
        assertThat(disabled.effectivePasswordVersion()).isEqualTo(5);
        assertThat(users.updateStatus(id, "disabled")).isEqualTo(1);
        assertThat(users.findById(id).effectivePasswordVersion()).isEqualTo(5);
        disabled.setStatus("active");
        assertThat(users.updateUser(disabled)).isEqualTo(1);
        var active = users.findById(id);
        assertThat(active.getStatus()).isEqualTo("active");
        assertThat(active.effectivePasswordVersion()).isEqualTo(6);
        assertThat(active.getPasswordHash()).isEqualTo("synthetic-placeholder");
        assertThat(active.isPasswordChangeRequired()).isFalse();
    }
}
