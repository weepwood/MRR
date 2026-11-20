package com.zjcxph.imgapi.unit.setting.browse;

import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.MaskStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册表装配的完整性校验。
 *
 * <p>{@link DbBrowseRegistry} 会吞掉单表定义错误（只打日志），所以「写错一个列名」的后果是
 * 这张表从清单里静默消失，接口只报「表不存在」。本测试用固定清单把这个失败点从运行时
 * 挪到构建期。</p>
 */
class DbBrowseRegistryTest {

    private static final Set<String> EXPECTED_KEYS = Set.of(
            "frontend_response_metric",
            "system_settings",
            "archive_box_record",
            "patient",
            "archive",
            "scan",
            "statistics",
            "data_quality_issue",
            "system_error_event",
            "access_log",
            "auth_user",
            "auth_role",
            "archive_ip_binding",
            "external_access_log",
            "archive_search_history",
            "migration_job",
            "image_migration_log",
            "flyway_history",
            "availability_period",
            "permission_matrix_snapshot",
            "ocr_lexicon_set",
            "ocr_lexicon_entry",
            "ocr_batch",
            "classification_rule_set",
            "classification_rule_entry",
            "image_type_audit",
            "image_classification_suggestion",
            "smart_classification_job",
            "smart_classification_job_item",
            "data_quality_run",
            "data_quality_check"
    );

    /** 任何情况下都不允许以原值返回的列名，命中即视为脱敏配置漏了。 */
    private static final Set<String> NEVER_RAW_COLUMNS = Set.of(
            "password_hash", "password_version", "token_hash", "idcard",
            "name", "patientname", "patient_name", "display_name",
            "contact_info", "bah", "sjh", "query_string", "request_body", "oss_url"
    );

    private final DbBrowseRegistry registry = new DbBrowseRegistry();

    @Test
    @DisplayName("固定清单内的表全部登记成功，没有因定义非法被静默丢弃")
    void registersEveryTableInCatalog() {
        assertThat(registry.all()).extracting(DbBrowseTable::key)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
    }

    @Test
    @DisplayName("只有系统设置项默认关闭，其余表默认可用")
    void onlySettingsTableIsDisabledByDefault() {
        Set<String> disabled = registry.all().stream()
                .filter(table -> !table.enabled())
                .map(DbBrowseTable::key)
                .collect(Collectors.toSet());
        assertThat(disabled).containsExactly("system_settings");
    }

    @Test
    @DisplayName("标记为个人数据的表，敏感列一律脱敏或整列不返回")
    void personalColumnsAreNeverReturnedRaw() {
        for (DbBrowseTable table : registry.all()) {
            if (!table.personalData()) {
                continue;
            }
            for (DbBrowseColumn column : table.returnedColumns()) {
                if (NEVER_RAW_COLUMNS.contains(column.name())) {
                    assertThat(column.mask())
                            .as("表 %s 的列 %s 必须以脱敏或隐藏方式返回", table.key(), column.name())
                            .isNotEqualTo(MaskStrategy.NONE);
                }
            }
        }
    }

    @Test
    @DisplayName("标记为个人数据的表必须至少做过一次脱敏或剪列")
    void personalTablesApplyAtLeastOneMask() {
        for (DbBrowseTable table : registry.all()) {
            if (!table.personalData()) {
                continue;
            }
            boolean masked = table.returnedColumns().stream()
                    .anyMatch(column -> column.mask() != MaskStrategy.NONE);
            assertThat(masked || table.omittedColumnCount() > 0)
                    .as("表 %s 声明了个人数据，却没有任何脱敏或剪列", table.key())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("大表可直接浏览，但单页上限收紧，靠游标与超时兜住开销")
    void largeTablesStayBrowsableWithTightPageBudget() {
        DbBrowseTable scan = registry.find("scan").orElseThrow();
        DbBrowseTable patient = registry.find("patient").orElseThrow();

        // 大表不再强制过滤：首屏走主键索引倒序取一页，代价是 O(页大小) 而不是全表扫描。
        assertThat(scan.requireFilter()).isFalse();
        assertThat(patient.requireFilter()).isFalse();
        assertThat(scan.maxPageSize()).isLessThanOrEqualTo(50);
        assertThat(patient.maxPageSize()).isLessThanOrEqualTo(50);

        // requireFilter 机制保留，供将来出现无索引大表时使用；当前没有任何表启用它。
        assertThat(registry.all()).noneMatch(DbBrowseTable::requireFilter);
    }

    @Test
    @DisplayName("敏感表把不可外泄的列整列剪掉，连列名都不出现")
    void sensitiveTablesOmitSecretColumns() {
        assertThat(registry.find("auth_user").orElseThrow().returnedColumns())
                .extracting(DbBrowseColumn::name)
                .doesNotContain("password_hash", "password_version");
        assertThat(registry.find("access_log").orElseThrow().returnedColumns())
                .extracting(DbBrowseColumn::name)
                .doesNotContain("query_string", "request_body", "referer");
        assertThat(registry.find("access_log").orElseThrow().omittedColumnCount()).isEqualTo(3);
        // 任务租约令牌等同凭据：拿到它就能冒充 worker 续租/领任务，必须整列不返回。
        assertThat(registry.find("smart_classification_job").orElseThrow().returnedColumns())
                .extracting(DbBrowseColumn::name)
                .doesNotContain("lease_token");
    }

    @Test
    @DisplayName("每张表的可排序列都包含主键列，保证游标翻页稳定")
    void keyColumnsAreOrderable() {
        for (DbBrowseTable table : registry.all()) {
            assertThat(table.orderableColumns())
                    .as("表 %s 的排序列必须覆盖 keyColumns", table.key())
                    .containsAll(table.keyColumns());
        }
    }

    @Test
    @DisplayName("表标识与物理表名不复用，避免路径参数被当表名直传")
    void keysAreDistinct() {
        List<String> keys = registry.all().stream().map(DbBrowseTable::key).toList();
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(key -> assertThat(key).matches("^[a-z][a-z0-9_]{2,40}$"));
    }
}
