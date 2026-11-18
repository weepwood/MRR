package com.zjcxph.imgapi.setting.browse.catalog;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.MaskStrategy;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 平台自身产生的运行与配置数据。
 *
 * <p>不涉及病案内容，但 {@code mr_system_settings} 的配置值可能含密钥，
 * 因此默认关闭且整列不返回。</p>
 */
public final class PlatformCatalog {

    private PlatformCatalog() {
    }

    public static List<Supplier<DbBrowseTable>> entries() {
        return List.of(
                PlatformCatalog::responseMetricTable,
                PlatformCatalog::systemSettingsTable
        );
    }

    private static DbBrowseTable responseMetricTable() {
        return DbBrowseTable.builder()
                .key("frontend_response_metric")
                .schemaName("app")
                .tableName("frontend_response_metric")
                .label("前端响应指标")
                .description("前端上报的接口耗时与结果，用于排查页面慢与失败请求")
                .enabled(true)
                .requiredPermissions(List.of())
                .keyColumns(List.of("request_id"))
                .orderableColumns(Set.of("request_id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("request_id", "请求编号", true, true),
                        DbBrowseColumn.text("route_pattern", "路由模板", true, false),
                        DbBrowseColumn.text("method", "请求方法", true, false),
                        DbBrowseColumn.number("http_status", "HTTP 状态", true, false),
                        DbBrowseColumn.number("business_code", "业务码", true, false),
                        DbBrowseColumn.bool("success", "是否成功"),
                        DbBrowseColumn.number("client_duration_ms", "客户端耗时(ms)", true, false),
                        DbBrowseColumn.number("server_duration_ms", "服务端耗时(ms)", true, false),
                        DbBrowseColumn.dateTime("occurred_at", "发生时间", true)
                ))
                .build();
    }

    private static DbBrowseTable systemSettingsTable() {
        return DbBrowseTable.builder()
                .key("system_settings")
                .schemaName("app")
                .tableName("mr_system_settings")
                .label("系统设置项")
                .description("配置键与说明；配置值可能含敏感内容，整列不返回")
                .enabled(false)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("setting_key", "配置键", true, false),
                        DbBrowseColumn.hidden("setting_value", "配置值", ColumnType.TEXT),
                        DbBrowseColumn.text("description", "说明", false, false),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true),
                        DbBrowseColumn.masked("updated_by", "更新人", MaskStrategy.NAME, false, false)
                ))
                .build();
    }
}
