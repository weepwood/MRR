package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class CodeKeyIndexPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    private String sql() throws Exception { return Files.readString(Path.of("scripts/build-code-key-indexes.sql")); }
    private String guard() throws Exception {
        var match=Pattern.compile("DO \\$\\$.*?\\$\\$;",Pattern.DOTALL).matcher(sql());
        assertThat(match.find()).isTrue();return match.group();
    }
    @Test void checkpointIdentitySeparatesSameNamedDatabasesOnDifferentInstances() throws Exception {
        String script=Files.readString(Path.of("scripts/check-scan-quality.ps1"));
        var match=Pattern.compile("'databaseKey',(md5\\(.+?\\)),'highWater'").matcher(script);
        assertThat(match.find()).isTrue();
        String query="SELECT "+match.group(1);
        String first=jdbc.queryForObject(query,String.class);
        String name=jdbc.queryForObject("SELECT current_database()",String.class);
        try(var second=new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName(name)) {
            second.start();
            try(var connection=java.sql.DriverManager.getConnection(second.getJdbcUrl(),second.getUsername(),second.getPassword());var statement=connection.createStatement()) {
                try(var result=statement.executeQuery("SELECT current_database()")){assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo(name);}
                try(var result=statement.executeQuery(query)){assertThat(result.next()).isTrue();assertThat(result.getString(1)).isNotEqualTo(first);}
            }
        }
    }
    @Test void standaloneConcurrentIndexesAreValidAndIndependentlyRemovable() throws Exception {
        try {
            jdbc.execute(guard());
            for (String line:sql().lines().filter(row->row.startsWith("CREATE INDEX CONCURRENTLY")).toList()) jdbc.execute(line);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE c.relname IN ('idx_mr_scan_bah_numeric_cursor','idx_mr_scan_sjh_numeric_cursor') AND indisvalid AND indisready",Long.class)).isEqualTo(2);
        } finally { drop(); }
    }
    @Test void invalidSameNamedIndexIsRejectedInsteadOfSilentlySkipped() throws Exception {
        try {
            jdbc.update("INSERT INTO app.mr_scan(bah,filename,folder,uploadflag) VALUES ('9876','synthetic-index-a','synthetic',1),('9876','synthetic-index-b','synthetic',1)");
            assertThatThrownBy(()->jdbc.execute("CREATE UNIQUE INDEX CONCURRENTLY idx_mr_scan_bah_numeric_cursor ON app.mr_scan(app.numeric_code_key(bah))")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(()->jdbc.execute(guard())).hasMessageContaining("Invalid governance index exists");
        } finally { drop();jdbc.update("DELETE FROM app.mr_scan WHERE filename IN ('synthetic-index-a','synthetic-index-b')"); }
    }
    private void drop() {
        jdbc.execute("DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_bah_numeric_cursor");
        jdbc.execute("DROP INDEX CONCURRENTLY IF EXISTS app.idx_mr_scan_sjh_numeric_cursor");
    }
}
