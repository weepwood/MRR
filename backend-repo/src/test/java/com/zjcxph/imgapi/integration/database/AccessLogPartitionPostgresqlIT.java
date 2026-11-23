package com.zjcxph.imgapi.integration.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.database.postgresql.PostgreSQLConfigurationExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-0010 access_log 按月分区迁移的完整生命周期。
 *
 * <p>在独立临时库中先迁移到上一版本并写入「历史」数据，再执行分区迁移，覆盖：双写、分批复制与
 * 中断续跑不重复、默认分区迁出、切换、整月分区删除、切换后回退、放弃，以及分区表的行数估算。</p>
 */
@DisplayName("access_log 按月分区迁移（PostgreSQL 16）")
class AccessLogPartitionPostgresqlIT extends PostgresqlIntegrationTestSupport {

    private static final String PREVIOUS_VERSION = "20261119090000";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("双写 → 分批复制 → 切换 → 删除过期分区 → 回退 → 放弃")
    void fullLifecycle() throws Exception {
        String sourceUrl = postgresqlJdbcUrl();
        String adminUrl = databaseUrl(sourceUrl, databaseName(sourceUrl));
        String temporaryDatabase = "mrr_access_log_part_it_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        execute(adminUrl, "CREATE DATABASE \"" + temporaryDatabase + "\"");
        try {
            String url = databaseUrl(sourceUrl, temporaryDatabase);
            assertThat(flyway(url, PREVIOUS_VERSION).migrate().success).isTrue();
            try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
                insertLegacy(sql, "hist-1", "'2024-01-15 08:00:00'");
                insertLegacy(sql, "hist-2", "'2024-01-20 09:30:00'");
                insertLegacy(sql, "hist-3", "'2024-02-10 10:00:00'");
                insertLegacy(sql, "hist-4", "'2024-02-11 11:00:00'");
                insertLegacy(sql, "hist-null", "NULL");
            }

            assertThat(flyway(url, null).migrate().success).isTrue();

            try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
                assertInitialState(sql);
                assertCopyUntilReady(sql);
                assertDeletedLogDoesNotReturnAtCutover(sql);
                assertDefaultPartitionIsSplitOut(sql);
                assertCutoverLockWaitIsBounded(url, sql);
                long cutoverMaxId = assertCutover(sql);
                assertPartitionedRowEstimate(url, sql);
                assertSplitAfterCutoverKeepsLegacyCopy(sql);
                assertExpiredMonthDroppedOnlyAfterRetirement(sql);
                assertRollback(sql, cutoverMaxId);
                assertAbort(sql);
            }

            assertThat(flyway(url, null).migrate().migrationsExecuted).isZero();
        } finally {
            execute(adminUrl, "DROP DATABASE IF EXISTS \"" + temporaryDatabase + "\" WITH (FORCE)");
        }
    }

    private static void assertInitialState(Statement sql) throws SQLException {
        Map<String, Object> status = json(sql, "SELECT app.access_log_partition_status()::text");
        assertThat(status.get("phase")).isEqualTo("COPYING");
        assertThat(status.get("partitionedTable")).isEqualTo("access_log_partitioned");
        // 默认分区 + 当前月 + 之后两个月
        assertThat(((Number) status.get("partitionCount")).intValue()).isGreaterThanOrEqualTo(4);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned")).isZero();

        assertThat(count(sql, "SELECT app.access_log_drop_expired_partitions(LOCALTIMESTAMP)")).isZero();
        insertLegacy(sql, "live-1", "LOCALTIMESTAMP");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned WHERE request_id = 'live-1'"))
                .as("触发器双写").isEqualTo(1);

        assertThatThrownBy(() -> sql.executeQuery("SELECT app.access_log_partition_cutover()"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ACCESS_LOG_PARTITION_NOT_READY");
    }

    private static void assertCopyUntilReady(Statement sql) throws SQLException {
        long finalSourceId = insertLegacy(sql, "cleaned-copy-target", "LOCALTIMESTAMP");
        assertThat(json(sql, "SELECT app.access_log_partition_copy_batch(2)::text").get("phase")).isEqualTo("COPYING");
        sql.executeUpdate("DELETE FROM app.access_log WHERE id = " + finalSourceId);
        String phase = "COPYING";
        int rounds = 0;
        while (!"READY".equals(phase) && rounds++ < 20) {
            // 每批 2 行：覆盖多批推进、跨月份建分区、与触发器已写入行的去重
            phase = (String) json(sql, "SELECT app.access_log_partition_copy_batch(2)::text").get("phase");
        }
        assertThat(phase).isEqualTo("READY");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned WHERE request_id = 'cleaned-copy-target'"))
                .isZero();
        assertSameRows(sql);
        assertThat(regclass(sql, "app.access_log_y2024m01")).isNotNull();
        assertThat(regclass(sql, "app.access_log_y2024m02")).isNotNull();
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_default WHERE request_id = 'hist-null'"))
                .isEqualTo(1);

        // READY 后继续调用是幂等的：不会重复复制
        json(sql, "SELECT app.access_log_partition_copy_batch(100)::text");
        assertSameRows(sql);
    }

    private static void assertDeletedLogDoesNotReturnAtCutover(Statement sql) throws SQLException {
        insertLegacy(sql, "cleaned-before-cutover", "LOCALTIMESTAMP");
        sql.executeUpdate("DELETE FROM app.access_log WHERE request_id = 'cleaned-before-cutover'");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned WHERE request_id = 'cleaned-before-cutover'"))
                .as("切换前清理过的记录不能留在待切换的分区表中").isZero();
    }

    private static void assertDefaultPartitionIsSplitOut(Statement sql) throws SQLException {
        // 时钟异常写入远期月份：先落默认分区，维护任务把它迁出为独立分区
        insertLegacy(sql, "far-future", "'2030-06-01 00:00:00'");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_default WHERE request_id = 'far-future'"))
                .isEqualTo(1);
        assertThat(count(sql, "SELECT app.access_log_maintain_partitions(2)")).isGreaterThanOrEqualTo(1);
        assertThat(regclass(sql, "app.access_log_y2030m06")).isNotNull();
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_default")).isEqualTo(1);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_y2030m06 WHERE request_id = 'far-future'"))
                .isEqualTo(1);
        assertSameRows(sql);
    }

    private static void assertCutoverLockWaitIsBounded(String url, Statement sql) throws SQLException {
        try (Connection blocker = connection(url); Statement blockerSql = blocker.createStatement()) {
            blocker.setAutoCommit(false);
            blockerSql.execute("SELECT pg_advisory_xact_lock(hashtext('mrr.access_log_partition'))");
            sql.execute("SET statement_timeout = '8s'");
            try {
                assertThatThrownBy(() -> sql.executeQuery("SELECT app.access_log_partition_cutover()"))
                        .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState())
                        .isEqualTo("55P03");
                assertThat(json(sql, "SELECT app.access_log_partition_status()::text").get("phase")).isEqualTo("READY");
            } finally {
                blocker.rollback();
                sql.execute("RESET statement_timeout");
            }
        }
    }

    private static long assertCutover(Statement sql) throws SQLException {
        Map<String, Object> result = json(sql, "SELECT app.access_log_partition_cutover()::text");
        assertThat(result.get("phase")).isEqualTo("CUTOVER");
        long cutoverMaxId = ((Number) result.get("cutoverMaxId")).longValue();

        assertThat(single(sql, "SELECT relkind::text FROM pg_class WHERE oid = 'app.access_log'::regclass"))
                .isEqualTo("p");
        assertThat(regclass(sql, "app.access_log_legacy")).isNotNull();
        assertThat(regclass(sql, "app.access_log_partitioned")).isNull();
        assertThat(count(sql, "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_access_log_copy_to_partitioned'"))
                .isZero();
        assertThat(count(sql, "SELECT count(*) FROM app.access_log"))
                .isEqualTo(count(sql, "SELECT count(*) FROM app.access_log_legacy"));

        long newId = insertLegacy(sql, "after-cutover", "LOCALTIMESTAMP");
        assertThat(newId).as("切换后序列从旧表最大 id 之后继续").isGreaterThan(cutoverMaxId);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_legacy WHERE request_id = 'after-cutover'"))
                .isEqualTo(1);
        assertThatThrownBy(() -> insertLegacy(sql, "after-cutover", "LOCALTIMESTAMP"))
                .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23505");
        sql.executeUpdate("DELETE FROM app.access_log WHERE request_id = 'live-1'");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_legacy WHERE request_id = 'live-1'")).isZero();
        return cutoverMaxId;
    }

    private static void assertPartitionedRowEstimate(String url, Statement sql) throws SQLException {
        sql.execute("ANALYZE app.access_log");
        long actual = count(sql, "SELECT count(*) FROM app.access_log");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, postgresqlUsername(), postgresqlPassword());
        Long estimate = new DatabaseBrowseRepository(dataSource).estimateRows("app", "access_log");
        assertThat(estimate).as("分区表按分区求和估算").isEqualTo(actual);
    }

    private static void assertSplitAfterCutoverKeepsLegacyCopy(Statement sql) throws SQLException {
        insertLegacy(sql, "split-after-cutover", "'2032-03-01 00:00:00'");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_default WHERE request_id = 'split-after-cutover'"))
                .isEqualTo(1);
        assertThat(count(sql, "SELECT app.access_log_maintain_partitions(2)")).isGreaterThanOrEqualTo(1);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_y2032m03 WHERE request_id = 'split-after-cutover'"))
                .isEqualTo(1);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_legacy WHERE request_id = 'split-after-cutover'"))
                .isEqualTo(1);
    }

    private static void assertExpiredMonthDroppedOnlyAfterRetirement(Statement sql) throws SQLException {
        assertThat(count(sql, "SELECT app.access_log_drop_expired_partitions('2024-02-01 00:00:00')")).isZero();
        assertThat(regclass(sql, "app.access_log_y2024m01")).isNotNull();

        Connection connection = sql.getConnection();
        connection.setAutoCommit(false);
        try {
            sql.executeUpdate("DROP TABLE app.access_log_legacy");
            assertThat(count(sql, "SELECT app.access_log_drop_expired_partitions('2024-02-01 00:00:00')")).isEqualTo(1);
            assertThat(regclass(sql, "app.access_log_y2024m01")).isNull();
            assertThat(regclass(sql, "app.access_log_y2024m02")).isNotNull();
            assertThat(count(sql, "SELECT count(*) FROM app.access_log WHERE access_time < '2024-02-01'")).isZero();
            assertThat(count(sql, "SELECT count(*) FROM app.access_log_default")).isEqualTo(1);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void assertRollback(Statement sql, long cutoverMaxId) throws SQLException {
        Map<String, Object> result = json(sql, "SELECT app.access_log_partition_rollback()::text");
        assertThat(result.get("phase")).isEqualTo("READY");
        assertThat(((Number) result.get("rowsReturned")).intValue()).isZero();

        assertThat(single(sql, "SELECT relkind::text FROM pg_class WHERE oid = 'app.access_log'::regclass"))
                .isEqualTo("r");
        assertThat(regclass(sql, "app.access_log_partitioned")).isNotNull();
        assertThat(count(sql, "SELECT count(*) FROM app.access_log WHERE request_id = 'after-cutover'"))
                .as("切换后写入已同步并保留到回退后的旧表").isEqualTo(1);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log WHERE request_id = 'live-1'")).isZero();

        long newId = insertLegacy(sql, "after-rollback", "LOCALTIMESTAMP");
        assertThat(newId).isGreaterThan(cutoverMaxId + 2);
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned WHERE request_id = 'after-rollback'"))
                .as("回退后恢复双写").isEqualTo(1);
    }

    private static void assertAbort(Statement sql) throws SQLException {
        assertThat(json(sql, "SELECT app.access_log_partition_abort()::text").get("phase")).isEqualTo("ABORTED");
        assertThat(regclass(sql, "app.access_log_partitioned")).isNull();
        assertThat(count(sql, "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_access_log_copy_to_partitioned'"))
                .isZero();
        assertThat(count(sql, "SELECT app.access_log_maintain_partitions(2)")).isZero();
        insertLegacy(sql, "after-abort", "LOCALTIMESTAMP");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log WHERE request_id = 'after-abort'")).isEqualTo(1);
        assertThatThrownBy(() -> sql.executeQuery("SELECT app.access_log_partition_cutover()"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ACCESS_LOG_PARTITION_NOT_READY");
    }

    private static void assertSameRows(Statement sql) throws SQLException {
        long legacy = count(sql, "SELECT count(*) FROM app.access_log");
        assertThat(count(sql, "SELECT count(*) FROM app.access_log_partitioned")).isEqualTo(legacy);
        assertThat(count(sql, "SELECT count(DISTINCT id) FROM app.access_log_partitioned")).isEqualTo(legacy);
        assertThat(count(sql, """
                SELECT count(*) FROM app.access_log l
                WHERE NOT EXISTS (
                    SELECT 1 FROM app.access_log_partitioned p
                    WHERE p.id = l.id
                      AND p.request_id IS NOT DISTINCT FROM l.request_id
                      AND p.access_time IS NOT DISTINCT FROM l.access_time)
                """)).isZero();
    }

    private static long insertLegacy(Statement sql, String requestId, String accessTimeExpression) throws SQLException {
        try (ResultSet rs = sql.executeQuery("""
                INSERT INTO app.access_log (request_id, username, client_ip, request_uri, method, access_time,
                                            response_status, execute_time)
                VALUES ('%s', 'it-user', '127.0.0.1', '/api/v1/it', 'GET', %s, '200', 1)
                RETURNING id
                """.formatted(requestId, accessTimeExpression))) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private static Flyway flyway(String url, String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(url, postgresqlUsername(), postgresqlPassword())
                .locations("classpath:db/migration", "classpath:db/callback")
                .schemas("app")
                .defaultSchema("app")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .outOfOrder(false);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        configuration.getConfigurationExtension(PostgreSQLConfigurationExtension.class)
                .setTransactionalLock(false);
        return configuration.load();
    }

    private static Map<String, Object> json(Statement sql, String query) throws SQLException {
        try {
            return JSON.readValue(single(sql, query), new TypeReference<>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError("函数返回值不是 JSON：" + query, e);
        }
    }

    private static long count(Statement sql, String query) throws SQLException {
        try (ResultSet rs = sql.executeQuery(query)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    private static String regclass(Statement sql, String name) throws SQLException {
        return single(sql, "SELECT to_regclass('" + name + "')::text");
    }

    private static String single(Statement sql, String query) throws SQLException {
        try (ResultSet rs = sql.executeQuery(query)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private static void execute(String url, String statement) throws SQLException {
        try (Connection connection = connection(url); Statement sql = connection.createStatement()) {
            sql.execute(statement);
        }
    }

    private static Connection connection(String url) throws SQLException {
        return DriverManager.getConnection(url, postgresqlUsername(), postgresqlPassword());
    }

    private static String databaseName(String jdbcUrl) {
        String path = postgresUri(jdbcUrl).getPath();
        if (path == null || path.length() <= 1) {
            throw new IllegalArgumentException("PostgreSQL JDBC URL does not contain a database name: " + jdbcUrl);
        }
        return path.substring(1);
    }

    private static String databaseUrl(String jdbcUrl, String databaseName) {
        return "jdbc:postgresql://" + postgresUri(jdbcUrl).getRawAuthority() + "/" + databaseName;
    }

    private static URI postgresUri(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Unsupported PostgreSQL JDBC URL: " + jdbcUrl);
        }
        return URI.create(jdbcUrl.substring("jdbc:".length()));
    }
}
