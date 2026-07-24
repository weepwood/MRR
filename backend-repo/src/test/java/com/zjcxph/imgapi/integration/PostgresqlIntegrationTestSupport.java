package com.zjcxph.imgapi.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides one PostgreSQL 16 database for all backend integration test classes.
 *
 * <p>GitHub Actions injects an external service database through the
 * {@code MRR_TEST_POSTGRES_*} environment variables. Local executions fall
 * back to one lazily started Testcontainer shared by the whole Maven JVM.</p>
 */
public abstract class PostgresqlIntegrationTestSupport {

    private static final String EXTERNAL_URL = environment("MRR_TEST_POSTGRES_URL");
    private static final String EXTERNAL_USERNAME = environmentOrDefault(
            "MRR_TEST_POSTGRES_USERNAME",
            "imgapi"
    );
    private static final String EXTERNAL_PASSWORD = environmentOrDefault(
            "MRR_TEST_POSTGRES_PASSWORD",
            "imgapi"
    );

    private static final class LocalContainerHolder {
        private static final PostgreSQLContainer<?> INSTANCE = startLocalContainer();

        private static PostgreSQLContainer<?> startLocalContainer() {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("imgapi")
                    .withUsername("imgapi")
                    .withPassword("imgapi");
            container.start();
            return container;
        }
    }

    @DynamicPropertySource
    protected static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresqlIntegrationTestSupport::postgresqlJdbcUrl);
        registry.add("spring.datasource.username", PostgresqlIntegrationTestSupport::postgresqlUsername);
        registry.add("spring.datasource.password", PostgresqlIntegrationTestSupport::postgresqlPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO app, public"
        );
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.schemas", () -> "app");
        registry.add("spring.flyway.default-schema", () -> "app");
        registry.add("spring.flyway.postgresql.transactional-lock", () -> false);
        registry.add("spring.sql.init.mode", () -> "never");
    }

    /**
     * Returns the effective JDBC URL used by PostgreSQL integration tests.
     *
     * <p>Subclasses may use this to create isolated temporary databases while
     * reusing the same CI service or local Testcontainer credentials.</p>
     */
    protected static String postgresqlJdbcUrl() {
        if (EXTERNAL_URL != null) {
            return withCurrentSchema(EXTERNAL_URL);
        }
        return withCurrentSchema(LocalContainerHolder.INSTANCE.getJdbcUrl());
    }

    protected static String postgresqlUsername() {
        return EXTERNAL_URL != null
                ? EXTERNAL_USERNAME
                : LocalContainerHolder.INSTANCE.getUsername();
    }

    protected static String postgresqlPassword() {
        return EXTERNAL_URL != null
                ? EXTERNAL_PASSWORD
                : LocalContainerHolder.INSTANCE.getPassword();
    }

    private static String withCurrentSchema(String url) {
        if (url.contains("currentSchema=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=app";
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = environment(name);
        return value == null ? fallback : value;
    }
}
