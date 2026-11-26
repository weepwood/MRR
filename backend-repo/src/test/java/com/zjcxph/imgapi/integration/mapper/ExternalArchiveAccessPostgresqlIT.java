package com.zjcxph.imgapi.integration.mapper;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.ExternalArchiveAccessMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "mybatis.configuration.map-underscore-to-camel-case=true")
class ExternalArchiveAccessPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired ExternalArchiveAccessMapper mapper;

    @Test
    void ticketCannotBeConsumedTwiceEvenInSameMapperSession() {
        mapper.insertTicket("synthetic-ticket", "synthetic", "synthetic-user", false,
                "[]", LocalDateTime.now().plusMinutes(2), "127.0.0.1");
        assertThat(mapper.consumeTicket("synthetic-ticket")).isNotNull();
        assertThat(mapper.consumeTicket("synthetic-ticket")).isNull();
    }

    @Test
    void nonceUniquenessAndSessionRevocationAreEnforcedByDatabase() {
        var expiry = LocalDateTime.now().plusMinutes(2);
        assertThat(mapper.insertNonce("synthetic", "synthetic-nonce", expiry)).isEqualTo(1);
        assertThat(mapper.insertNonce("synthetic", "synthetic-nonce", expiry)).isZero();
        mapper.insertSession("synthetic-session", "synthetic", "synthetic-user", false, "[]", expiry, "127.0.0.1");
        assertThat(mapper.findSession("synthetic-session")).isNotNull();
        assertThat(mapper.touchSession("synthetic-session")).isEqualTo(1);
        assertThat(mapper.revokeSession("synthetic-session")).isEqualTo(1);
        assertThat(mapper.touchSession("synthetic-session")).isZero();
        assertThat(mapper.findSession("synthetic-session")).isNull();
        mapper.insertSession("synthetic-expired", "synthetic", "synthetic-user", false, "[]",
                LocalDateTime.now().minusMinutes(1), "127.0.0.1");
        assertThat(mapper.touchSession("synthetic-expired")).isZero();
        assertThat(mapper.findSession("synthetic-expired")).isNull();
    }
}
