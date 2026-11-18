package com.zjcxph.imgapi.integration.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseColumnView;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowsePage;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import com.zjcxph.imgapi.service.DatabaseBrowseService;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受控只读数据库浏览的 PostgreSQL 集成测试。
 *
 * <p>单元测试 mock 掉数据访问层，覆盖不到「SQL 能否被 PostgreSQL 接受」；
 * 本类用真实 PostgreSQL 验证列名、{@code to_char} 格式、游标翻页与脱敏。</p>
 */
@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DatabaseBrowsePostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private DatabaseBrowseService service;

    @BeforeEach
    void setUp() {
        // 测试事务内清空，方法结束后随回滚恢复，断言因此可以精确到行数。
        jdbc.update("DELETE FROM app.mr_archive_box_record");
        service = new DatabaseBrowseService(
                new DbBrowseRegistry(),
                new DatabaseBrowseRepository(jdbc.getDataSource()),
                new ObjectMapper(),
                true);
        AuthSession session = new AuthSession();
        session.setRoleCode("USER");
        session.setPermissions(List.of(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ));
        AuthContext.setCurrentUser(session);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("真实 SQL 可执行：列名、时间格式化、脱敏与隐藏列")
    void readsRealRowsWithMasking() {
        insert("90000001", "S-001");
        insert("90000002", "S-002");

        DbBrowsePage page = service.readRows("archive_box_record", null, null, "desc", 1, 10, null);

        assertThat(page.rows()).hasSize(2);
        assertThat(page.columns()).extracting(DbBrowseColumnView::name)
                .containsExactly("id", "bah", "sjh", "box_no", "expected_box_no", "status", "remark", "created_at", "updated_at");
        Map<String, Object> first = page.rows().get(0);
        assertThat(first.get("bah")).asString().startsWith("****").endsWith("0002");
        // 上架号只有 5 位，按「保留后 4 位」规则只剩 1 个掩码字符
        assertThat(first.get("sjh")).asString().hasSize(5).isNotEqualTo("S-002");
        // 时间列由 SQL 内 to_char 输出，不带时区后缀
        assertThat(first.get("created_at")).asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(page.omittedColumnCount()).isZero();
    }

    @Test
    @DisplayName("游标翻页不重复、不丢行")
    void cursorPaginationIsStable() {
        insert("90000011", "S-011");
        insert("90000012", "S-012");
        insert("90000013", "S-013");

        DbBrowsePage firstPage = service.readRows("archive_box_record", null, null, "desc", 1, 2, null);
        assertThat(firstPage.rows()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        DbBrowsePage secondPage = service.readRows("archive_box_record", null, null, "desc", 2, 2, firstPage.nextCursor());
        assertThat(secondPage.rows()).hasSize(1);
        assertThat(secondPage.hasMore()).isFalse();

        List<Object> firstIds = firstPage.rows().stream().map(row -> row.get("id")).toList();
        List<Object> secondIds = secondPage.rows().stream().map(row -> row.get("id")).toList();
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
    }

    @Test
    @DisplayName("时间范围过滤在真实库上可用（参数为 Timestamp，不是 varchar）")
    void temporalFilterWorksOnRealDatabase() {
        insert("90000021", "S-021");

        DbBrowsePage page = service.readRows(
                "archive_box_record", "created_at:GTE:2000-01-01T00:00:00", null, "desc", 1, 10, null);

        assertThat(page.rows()).isNotEmpty();
    }

    @Test
    @DisplayName("病案号等值过滤命中真实数据（掩码列不开放前缀匹配）")
    void exactFilterHitsRealRows() {
        insert("90000031", "S-031");

        DbBrowsePage page = service.readRows("archive_box_record", "bah:EQ:90000031", null, "desc", 1, 10, null);

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0).get("bah")).asString().endsWith("0031");
    }

    private void insert(String bah, String sjh) {
        jdbc.update("""
                INSERT INTO app.mr_archive_box_record (bah, sjh, box_no, status)
                VALUES (?, ?, ?, 'NORMAL')
                """, bah, sjh, "BOX-" + sjh);
    }
}
