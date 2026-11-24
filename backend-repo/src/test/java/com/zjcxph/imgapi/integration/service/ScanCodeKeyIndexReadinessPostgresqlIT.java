package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.service.DeploymentReadinessService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.flywaydb.core.Flyway;
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
import static org.mockito.Mockito.mock;

/**
 * 编号索引就绪检查在真实 PostgreSQL 上的行为。
 *
 * <p>单元测试用 mock 只能验证分支逻辑；这里验证真正发出去的目录查询 SQL 是否成立，
 * 以及「索引真的不存在」时确实被判为缺失。索引名与
 * {@code backend-repo/scripts/build-code-key-indexes.sql} 保持一致。</p>
 */
@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("病案影像编号索引就绪检查 PostgreSQL 集成测试")
class ScanCodeKeyIndexReadinessPostgresqlIT extends PostgresqlIntegrationTestSupport {

    private static final String BAH_INDEX = "idx_mr_scan_bah_numeric_cursor";
    private static final String SJH_INDEX = "idx_mr_scan_sjh_numeric_cursor";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 阈值设为 0，让「表规模」不再是判定条件，专注于索引存在性。
     */
    private DeploymentReadinessService service() {
        return new DeploymentReadinessService(
                jdbcTemplate,
                mock(Flyway.class),
                mock(ImageProperties.class),
                mock(ArchiveExportProperties.class),
                mock(ImageUrlService.class),
                mock(OssService.class),
                mock(SystemSettingService.class),
                0L,
                48L,
                1L,
                0L
        );
    }

    private void dropGovernanceIndexes() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS app." + BAH_INDEX);
        jdbcTemplate.execute("DROP INDEX IF EXISTS app." + SJH_INDEX);
    }

    private void createIndex(String name, String column) {
        jdbcTemplate.execute("CREATE INDEX " + name + " ON app.mr_scan (app.numeric_code_key(" + column + "), id)");
    }

    @Test
    @DisplayName("真实库上缺少索引时报出两个具体索引名")
    void reportsBothMissingIndexesAgainstRealCatalog() {
        dropGovernanceIndexes();

        Map<String, Object> check = service().checkScanCodeKeyIndexes();

        assertThat(check.get("passed")).isEqualTo(false);
        assertThat(check.get("severity")).isEqualTo("WARNING");
        Map<?, ?> details = (Map<?, ?>) check.get("details");
        assertThat(details.get("state")).isEqualTo("MISSING");
        assertThat(details.get("missing")).isEqualTo(List.of(BAH_INDEX, SJH_INDEX));
    }

    @Test
    @DisplayName("只建了一个索引时仍然报缺失，并准确指出缺哪一个")
    void reportsOnlyTheRemainingIndexWhenPartiallyBuilt() {
        dropGovernanceIndexes();
        createIndex(BAH_INDEX, "bah");

        Map<String, Object> check = service().checkScanCodeKeyIndexes();

        assertThat(check.get("passed")).isEqualTo(false);
        Map<?, ?> details = (Map<?, ?>) check.get("details");
        assertThat(details.get("present")).isEqualTo(List.of(BAH_INDEX));
        assertThat(details.get("missing")).isEqualTo(List.of(SJH_INDEX));
    }

    @Test
    @DisplayName("同名普通列及反序复合索引不能冒充比较键游标索引")
    void rejectsValidButWrongKeyDefinitions() {
        dropGovernanceIndexes();
        jdbcTemplate.execute("CREATE INDEX " + BAH_INDEX + " ON app.mr_scan (bah, id)");
        jdbcTemplate.execute("CREATE INDEX " + SJH_INDEX + " ON app.mr_scan (id, app.numeric_code_key(sjh))");
        Map<String, Object> check = service().checkScanCodeKeyIndexes();
        assertThat(check.get("passed")).isEqualTo(false);
        Map<?, ?> details = (Map<?, ?>) check.get("details");
        assertThat(details.get("invalid")).isEqualTo(List.of(BAH_INDEX, SJH_INDEX));
        assertThat(details.get("present")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("同名部分索引不能覆盖无 uploadflag 约束的游标")
    void rejectsPartialIndexEvenWithCorrectKeys() {
        dropGovernanceIndexes();
        jdbcTemplate.execute("CREATE INDEX " + BAH_INDEX + " ON app.mr_scan (app.numeric_code_key(bah), id) WHERE uploadflag <> 0");
        createIndex(SJH_INDEX, "sjh");
        Map<String, Object> check = service().checkScanCodeKeyIndexes();
        assertThat(check.get("passed")).isEqualTo(false);
        Map<?, ?> details = (Map<?, ?>) check.get("details");
        assertThat(details.get("invalid")).isEqualTo(List.of(BAH_INDEX));
        assertThat(details.get("present")).isEqualTo(List.of(SJH_INDEX));
    }

    @Test
    @DisplayName("同名索引属于另一张表时不得通过")
    void rejectsIndexOnWrongTable() {
        dropGovernanceIndexes();
        jdbcTemplate.execute("CREATE TABLE app.scan_index_shape_fixture (bah text, id integer)");
        jdbcTemplate.execute("CREATE INDEX " + BAH_INDEX + " ON app.scan_index_shape_fixture (app.numeric_code_key(bah), id)");
        createIndex(SJH_INDEX, "sjh");
        Map<String, Object> check = service().checkScanCodeKeyIndexes();
        assertThat(check.get("passed")).isEqualTo(false);
        assertThat(((Map<?, ?>) check.get("details")).get("invalid")).isEqualTo(List.of(BAH_INDEX));
    }

    @Test
    @DisplayName("两个索引建好后检查通过，并按脚本走建索引路径验证 SQL 可行")
    void passesOnceBothIndexesExist() {
        dropGovernanceIndexes();
        try {
            createIndex(BAH_INDEX, "bah");
            createIndex(SJH_INDEX, "sjh");

            Map<String, Object> check = service().checkScanCodeKeyIndexes();

            assertThat(check.get("passed")).isEqualTo(true);
            Map<?, ?> details = (Map<?, ?>) check.get("details");
            assertThat(details.get("state")).isEqualTo("READY");
            assertThat((List<?>) details.get("missing")).isEmpty();
        } finally {
            dropGovernanceIndexes();
        }
    }
}
