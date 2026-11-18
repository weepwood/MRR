package com.zjcxph.imgapi.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowseColumnView;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowsePage;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.DatabaseBrowseRepository;
import com.zjcxph.imgapi.service.DatabaseBrowseService;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseRegistry;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.FilterOp;
import com.zjcxph.imgapi.setting.browse.MaskStrategy;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("受控只读数据库浏览服务测试")
class DatabaseBrowseServiceTest {

    private static final String TABLE_KEY = "demo_patient";

    @Mock
    private DbBrowseRegistry registry;

    @Mock
    private DatabaseBrowseRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DatabaseBrowseService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseBrowseService(registry, repository, objectMapper, true);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    // ------------------------------------------------------------ 访问控制

    @Test
    @DisplayName("功能开关关闭时返回 3007，且不访问注册表与数据库")
    void rejectsWhenDisabled() {
        service = new DatabaseBrowseService(registry, repository, objectMapper, false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 1, 10, null)))
                .isEqualTo(3007);
        verifyNoInteractions(registry, repository);
    }

    @Test
    @DisplayName("未登记的表返回 3001，且不执行任何查询")
    void rejectsUnknownTable() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        when(registry.find("missing")).thenReturn(Optional.empty());

        assertThat(codeOf(() -> service.readRows("missing", null, null, "desc", 1, 10, null)))
                .isEqualTo(3001);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("缺少表级权限时返回 3001，而不是 403，避免枚举表存在")
    void rejectsWithoutTablePermission() {
        login(Permissions.SYSTEM_DB_BROWSE);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 1, 10, null)))
                .isEqualTo(3001);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("record:manage 通过继承获得 record:read，可访问病案表")
    void allowsThroughPermissionInheritance() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_MANAGE);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of());

        DbBrowsePage page = service.readRows(TABLE_KEY, null, null, "desc", 1, 10, null);

        assertThat(page.rows()).isEmpty();
    }

    // ------------------------------------------------------------ 参数与预算

    @Test
    @DisplayName("要求过滤的表缺少过滤条件时返回 3003")
    void requiresFilter() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(true);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 1, 10, null)))
                .isEqualTo(3003);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("页大小超过注册表上限返回 3005")
    void rejectsOversizedPage() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 1, 51, null)))
                .isEqualTo(3005);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("页大小小于等于 0 返回 3002")
    void rejectsNonPositiveSize() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 1, 0, null)))
                .isEqualTo(3002);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("注入形态的 orderBy 被拒绝且不产生查询")
    void rejectsInjectedOrderBy() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, "id; DROP TABLE app.mr_patient--", "desc", 1, 10, null)))
                .isEqualTo(3002);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("不可过滤的列出现在 filter 中返回 3002")
    void rejectsNonFilterableColumn() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, "id:EQ:1", null, "desc", 1, 10, null)))
                .isEqualTo(3002);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("列未开放的操作符返回 3002")
    void rejectsUnsupportedOperator() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, "bah:GT:12345678", null, "desc", 1, 10, null)))
                .isEqualTo(3002);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("page 大于 1 但缺少游标返回 3006，不产生 OFFSET 查询")
    void rejectsDeepPageWithoutCursor() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 2, 10, null)))
                .isEqualTo(3006);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("其他表的游标返回 3006")
    void rejectsForeignCursor() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        String foreignCursor = encodeCursor("{\"k\":\"other_table\",\"c\":[\"id\"],\"d\":\"desc\",\"v\":[5]}");

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 2, 10, foreignCursor)))
                .isEqualTo(3006);
        verify(repository, never()).query(anyString(), anyList());
    }

    @Test
    @DisplayName("游标方向与请求方向不一致时返回 3006，而不是静默返回错误页")
    void rejectsCursorWithMismatchedDirection() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        String ascCursor = encodeCursor("{\"k\":\"" + TABLE_KEY + "\",\"c\":[\"id\"],\"d\":\"asc\",\"v\":[5]}");

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 2, 10, ascCursor)))
                .isEqualTo(3006);
        verify(repository, never()).query(anyString(), anyList());
    }

    @Test
    @DisplayName("游标值无法转成排序列类型时返回 3006，而不是 3002 或 500")
    void rejectsCursorWithUncoercibleValue() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        String tampered = encodeCursor("{\"k\":\"" + TABLE_KEY + "\",\"c\":[\"id\"],\"d\":\"desc\",\"v\":[\"not-a-number\"]}");

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 2, 10, tampered)))
                .isEqualTo(3006);
        verify(repository, never()).query(anyString(), anyList());
    }

    @Test
    @DisplayName("order 只接受 asc/desc，其他取值返回 3002")
    void rejectsUnknownOrder() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "foo", 1, 10, null)))
                .isEqualTo(3002);
        verify(repository, never()).query(anyString(), anyList());
    }

    @Test
    @DisplayName("page 小于 1 返回 3002")
    void rejectsNonPositivePage() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, null, null, "desc", 0, 10, null)))
                .isEqualTo(3002);
        verify(repository, never()).query(anyString(), anyList());
    }

    @Test
    @DisplayName("掩码列不接受前缀过滤，否则可逐字符还原原值")
    void rejectsPrefixOnMaskedColumn() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, "bah:PREFIX:1234", null, "desc", 1, 10, null)))
                .isEqualTo(3002);
        assertThat(codeOf(() -> service.readRows(TABLE_KEY, "name:PREFIX:张", null, "desc", 1, 10, null)))
                .isEqualTo(3002);
        verify(repository, never()).query(anyString(), anyList());
    }

    // ------------------------------------------------------------ 查询与脱敏

    @Test
    @DisplayName("返回行按策略脱敏，隐藏列不出现在响应结构中")
    void masksRowsAndHidesColumns() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of(sampleRow()));

        DbBrowsePage page = service.readRows(TABLE_KEY, null, null, "desc", 1, 10, null);

        assertThat(page.columns()).extracting(DbBrowseColumnView::name)
                .containsExactly("id", "bah", "name", "note", "created_at");
        assertThat(page.omittedColumnCount()).isEqualTo(1);
        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0))
                .containsEntry("id", 7L)
                .containsEntry("bah", "****5678")
                .containsEntry("name", "张*")
                .doesNotContainKey("password_hash");
    }

    @Test
    @DisplayName("SQL 只由注册表列名与绑定占位符组成，不拼接用户值")
    void buildsParameterisedSql() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of());

        service.readRows(TABLE_KEY, "bah:EQ:12345678", null, "desc", 1, 10, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).query(sqlCaptor.capture(), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .startsWith("SELECT id, bah, name,")
                .contains("FROM app.mr_patient")
                .contains("WHERE bah = ?")
                .contains("ORDER BY id DESC")
                .endsWith("LIMIT ?")
                .doesNotContain("12345678");
        assertThat(argsCaptor.getValue()).containsExactly("12345678", 11);
    }

    @Test
    @DisplayName("时间过滤值转成 Timestamp，避免 timestamp >= varchar 报错")
    void convertsTemporalFilterToTimestamp() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of());

        service.readRows(TABLE_KEY, "created_at:GTE:2026-11-01T00:00:00", null, "desc", 1, 10, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).query(anyString(), argsCaptor.capture());
        assertThat(argsCaptor.getValue().get(0)).isInstanceOf(java.sql.Timestamp.class);
    }

    @Test
    @DisplayName("时间格式非法时返回 3002，不把字符串交给数据库")
    void rejectsInvalidTemporalValue() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        assertThat(codeOf(() -> service.readRows(TABLE_KEY, "created_at:GTE:2026-11-01", null, "desc", 1, 10, null)))
                .isEqualTo(3002);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("LIKE 前缀匹配会转义通配符，% 不作为通配使用")
    void escapesLikeWildcards() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of());

        service.readRows(TABLE_KEY, "note:PREFIX:12%_", null, "desc", 1, 10, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).query(anyString(), argsCaptor.capture());
        assertThat(argsCaptor.getValue().get(0)).isEqualTo("12\\%\\_%");
    }

    @Test
    @DisplayName("过滤值中的 %2C 还原为逗号，其余百分号原样保留")
    void decodesOnlyEncodedComma() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);
        when(repository.query(anyString(), anyList())).thenReturn(List.of());

        service.readRows(TABLE_KEY, "name:EQ:a%2Cb%25c", null, "desc", 1, 10, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).query(anyString(), argsCaptor.capture());
        assertThat(argsCaptor.getValue().get(0)).isEqualTo("a,b%25c");
    }

    @Test
    @DisplayName("复合主键翻页用行值比较，两列各自保留原始类型")
    void compositeKeyCursorKeepsColumnTypes() {
        String key = "demo_job_item";
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        when(registry.find(key)).thenReturn(Optional.of(compositeKeyTable()));

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("job_id", "job-1");
        first.put("scan_id", 20L);
        first.put("status", "DONE");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("job_id", "job-1");
        second.put("scan_id", 19L);
        second.put("status", "DONE");
        when(repository.query(anyString(), anyList())).thenReturn(List.of(first, second));

        DbBrowsePage page1 = service.readRows(key, null, null, "desc", 1, 1, null);

        assertThat(page1.rows()).hasSize(1);
        assertThat(page1.hasMore()).isTrue();
        String cursor = page1.nextCursor();
        assertThat(cursor).isNotBlank();

        when(repository.query(anyString(), anyList())).thenReturn(List.of());
        service.readRows(key, null, null, "desc", 2, 1, cursor);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object>> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).query(sqlCaptor.capture(), argsCaptor.capture());

        // 复合键必须整行比较：拆成 job_id < ? AND scan_id < ? 会漏行。
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("WHERE (job_id, scan_id) < (?, ?)")
                .contains("ORDER BY job_id DESC, scan_id DESC");
        // 文本键还原成 String、数值键还原成 Long，不能一律当字符串绑定。
        assertThat(argsCaptor.getAllValues().get(1)).containsExactly("job-1", 20L, 2);
    }

    @Test
    @DisplayName("表结构查询不返回隐藏列")
    void schemaOmitsHiddenColumn() {
        login(Permissions.SYSTEM_DB_BROWSE, Permissions.RECORD_READ);
        stubTable(false);

        var schema = service.getSchema(TABLE_KEY);

        assertThat(schema.columns()).extracting(DbBrowseColumnView::name)
                .containsExactly("id", "bah", "name", "note", "created_at");
        assertThat(schema.omittedColumnCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------ 工具

    private void login(String... permissions) {
        AuthSession session = new AuthSession();
        session.setRoleCode("USER");
        session.setPermissions(List.of(permissions));
        AuthContext.setCurrentUser(session);
    }

    private void stubTable(boolean requireFilter) {
        when(registry.find(TABLE_KEY)).thenReturn(Optional.of(demoTable(requireFilter)));
    }

    private static DbBrowseTable demoTable(boolean requireFilter) {
        return DbBrowseTable.builder()
                .key(TABLE_KEY)
                .schemaName("app")
                .tableName("mr_patient")
                .label("患者主索引")
                .description("测试用表")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(50)
                .requireFilter(requireFilter)
                .estimateRowCount(false)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false,
                                Set.of(FilterOp.EQ, FilterOp.NE)),
                        DbBrowseColumn.masked("name", "姓名", MaskStrategy.NAME, true, false),
                        new DbBrowseColumn("note", "备注", ColumnType.TEXT, MaskStrategy.NONE,
                                true, true, false, 512, Set.of(FilterOp.EQ, FilterOp.PREFIX)),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.hidden("password_hash", "密码哈希", ColumnType.TEXT)
                ))
                .build();
    }

    private static DbBrowseTable compositeKeyTable() {
        return DbBrowseTable.builder()
                .key("demo_job_item")
                .schemaName("app")
                .tableName("mr_smart_classification_job_item")
                .label("任务明细")
                .description("复合主键测试表")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("job_id", "scan_id"))
                .orderableColumns(Set.of("job_id", "scan_id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(false)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("job_id", "任务编号", true, true),
                        DbBrowseColumn.number("scan_id", "影像编号", true, true),
                        DbBrowseColumn.text("status", "状态", true, false)
                ))
                .build();
    }

    private static Map<String, Object> sampleRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("bah", "12345678");
        row.put("name", "张三");
        row.put("password_hash", "secret");
        return row;
    }

    private static String encodeCursor(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Integer codeOf(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        try {
            callable.call();
            return null;
        } catch (BusinessException e) {
            return e.getCode();
        } catch (Throwable e) {
            throw new AssertionError("期望业务异常，实际为: " + e, e);
        }
    }
}
