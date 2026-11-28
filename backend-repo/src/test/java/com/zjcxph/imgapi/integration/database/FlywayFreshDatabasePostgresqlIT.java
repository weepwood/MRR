package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @ParameterizedTest(name = "staged={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("旧正式基线升级，保留原有业务数据及迁移校验信息")
    void upgradesPopulatedPreviousBaselinesWithoutReinterpretingHistory(boolean staged) throws Exception {
        String sourceUrl = postgresqlJdbcUrl();
        String adminUrl = databaseUrl(sourceUrl, databaseName(sourceUrl));
        String database = "mrr_flyway_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createDatabase(adminUrl, database);
        try {
            String url = databaseUrl(sourceUrl, database);
            var previous = newFlyway(url, "20261123170000");
            assertThat(previous.migrate().success).isTrue();
            try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
                createLegacyProbeData(sql);
                var before = upgradeSnapshot(sql, false);
                var oldChecksums = migrationSnapshot(sql, "20261123170000");

                String preservedVersion = "20261123170000";
                String preservedChecksums = oldChecksums;
                if (staged) {
                    var intermediate = newFlyway(url, "20261125112000");
                    assertThat(intermediate.migrate().migrationsExecuted).isEqualTo(9);
                    assertThat(upgradeSnapshot(sql, false)).isEqualTo(before);
                    assertThat(migrationSnapshot(sql, preservedVersion)).isEqualTo(oldChecksums);
                    preservedVersion = "20261125112000";
                    preservedChecksums = migrationSnapshot(sql, preservedVersion);
                }

                var latest = newFlyway(url);
                assertThat(latest.migrate().migrationsExecuted).isEqualTo(staged ? 3 : 12);
                assertThat(latest.info().pending()).isEmpty();
                assertThat(upgradeSnapshot(sql, true)).isEqualTo(before);
                assertThat(migrationSnapshot(sql, preservedVersion)).isEqualTo(preservedChecksums);
                assertThat(singleString(sql, "SELECT count(*) FROM app.mrr_data_quality_run WHERE triggered_by='synthetic-upgrade' AND NOT task_managed AND deadline_at IS NULL")).isEqualTo("3");
                assertThat(singleString(sql, "SELECT count(*) FROM app.mr_scan WHERE bah='7000011' AND migration_status='migrating' AND migration_claim_id IS NULL")).isEqualTo("1");
                assertThat(singleString(sql, "SELECT count(*) FROM app.medical_audit_event")).isEqualTo("0");
                assertThat(singleString(sql, "SELECT count(*) FROM app.mr_security_change_audit")).isEqualTo("0");
                sql.executeUpdate("INSERT INTO app.mr_security_change_audit(event_type,actor_user_id,target_user_id) VALUES ('USER_DISABLED',101,102)");
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    sql.executeUpdate("INSERT INTO app.mr_security_change_audit(event_type,actor_user_id,target_user_id) VALUES ('USER_DISABLED',NULL,102)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));

                // Old observations do not consume the new managed slot; the new slot rejects a second active run.
                sql.executeUpdate("INSERT INTO app.mrr_data_quality_run(status,triggered_by,task_managed,deadline_at) VALUES ('PENDING','synthetic-new',true,CURRENT_TIMESTAMP + interval '1 minute')");
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    sql.executeUpdate("INSERT INTO app.mrr_data_quality_run(status,triggered_by,task_managed,deadline_at) VALUES ('RUNNING','synthetic-new',true,CURRENT_TIMESTAMP + interval '1 minute')"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    sql.executeUpdate("INSERT INTO app.mrr_data_quality_run(status,triggered_by,task_managed) VALUES ('SUCCESS','synthetic-new',true)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
                assertThat(newFlyway(url).migrate().migrationsExecuted).isZero();
                assertThat(singleString(sql, "SELECT count(*) FROM app.mr_security_change_audit WHERE event_type='USER_DISABLED' AND actor_user_id=101 AND target_user_id=102")).isEqualTo("1");
                assertThat(upgradeSnapshot(sql, true)).isEqualTo(before);
                System.out.println("MRR_UPGRADE staged=" + staged + " preserved eight business snapshots and prior migration metadata; active slot and deadline enforced");
            }
        } finally {
            dropDatabase(adminUrl, database);
        }
    }

    static void createLegacyProbeData(Statement sql) throws SQLException {
        sql.executeUpdate("INSERT INTO app.mr_archive(bah,sjh) VALUES ('7000011','7000012')");
        sql.executeUpdate("INSERT INTO app.mr_scan(bah,sjh,filename,pages,uploadflag,folder,migration_status) VALUES ('7000011','7000012','synthetic.png',1,1,'synthetic','migrating')");
        sql.executeUpdate("INSERT INTO app.mr_patient(bah,name,department) VALUES ('7000011','synthetic','synthetic')");
        sql.executeUpdate("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('RUNNING','synthetic-upgrade'),('RUNNING','synthetic-upgrade'),('SUCCESS','synthetic-upgrade')");
        sql.executeUpdate("INSERT INTO app.mrr_data_quality_check_result(run_id,check_code,check_name,severity,issue_count) SELECT id,'synthetic','synthetic','WARNING',1 FROM app.mrr_data_quality_run WHERE status='SUCCESS' AND triggered_by='synthetic-upgrade'");
        sql.executeUpdate("INSERT INTO app.migration_job(status,mode,total_count,processed_count) VALUES ('running','folder',10,3)");
        sql.executeUpdate("INSERT INTO app.access_log(request_id,username,method,request_uri,access_time,response_status) VALUES ('synthetic-upgrade','synthetic','GET','/synthetic',CURRENT_TIMESTAMP,'200')");
        sql.executeUpdate("INSERT INTO app.token_revocation(token_hash,expires_at) VALUES (repeat('a',64),CURRENT_TIMESTAMP + interval '1 day')");
    }

    static java.util.Map<String, String> upgradeSnapshot(Statement sql, boolean latest) throws SQLException {
        var snapshot = new java.util.LinkedHashMap<String, String>();
        for (String table : java.util.List.of("mr_archive", "mr_scan", "mr_patient", "mrr_data_quality_run",
                "mrr_data_quality_check_result", "migration_job", "access_log", "token_revocation")) {
            String row = "to_jsonb(t)";
            String filter = "";
            if ("mrr_data_quality_run".equals(table)) {
                filter = " WHERE triggered_by='synthetic-upgrade'";
                if (latest) row += " - ARRAY['task_managed','deadline_at','claimed_at','requested_by','cancelled_by']";
            }
            if (latest && "mr_scan".equals(table)) row += " - 'migration_claim_id'";
            snapshot.put(table, singleString(sql, "SELECT COALESCE(jsonb_agg(" + row + " ORDER BY (" + row + ")::text),'[]'::jsonb)::text FROM app." + table + " t" + filter));
        }
        return snapshot;
    }

    static String migrationSnapshot(Statement sql, String version) throws SQLException {
        return singleString(sql, "SELECT jsonb_agg(jsonb_build_array(installed_rank,version,script,checksum,success) ORDER BY installed_rank)::text FROM app.flyway_schema_history WHERE version <= '" + version + "'");
    }

    static Flyway newFlyway(String jdbcUrl) {
        return newFlyway(jdbcUrl, null);
    }

    static Flyway newFlyway(String jdbcUrl, String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(jdbcUrl, postgresqlUsername(), postgresqlPassword())
                .locations("classpath:db/migration", "classpath:db/callback")
                .schemas("app")
                .defaultSchema("app")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .outOfOrder(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
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
            assertThat(singleString(statement, "SELECT to_regclass('app.mr_security_change_audit')::text"))
                    .isEqualTo("app.mr_security_change_audit");
            assertRuntimeLogJournalDropped(statement);
        }
    }

    /**
     * ADR-0008：journal 三表应已删除，而错误中心投影防重的收据表必须保留。
     *
     * <p>用 {@code to_regclass} 断言：表存在时返回带 schema 的名字，不存在时返回 NULL，
     * 两种结果都能精确区分。</p>
     */
    private static void assertRuntimeLogJournalDropped(Statement statement) throws SQLException {
        assertThat((Object) singleString(statement, "SELECT to_regclass('app.runtime_log_event')::text")).isNull();
        assertThat((Object) singleString(statement, "SELECT to_regclass('app.runtime_log_source')::text")).isNull();
        assertThat((Object) singleString(statement, "SELECT to_regclass('app.runtime_log_capacity_sample')::text")).isNull();
        assertThat(singleString(statement, "SELECT to_regclass('app.runtime_error_projection_receipt')::text"))
                .isEqualTo("app.runtime_error_projection_receipt");
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

    static void createDatabase(String adminUrl, String databaseName) throws SQLException {
        try (Connection connection = connection(adminUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
        }
    }

    static void dropDatabase(String adminUrl, String databaseName) throws SQLException {
        try (Connection connection = connection(adminUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + quoteIdentifier(databaseName) + " WITH (FORCE)");
        }
    }

    static Connection connection(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgresqlUsername(), postgresqlPassword());
    }

    private static String singleString(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    static String databaseName(String jdbcUrl) {
        URI uri = postgresUri(jdbcUrl);
        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("PostgreSQL JDBC URL does not contain a database name: " + jdbcUrl);
        }
        return path.substring(1);
    }

    static String databaseUrl(String jdbcUrl, String databaseName) {
        URI uri = postgresUri(jdbcUrl);
        return "jdbc:postgresql://" + uri.getRawAuthority() + "/" + databaseName;
    }

    static URI postgresUri(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Unsupported PostgreSQL JDBC URL: " + jdbcUrl);
        }
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }

    private static String quoteIdentifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
