package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.zjcxph.imgapi.integration.database.FlywayFreshDatabasePostgresqlIT.*;

/** Explicit local rehearsal; not included by default Failsafe naming patterns.
 * Requires MRR_TEST_PG_BIN and a disposable loopback MRR_TEST_POSTGRES_URL. */
class FlywayNativeBackupRehearsal extends PostgresqlIntegrationTestSupport {
    // Explicit opt-in: native tools are already exercised on Windows by its dedicated CI.
    @Test
    void restoresOldCustomArchiveAndUpgradesIndependentReplica(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        assertThat(System.getenv("MRR_TEST_PG_BIN")).isNotBlank();
        assertThat(System.getenv("MRR_TEST_POSTGRES_URL")).isNotBlank();
        String sourceUrl = postgresqlJdbcUrl();
        URI uri = postgresUri(sourceUrl);
        assertThat(uri.getHost()).isIn("127.0.0.1", "localhost", "::1", "[::1]");
        String adminUrl = databaseUrl(sourceUrl, databaseName(sourceUrl));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String source = "mrr_flyway_backup_" + suffix;
        String replica = "mrr_flyway_replica_" + suffix;
        String damagedReplica = "mrr_flyway_damaged_" + suffix;
        createDatabase(adminUrl, source);
        try {
            String url = databaseUrl(sourceUrl, source);
            assertThat(newFlyway(url, "20261123170000").migrate().migrationsExecuted).isEqualTo(47);
            java.util.Map<String, String> before;
            String history;
            try (Connection c = connection(url); Statement sql = c.createStatement()) {
                createLegacyProbeData(sql);
                before = upgradeSnapshot(sql, false);
                history = migrationSnapshot(sql, "20261123170000");
            }
            var archive = temp.resolve("synthetic.dump");
            assertThat(nativePg(temp, "pg_dump", uri, source, "--format=custom", "--file=" + archive)).isZero();
            assertThat(java.nio.file.Files.size(archive)).isPositive();
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(archive)));
            assertThat(nativePg(temp, "pg_restore", uri, null, "--list", archive.toString())).isZero();
            createDatabase(adminUrl, replica);
            try {
                String restored = databaseUrl(sourceUrl, replica);
                assertThat(nativePg(temp, "pg_restore", uri, replica, "--exit-on-error", "--no-owner", "--no-privileges", archive.toString())).isZero();
                try (Connection c = connection(restored); Statement sql = c.createStatement()) {
                    assertThat(upgradeSnapshot(sql, false)).isEqualTo(before);
                    assertThat(migrationSnapshot(sql, "20261123170000")).isEqualTo(history);
                    assertThat(newFlyway(restored).migrate().migrationsExecuted).isEqualTo(11);
                    assertThat(upgradeSnapshot(sql, true)).isEqualTo(before);
                    assertThat(migrationSnapshot(sql, "20261123170000")).isEqualTo(history);
                    assertThat(newFlyway(restored).migrate().migrationsExecuted).isZero();
                }
            } finally {
                dropDatabase(adminUrl, replica);
            }

            // A readable archive directory is not a proof of payload integrity.
            var truncated = temp.resolve("truncated.dump");
            byte[] bytes = java.nio.file.Files.readAllBytes(archive);
            java.nio.file.Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length - Math.min(1024, bytes.length / 4)));
            int listStatus = nativePg(temp, "pg_restore", uri, null, "--list", truncated.toString());
            createDatabase(adminUrl, damagedReplica);
            try {
                assertThat(nativePg(temp, "pg_restore", uri, damagedReplica, "--exit-on-error", "--no-owner", "--no-privileges", truncated.toString())).isNotZero();
            } finally {
                dropDatabase(adminUrl, damagedReplica);
            }
            try (Connection c = connection(url); Statement sql = c.createStatement()) {
                assertThat(upgradeSnapshot(sql, false)).isEqualTo(before);
                assertThat(migrationSnapshot(sql, "20261123170000")).isEqualTo(history);
            }
            System.out.println("MRR_BACKUP bytes=" + bytes.length + " sha256=" + digest
                    + " restored eight snapshots; eleven forward migrations; truncated list status=" + listStatus + "; truncated restore rejected; source unchanged");
        } finally {
            dropDatabase(adminUrl, source);
        }
    }

    private static int nativePg(java.nio.file.Path temp, String executable, URI uri, String database, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(java.nio.file.Path.of(System.getenv("MRR_TEST_PG_BIN"), executable).toString());
        if (database != null) {
            command.addAll(java.util.List.of("--host=" + uri.getHost(), "--port=" + uri.getPort(),
                    "--username=" + postgresqlUsername(), "--dbname=" + database, "--no-password"));
        }
        command.addAll(java.util.List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(temp.resolve(executable + ".log").toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("PG"));
        builder.environment().put("PGPASSWORD", postgresqlPassword());
        Process process = builder.start();
        try {
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new java.io.IOException("Native PostgreSQL test command timed out");
            }
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

}
