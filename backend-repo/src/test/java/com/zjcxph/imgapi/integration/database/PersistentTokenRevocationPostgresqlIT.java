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
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PersistentTokenRevocationPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;

    @Test void revokedTokenRemainsRevokedAfterRecreatingTheService() {
        new TokenBlacklist(jdbc).revoke("synthetic-token-id", System.currentTimeMillis() + 60000);
        assertThat(new TokenBlacklist(jdbc).isRevoked("synthetic-token-id")).isTrue();
        assertThat(new TokenBlacklist(jdbc).isRevoked("other-synthetic-id")).isFalse();
    }
}
