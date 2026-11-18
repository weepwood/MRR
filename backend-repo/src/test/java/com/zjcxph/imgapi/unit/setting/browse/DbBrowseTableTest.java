package com.zjcxph.imgapi.unit.setting.browse;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.FilterOp;
import com.zjcxph.imgapi.setting.browse.MaskStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("浏览表定义校验测试")
class DbBrowseTableTest {

    private static DbBrowseTable.DbBrowseTableBuilder validBuilder() {
        return DbBrowseTable.builder()
                .key("demo_table")
                .schemaName("app")
                .tableName("mr_patient")
                .label("演示表")
                .description("用于校验")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(50)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false),
                        DbBrowseColumn.hidden("password_hash", "密码哈希", ColumnType.TEXT)
                ));
    }

    @Test
    @DisplayName("合法定义可以构建，隐藏列不计入返回列")
    void validDefinition() {
        DbBrowseTable table = validBuilder().build();

        assertThat(table.returnedColumns()).extracting(DbBrowseColumn::name)
                .containsExactly("id", "bah");
        assertThat(table.omittedColumnCount()).isEqualTo(1);
        assertThat(table.filterableColumnNames()).containsExactly("bah");
    }

    @Test
    @DisplayName("非法 key 直接失败")
    void rejectsInvalidKey() {
        assertThatThrownBy(() -> validBuilder().key("Demo").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法表标识");
    }

    @Test
    @DisplayName("只允许 app 模式")
    void rejectsForeignSchema() {
        assertThatThrownBy(() -> validBuilder().schemaName("public").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许浏览");
    }

    @Test
    @DisplayName("keyColumn 必须在列声明中登记")
    void rejectsUnknownKeyColumn() {
        assertThatThrownBy(() -> validBuilder().keyColumns(List.of("missing")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keyColumn 未在列声明中登记");
    }

    @Test
    @DisplayName("重复列声明直接失败")
    void rejectsDuplicateColumn() {
        assertThatThrownBy(() -> validBuilder().columns(List.of(
                DbBrowseColumn.number("id", "主键", false, true),
                DbBrowseColumn.number("id", "主键2", false, true)
        )).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复列声明");
    }

    @Test
    @DisplayName("页大小上限必须在 1..200")
    void rejectsInvalidPageSize() {
        assertThatThrownBy(() -> validBuilder().maxPageSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页大小上限");
    }

    @Test
    @DisplayName("未登记的权限代码直接失败")
    void rejectsUnknownPermission() {
        assertThatThrownBy(() -> validBuilder().requiredPermissions(List.of("system:unknown")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未登记的权限代码");
    }

    @Test
    @DisplayName("没有任何可见列的表不可用")
    void rejectsTableWithoutReturnedColumn() {
        assertThatThrownBy(() -> validBuilder()
                .columns(List.of(DbBrowseColumn.hidden("secret", "仅隐藏列", ColumnType.TEXT)))
                .keyColumns(List.of("secret"))
                .orderableColumns(Set.of("secret"))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("排序列必须可见");
    }

    @Test
    @DisplayName("掩码列不允许前缀/包含过滤：过滤按原值匹配会绕过掩码")
    void rejectsPrefixOnMaskedColumn() {
        assertThatThrownBy(() -> new DbBrowseColumn("name", "姓名", ColumnType.TEXT, MaskStrategy.NAME,
                true, true, false, 128, Set.of(FilterOp.EQ, FilterOp.PREFIX)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("掩码列不允许前缀");
    }

    @Test
    @DisplayName("排序列必须可见，否则游标会把该列原值带进响应")
    void rejectsHiddenSortColumn() {
        assertThatThrownBy(() -> validBuilder()
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.hidden("secret", "隐藏列", ColumnType.TEXT)
                ))
                .keyColumns(List.of("secret"))
                .orderableColumns(Set.of("secret"))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("排序列必须可见");
    }
}
