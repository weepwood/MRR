package com.zjcxph.imgapi.setting.browse.catalog;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;
import com.zjcxph.imgapi.setting.browse.FilterOp;
import com.zjcxph.imgapi.setting.browse.MaskStrategy;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 运维与账号域：错误、审计、账号角色、迁移与版本记录。
 *
 * <p>这一组不返回病案内容，但「谁在什么时候查了哪个病案」本身是敏感事实，
 * 所以审计类表统一要求 {@link Permissions#SYSTEM_MANAGE}；
 * 只有纯技术状态的错误事件与迁移任务对 {@link Permissions#SYSTEM_READ} 开放。</p>
 */
public final class OpsCatalog {

    private static final Set<FilterOp> CODE_OPS = Set.of(FilterOp.EQ);

    private OpsCatalog() {
    }

    public static List<Supplier<DbBrowseTable>> entries() {
        return List.of(
                OpsCatalog::systemErrorEventTable,
                OpsCatalog::accessLogTable,
                OpsCatalog::authUserTable,
                OpsCatalog::authRoleTable,
                OpsCatalog::archiveIpBindingTable,
                OpsCatalog::externalAccessLogTable,
                OpsCatalog::archiveSearchHistoryTable,
                OpsCatalog::migrationJobTable,
                OpsCatalog::imageMigrationLogTable,
                OpsCatalog::flywayHistoryTable,
                OpsCatalog::runtimeLogSourceTable,
                OpsCatalog::availabilityPeriodTable,
                OpsCatalog::permissionMatrixSnapshotTable
        );
    }

    private static DbBrowseTable systemErrorEventTable() {
        return DbBrowseTable.builder()
                .key("system_error_event")
                .schemaName("app")
                .tableName("system_error_event")
                .label("系统错误事件")
                .description("错误中心的聚合记录：指纹、级别、发生次数与处理状态；堆栈在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_ERROR_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("error_id", "错误编号", true, false),
                        DbBrowseColumn.hidden("error_ids", "关联错误编号", ColumnType.TEXT),
                        DbBrowseColumn.text("fingerprint", "指纹", true, false),
                        DbBrowseColumn.text("level", "级别", true, false),
                        DbBrowseColumn.text("module", "模块", true, false),
                        DbBrowseColumn.text("logger_name", "日志分类", true, false),
                        DbBrowseColumn.text("exception_type", "异常类型", true, false),
                        DbBrowseColumn.text("message_summary", "摘要", false, false, 500),
                        DbBrowseColumn.text("stack_trace", "堆栈", false, false, 2000),
                        DbBrowseColumn.text("request_id", "请求编号", true, false),
                        DbBrowseColumn.text("thread_name", "线程", false, false),
                        DbBrowseColumn.dateTime("first_seen_at", "首次发生", true),
                        DbBrowseColumn.dateTime("last_seen_at", "最近发生", true),
                        DbBrowseColumn.number("occurrence_count", "发生次数", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("acknowledged_by", "确认人", false, false),
                        DbBrowseColumn.dateTime("resolved_at", "解决时间", true)
                ))
                .build();
    }

    private static DbBrowseTable accessLogTable() {
        return DbBrowseTable.builder()
                .key("access_log")
                .schemaName("app")
                .tableName("access_log")
                .label("访问审计")
                .description("接口访问与业务审计记录；请求体、查询串与来源页可能含敏感信息，整列不返回")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("username", "账号", true, false),
                        DbBrowseColumn.text("client_ip", "来源 IP", true, false),
                        DbBrowseColumn.text("request_uri", "请求地址", true, false, 200),
                        DbBrowseColumn.text("method", "请求方法", true, false),
                        DbBrowseColumn.text("endpoint_template", "端点模板", true, false, 200),
                        DbBrowseColumn.text("user_agent", "客户端标识", false, false, 120),
                        DbBrowseColumn.dateTime("access_time", "访问时间", true),
                        DbBrowseColumn.hidden("query_string", "查询串", ColumnType.TEXT),
                        DbBrowseColumn.hidden("request_body", "请求体", ColumnType.TEXT),
                        DbBrowseColumn.hidden("referer", "来源页", ColumnType.TEXT),
                        DbBrowseColumn.text("response_status", "响应状态", true, false),
                        DbBrowseColumn.number("execute_time", "耗时(ms)", true, false),
                        DbBrowseColumn.text("audit_action", "审计动作", true, false),
                        DbBrowseColumn.text("audit_target", "审计对象", true, false, 200),
                        DbBrowseColumn.text("audit_description", "审计说明", false, false, 200),
                        DbBrowseColumn.text("request_id", "请求编号", true, false),
                        DbBrowseColumn.text("error_message", "错误信息", false, false, 500)
                ))
                .build();
    }

    private static DbBrowseTable authUserTable() {
        return DbBrowseTable.builder()
                .key("auth_user")
                .schemaName("app")
                .tableName("mr_auth_user")
                .label("系统账号")
                .description("账号、角色与登录信息；密码哈希与密码版本整列不返回，联系方式按电话号码脱敏")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("username", "登录名", true, false),
                        DbBrowseColumn.masked("display_name", "姓名", MaskStrategy.NAME, true, false),
                        DbBrowseColumn.hidden("password_hash", "密码哈希", ColumnType.TEXT),
                        DbBrowseColumn.hidden("password_version", "密码版本", ColumnType.NUMBER),
                        DbBrowseColumn.text("role_code", "角色代码", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.dateTime("last_login_at", "最近登录", true),
                        DbBrowseColumn.bool("must_change_password", "需改密码"),
                        DbBrowseColumn.dateTime("password_changed_at", "密码修改时间", true),
                        DbBrowseColumn.dateTime("temporary_password_expires_at", "临时密码到期", true),
                        DbBrowseColumn.number("password_reset_by", "重置操作人", false, false),
                        DbBrowseColumn.dateTime("password_reset_at", "密码重置时间", true),
                        DbBrowseColumn.masked("contact_info", "联系方式", MaskStrategy.PHONE, false, false),
                        DbBrowseColumn.text("apply_remark", "申请说明", false, false, 200),
                        DbBrowseColumn.dateTime("applied_at", "申请时间", true),
                        DbBrowseColumn.dateTime("reviewed_at", "审核时间", true),
                        DbBrowseColumn.number("reviewed_by", "审核人", false, false),
                        DbBrowseColumn.text("reject_reason", "驳回原因", false, false, 200),
                        DbBrowseColumn.number("created_by", "创建人", false, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable authRoleTable() {
        return DbBrowseTable.builder()
                .key("auth_role")
                .schemaName("app")
                .tableName("mr_auth_role")
                .label("角色权限")
                .description("角色代码、名称与权限清单；用于核对「某个角色到底被授予了什么」")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("code"))
                .orderableColumns(Set.of("code"))
                .maxPageSize(50)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("code", "角色代码", true, true),
                        DbBrowseColumn.text("name", "角色名称", true, false),
                        DbBrowseColumn.text("description", "说明", false, false, 200),
                        DbBrowseColumn.text("permissions", "权限清单", false, false, 2000),
                        DbBrowseColumn.number("sort_order", "排序", false, false)
                ))
                .build();
    }

    private static DbBrowseTable archiveIpBindingTable() {
        return DbBrowseTable.builder()
                .key("archive_ip_binding")
                .schemaName("app")
                .tableName("mr_archive_ip_binding")
                .label("档案 IP 绑定")
                .description("按日登记的用户与绑定 IP，以及当日换 IP 次数")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.date("access_date", "访问日期", true),
                        DbBrowseColumn.text("userid", "用户", true, false),
                        DbBrowseColumn.text("bound_ip", "绑定 IP", true, false),
                        DbBrowseColumn.number("ip_change_count", "换 IP 次数", true, false),
                        DbBrowseColumn.dateTime("first_access_at", "首次访问", true),
                        DbBrowseColumn.dateTime("last_access_at", "最近访问", true)
                ))
                .build();
    }

    private static DbBrowseTable externalAccessLogTable() {
        return DbBrowseTable.builder()
                .key("external_access_log")
                .schemaName("app")
                .tableName("mr_external_archive_access_log")
                .label("外部调阅审计")
                .description("外部系统调阅病案的审计明细：接入方、动作、结果与来源 IP")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("client_id", "接入方", true, false),
                        DbBrowseColumn.text("external_user_id", "外部用户", true, false),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.text("action", "动作", true, false),
                        DbBrowseColumn.number("image_id", "影像编号", true, false),
                        DbBrowseColumn.text("client_ip", "来源 IP", true, false),
                        DbBrowseColumn.text("user_agent", "客户端标识", false, false, 120),
                        DbBrowseColumn.text("result", "结果", true, false),
                        DbBrowseColumn.text("request_id", "请求编号", true, false),
                        DbBrowseColumn.text("detail", "详情", false, false, 200),
                        DbBrowseColumn.dateTime("created_at", "发生时间", true)
                ))
                .build();
    }

    private static DbBrowseTable archiveSearchHistoryTable() {
        return DbBrowseTable.builder()
                .key("archive_search_history")
                .schemaName("app")
                .tableName("mr_archive_search_history")
                .label("病案检索历史")
                .description("按用户记录的病案检索历史：查了什么、是否成功、返回多少影像")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("user_id", "用户编号", true, false),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.bool("success", "是否成功"),
                        DbBrowseColumn.number("image_count", "影像数量", true, false),
                        DbBrowseColumn.text("failure_reason", "失败原因", true, false, 200),
                        DbBrowseColumn.bool("favorite", "是否收藏"),
                        DbBrowseColumn.number("query_count", "查询次数", false, false),
                        DbBrowseColumn.dateTime("searched_at", "检索时间", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable migrationJobTable() {
        return DbBrowseTable.builder()
                .key("migration_job")
                .schemaName("app")
                .tableName("migration_job")
                .label("数据迁移任务")
                .description("影像迁移任务的进度、速率与失败数，用于核对迁移是否卡住")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("mode", "模式", true, false),
                        DbBrowseColumn.text("scope_value", "迁移范围", false, false, 200),
                        DbBrowseColumn.number("total_count", "总数", true, false),
                        DbBrowseColumn.number("requested_count", "请求数量", false, false),
                        DbBrowseColumn.number("processed_count", "已处理", true, false),
                        DbBrowseColumn.number("failed_count", "失败数", true, false),
                        DbBrowseColumn.number("rate", "速率", false, false),
                        DbBrowseColumn.number("max_scan_id", "最大影像编号", false, false),
                        DbBrowseColumn.bool("cancel_requested", "请求取消"),
                        DbBrowseColumn.text("error_message", "错误信息", false, false, 500),
                        DbBrowseColumn.text("created_by", "创建人", true, false),
                        DbBrowseColumn.dateTime("started_at", "开始时间", true),
                        DbBrowseColumn.dateTime("completed_at", "完成时间", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable imageMigrationLogTable() {
        return DbBrowseTable.builder()
                .key("image_migration_log")
                .schemaName("app")
                .tableName("image_migration_log")
                .label("影像迁移日志")
                .description("单个影像的迁移结果；本地路径只保留文件名，OSS 地址整列不返回")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("scan_id", "影像编号", true, false),
                        DbBrowseColumn.masked("local_path", "本地文件名", MaskStrategy.PATH, false, false),
                        DbBrowseColumn.hidden("oss_url", "OSS 地址", ColumnType.TEXT),
                        DbBrowseColumn.text("migration_status", "迁移状态", true, false),
                        DbBrowseColumn.text("error_message", "错误信息", false, false, 500),
                        DbBrowseColumn.number("file_size", "文件大小(字节)", false, false),
                        DbBrowseColumn.text("checksum_md5", "MD5", false, false, 64),
                        DbBrowseColumn.dateTime("migrated_at", "迁移时间", true),
                        DbBrowseColumn.dateTime("verified_at", "校验时间", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable flywayHistoryTable() {
        return DbBrowseTable.builder()
                .key("flyway_history")
                .schemaName("app")
                .tableName("flyway_schema_history")
                .label("数据库版本历史")
                .description("Flyway 迁移执行记录；核对当前版本与是否有失败迁移时的第一现场")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("installed_rank"))
                .orderableColumns(Set.of("installed_rank"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("installed_rank", "序号", true, true),
                        DbBrowseColumn.text("version", "版本", true, false),
                        DbBrowseColumn.text("description", "描述", true, false, 200),
                        DbBrowseColumn.text("type", "类型", true, false),
                        DbBrowseColumn.text("script", "脚本", true, false, 200),
                        DbBrowseColumn.number("checksum", "校验和", false, false),
                        DbBrowseColumn.text("installed_by", "执行账号", false, false),
                        DbBrowseColumn.dateTime("installed_on", "执行时间", true),
                        DbBrowseColumn.number("execution_time", "耗时(ms)", true, false),
                        DbBrowseColumn.bool("success", "是否成功")
                ))
                .build();
    }

    private static DbBrowseTable runtimeLogSourceTable() {
        return DbBrowseTable.builder()
                .key("runtime_log_source")
                .schemaName("app")
                .tableName("runtime_log_source")
                .label("运行日志采集源")
                .description("运行日志文件的采集进度：读到哪个文件、偏移量、状态与无效行数；文件名不含目录")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_READ))
                .keyColumns(List.of("source_key"))
                .orderableColumns(Set.of("source_key"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("source_key", "采集源标识", true, true),
                        DbBrowseColumn.text("file_name", "文件名", true, false, 255),
                        DbBrowseColumn.number("byte_offset", "已读偏移", false, false),
                        DbBrowseColumn.number("observed_bytes", "文件大小", false, false),
                        DbBrowseColumn.text("state", "状态", true, false),
                        DbBrowseColumn.text("failure_code", "失败代码", true, false),
                        DbBrowseColumn.number("invalid_lines", "无效行数", true, false),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable availabilityPeriodTable() {
        return DbBrowseTable.builder()
                .key("availability_period")
                .schemaName("app")
                .tableName("system_availability_period")
                .label("系统可用时段")
                .description("按心跳切分的系统可用时段：状态、起止时间与心跳；用于核算停机窗口")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("reason", "原因", true, false, 255),
                        DbBrowseColumn.dateTime("started_at", "开始时间", true),
                        DbBrowseColumn.dateTime("ended_at", "结束时间", true),
                        DbBrowseColumn.dateTime("last_heartbeat_at", "最近心跳", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable permissionMatrixSnapshotTable() {
        return DbBrowseTable.builder()
                .key("permission_matrix_snapshot")
                .schemaName("app")
                .tableName("permission_matrix_snapshot")
                .label("权限矩阵快照")
                .description("角色与权限矩阵的历史快照，用于核对「某次授权变更前后差了什么」；矩阵内容在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.SYSTEM_MANAGE))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(50)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("version", "版本标识", true, false, 128),
                        DbBrowseColumn.text("matrix_json", "矩阵内容(JSON)", false, false, 2000),
                        DbBrowseColumn.text("created_by", "创建人", true, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true)
                ))
                .build();
    }
}
