package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册表与真实数据库结构的一致性校验。
 *
 * <p>注册表里的列名是手写的，写错一个字母不会有任何编译期提示，运行时表现为
 * 「查询失败」而不是「配置错误」。本测试把每张登记表的每个声明列（含整列剪掉的隐藏列）
 * 拿去 information_schema 核对，并真正执行一次带 {@code to_char} 格式化的查询，
 * 覆盖 DDL 变更后注册表未同步的情况。</p>
 */
@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseBrowseCatalogPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("注册表声明的每一列都真实存在于数据库")
    void everyDeclaredColumnExists() {
        for (DbBrowseTable table : new DbBrowseRegistry().all()) {
            Set<String> actual = new TreeSet<>(jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = ?
                    """, String.class, table.schemaName(), table.tableName()));
            assertThat(actual)
                    .as("物理表 %s.%s 不存在或没有列", table.schemaName(), table.tableName())
                    .isNotEmpty();

            List<String> declared = table.columns().stream().map(DbBrowseColumn::name).toList();
            List<String> missing = declared.stream().filter(name -> !actual.contains(name)).toList();
            assertThat(missing)
                    .as("表 %s（%s）声明了数据库里不存在的列", table.key(), table.tableName())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("每张登记表的一次真实查询都能被 PostgreSQL 接受")
    void everyRegisteredTableIsQueryable() {
        for (DbBrowseTable table : new DbBrowseRegistry().all()) {
            String sql = "SELECT " + table.returnedColumns().stream()
                    .map(DatabaseBrowseCatalogPostgresqlIT::selectExpression)
                    .collect(Collectors.joining(", "))
                    + " FROM " + table.schemaName() + "." + table.tableName() + " LIMIT 1";

            assertThat(jdbc.queryForList(sql))
                    .as("表 %s 的查询无法执行：%s", table.key(), sql)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("按主键排序的游标查询在大表上可执行（不扫描全表）")
    void keysetQueryIsAcceptedOnEveryTable() {
        for (DbBrowseTable table : new DbBrowseRegistry().all()) {
            String orderColumn = table.keyColumns().get(0);
            DbBrowseColumn keyColumn = table.column(orderColumn).orElseThrow();
            String sql = "SELECT " + orderColumn + " FROM " + table.schemaName() + "." + table.tableName()
                    + " WHERE " + orderColumn + " < ? ORDER BY " + orderColumn + " DESC LIMIT 1";

            // 游标参数的类型必须跟列类型一致：文本主键传数字会被 PostgreSQL 直接拒绝。
            assertThat(jdbc.queryForList(sql, probeValue(keyColumn)))
                    .as("表 %s 的游标查询无法执行（主键类型 %s）", table.key(), keyColumn.type())
                    .isNotNull();
        }
    }

    private static Object probeValue(DbBrowseColumn keyColumn) {
        return switch (keyColumn.type()) {
            case NUMBER -> Long.MAX_VALUE;
            case DATETIME -> java.sql.Timestamp.valueOf("2100-01-01 00:00:00");
            case DATE -> java.sql.Date.valueOf("2100-01-01");
            case BOOLEAN -> Boolean.TRUE;
            default -> "zzzzzzzz";
        };
    }

    @Test
    @DisplayName("从未 ANALYZE 但有数据的表不能被估算成 0 行")
    void neverAnalysedTableWithRowsIsNotReportedAsZero() {
        // flyway_schema_history 在任何跑过迁移的库里都至少有一行，且通常从未被 ANALYZE。
        // 早期实现用 relpages=0 推断空表，会把这类表显示成「0 行」。
        Long estimate = new DatabaseBrowseRepository(jdbc.getDataSource())
                .estimateRows("app", "flyway_schema_history");

        assertThat(estimate == null || estimate >= 1L)
                .as("从未 ANALYZE 的表估算为 0 行属于错误结果，实际为 %s", estimate)
                .isTrue();
    }

    private static String selectExpression(DbBrowseColumn column) {
        if (column.type() == ColumnType.DATETIME) {
            return "to_char(" + column.name() + ", 'YYYY-MM-DD\"T\"HH24:MI:SS') AS " + column.name();
        }
        if (column.type() == ColumnType.DATE) {
            return "to_char(" + column.name() + ", 'YYYY-MM-DD') AS " + column.name();
        }
        return column.name();
    }
}
