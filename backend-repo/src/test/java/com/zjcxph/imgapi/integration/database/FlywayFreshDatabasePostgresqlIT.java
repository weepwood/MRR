package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Flyway PostgreSQL 16 空数据库生命周期测试")
class FlywayFreshDatabasePostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Test
    @DisplayName("空数据库完成全部迁移且第二次执行不重复修改数据")
    void migratesFreshDatabaseAndRerunsSafely() throws Exception {
        String sourceUrl = postgresqlJdbcUrl();
        String adminUrl = databaseUrl(sourceUrl, databaseName(sourceUrl));
        String temporaryDatabase = "mrr_flyway_it_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        createDatabase(adminUrl, temporaryDatabase);
        try {
            String targetUrl = databaseUrl(sourceUrl, temporaryDatabase);
            Flyway flyway = newFlyway(targetUrl);

            MigrateResult firstMigration = flyway.migrate();

            assertThat(firstMigration.success).isTrue();
            assertThat(firstMigration.migrationsExecuted).isPositive();
            assertThat(flyway.info().applied()).isNotEmpty();
            assertThat(flyway.info().pending()).isEmpty();
            assertCoreSchemaAndTables(targetUrl);
            createProbeData(targetUrl);

            int appliedBeforeRestart = flyway.info().applied().length;
            Flyway restartedFlyway = newFlyway(targetUrl);
            MigrateResult secondMigration = restartedFlyway.migrate();

            assertThat(secondMigration.success).isTrue();
            assertThat(secondMigration.migrationsExecuted).isZero();
            assertThat(restartedFlyway.info().pending()).isEmpty();
            assertThat(restartedFlyway.info().applied()).hasSize(appliedBeforeRestart);
            assertProbeDataPreserved(targetUrl);
        }
        finally {
            dropDatabase(adminUrl, temporaryDatabase);
        }
    }

    private static Flyway newFlyway(String jdbcUrl) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(jdbcUrl, postgresqlUsername(), postgresqlPassword())
                .locations("classpath:db/migration", "classpath:db/callback")
                .schemas("app")
                .defaultSchema("app")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .outOfOrder(false);
        configuration.getConfigurationExtension(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);
        return configuration.load();
    }

    private static void assertCoreSchemaAndTables(String jdbcUrl) throws SQLException {
        try (Connection connection = connection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            assertThat(singleString(statement, "SELECT to_regnamespace('app')::text"))
                    .isEqualTo("app");
            assertThat(singleString(statement, "SELECT to_regclass('app.flyway_schema_history')::text"))
                    .isEqualTo("app.flyway_schema_history");
            assertThat(singleString(statement, "SELECT to_regclass('app.mr_scan')::text"))
                    .isEqualTo("app.mr_scan");
            assertThat(singleString(statement, "SELECT to_regclass('app.mr_archive')::text"))
                    .isEqualTo("app.mr_archive");
        }
    }

    private static void createProbeData(String jdbcUrl) throws SQLException {
        try (Connection connection = connection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE app.flyway_repeat_probe (id integer PRIMARY KEY, value text NOT NULL)");
            statement.executeUpdate("INSERT INTO app.flyway_repeat_probe (id, value) VALUES (1, 'preserved')");
        }
    }

    private static void assertProbeDataPreserved(String jdbcUrl) throws SQLException {
        try (Connection connection = connection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            assertThat(singleString(
                    statement,
                    "SELECT value FROM app.flyway_repeat_probe WHERE id = 1"
            )).isEqualTo("preserved");
        }
    }

    private static void createDatabase(String adminUrl, String databaseName) throws SQLException {
        try (Connection connection = connection(adminUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
    }

    private static void dropDatabase(String adminUrl, String databaseName) throws SQLException {
        try (Connection connection = connection(adminUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName) + " WITH (FORCE)");
        }
    }

    private static Connection connection(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgresqlUsername(), postgresqlPassword());
    }

    private static String singleString(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private static String databaseName(String jdbcUrl) {
        URI uri = postgresUri(jdbcUrl);
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("PostgreSQL JDBC URL does not contain a database name: " + jdbcUrl);
        }
        return path.substring(1);
    }

    private static String databaseUrl(String jdbcUrl, String databaseName) {
        URI uri = postgresUri(jdbcUrl);
        return "jdbc:postgresql://" + uri.getRawAuthority() + "/" + databaseName;
    }

    private static URI postgresUri(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Unsupported PostgreSQL JDBC URL: " + jdbcUrl);
        }
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }

    private static String quoteIdentifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
