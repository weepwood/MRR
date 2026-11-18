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
 * 病案业务数据。
 *
 * <p>这一组全部是个人数据，统一要求 {@link Permissions#RECORD_READ}：能看检索结果才允许看数据行，
 * 与既有 {@code record:read} 的行级语义保持一致。姓名、病案号、上架号、身份证一律在服务端脱敏。</p>
 *
 * <p>大表（{@code mr_patient}、{@code mr_scan}）单页上限收到 50 行：首屏允许直接打开
 * （否则「按病案号找一条记录」也得先凑出过滤条件），但翻页只能沿着主键游标推进，
 * 不会生成大偏移量，也不会把三千万级影像表整段拉进共享连接池。</p>
 *
 * <p>掩码列只允许等值过滤：过滤条件在库内按原值匹配，开放前缀匹配等于给掩码开一条
 * 「逐字符逼近原值」的旁路。病案号、上架号需要精确值才能查，这是有意的取舍。</p>
 */
public final class RecordCatalog {

    private static final Set<FilterOp> CODE_OPS = Set.of(FilterOp.EQ);
    private static final Set<FilterOp> NAME_OPS = Set.of(FilterOp.EQ);

    private RecordCatalog() {
    }

    public static List<Supplier<DbBrowseTable>> entries() {
        return List.of(
                RecordCatalog::archiveBoxRecordTable,
                RecordCatalog::patientTable,
                RecordCatalog::archiveTable,
                RecordCatalog::scanTable,
                RecordCatalog::statisticsTable,
                RecordCatalog::dataQualityIssueTable
        );
    }

    private static DbBrowseTable archiveBoxRecordTable() {
        return DbBrowseTable.builder()
                .key("archive_box_record")
                .schemaName("app")
                .tableName("mr_archive_box_record")
                .label("档案袋记录")
                .description("病案档案袋的盒号、应收盒号与核对状态")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.text("box_no", "盒号", true, false),
                        DbBrowseColumn.text("expected_box_no", "应收盒号", false, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("remark", "备注", false, false, 200),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable patientTable() {
        return DbBrowseTable.builder()
                .key("patient")
                .schemaName("app")
                .tableName("mr_patient")
                .label("病案主数据")
                .description("患者姓名、病案号、身份证号与科室床位；姓名与证件号按列脱敏，建议按病案号或姓名过滤")
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
                        DbBrowseColumn.masked("idcard", "身份证号", MaskStrategy.ID_CARD, false, false),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("name", "姓名", MaskStrategy.NAME, true, false, NAME_OPS),
                        DbBrowseColumn.text("department", "科室", true, false),
                        DbBrowseColumn.text("bingqu", "病区", false, false),
                        DbBrowseColumn.text("chuangwei", "床位", false, false),
                        DbBrowseColumn.date("ruyuan", "入院日期", true),
                        DbBrowseColumn.text("admissiontime", "入院时间(原始)", false, false, 60)
                ))
                .build();
    }

    private static DbBrowseTable archiveTable() {
        return DbBrowseTable.builder()
                .key("archive")
                .schemaName("app")
                .tableName("mr_archive")
                .label("病案档案")
                .description("档案主记录：病案号、上架号、患者姓名、归档与出院日期")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("patient_id", "患者编号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("patient_name", "患者姓名", MaskStrategy.NAME, true, false, NAME_OPS),
                        DbBrowseColumn.text("inpatient_department", "住院科室", true, false),
                        DbBrowseColumn.text("device_id", "设备编号", false, false),
                        DbBrowseColumn.text("operator_no", "操作员工号", true, false),
                        DbBrowseColumn.date("archive_date", "归档日期", true),
                        DbBrowseColumn.date("discharge_date", "出院日期", true),
                        DbBrowseColumn.text("archive_type", "档案类型", true, false),
                        DbBrowseColumn.number("page_count", "页数", true, false),
                        DbBrowseColumn.number("source_statistics_id", "来源统计编号", false, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable scanTable() {
        return DbBrowseTable.builder()
                .key("scan")
                .schemaName("app")
                .tableName("mr_scan")
                .label("影像记录")
                .description("影像文件清单（生产三千万级）；默认按主键倒序取最近记录，建议按病案号或上架号过滤")
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
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.text("filename", "文件名", true, false, 120),
                        DbBrowseColumn.number("btype", "业务类型", true, false),
                        DbBrowseColumn.number("pages", "页数", false, false),
                        DbBrowseColumn.text("openerno", "上传人工号", false, false),
                        DbBrowseColumn.text("uploaddate", "上传日期(原始)", false, false, 40),
                        DbBrowseColumn.number("uploadflag", "上传标记", true, false),
                        DbBrowseColumn.masked("folder", "目录", MaskStrategy.PATH, false, false),
                        DbBrowseColumn.hidden("oss_url", "OSS 地址", ColumnType.TEXT),
                        DbBrowseColumn.number("file_size", "文件大小(字节)", false, false),
                        DbBrowseColumn.text("checksum_md5", "MD5", false, false, 64),
                        DbBrowseColumn.text("migration_status", "迁移状态", true, false),
                        DbBrowseColumn.dateTime("migrated_at", "迁移时间", true),
                        DbBrowseColumn.number("archive_id", "档案编号", true, false),
                        DbBrowseColumn.text("source_type", "来源类型", true, false),
                        DbBrowseColumn.text("source_node", "来源节点", false, false),
                        DbBrowseColumn.number("migration_attempts", "迁移尝试次数", false, false),
                        DbBrowseColumn.text("migration_error_code", "迁移错误码", true, false),
                        DbBrowseColumn.number("type_version", "类型版本", false, false)
                ))
                .build();
    }

    private static DbBrowseTable statisticsTable() {
        return DbBrowseTable.builder()
                .key("statistics")
                .schemaName("app")
                .tableName("mr_statistics")
                .label("病案统计")
                .description("导入的病案统计明细：患者姓名、科室、出院日期与页数")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.text("cid", "统计编号", true, false),
                        DbBrowseColumn.text("openerno", "操作员工号", false, false),
                        DbBrowseColumn.text("date", "统计日期", true, false),
                        DbBrowseColumn.text("type", "统计类型", true, false),
                        DbBrowseColumn.number("pages", "页数", false, false),
                        DbBrowseColumn.masked("patientname", "患者姓名", MaskStrategy.NAME, true, false, NAME_OPS),
                        DbBrowseColumn.text("inpatientdepartment", "住院科室", true, false),
                        DbBrowseColumn.masked("patientid", "患者编号", MaskStrategy.CODE, false, false),
                        DbBrowseColumn.text("dischargedate", "出院日期(原始)", false, false, 40),
                        DbBrowseColumn.number("archive_id", "档案编号", true, false)
                ))
                .build();
    }

    private static DbBrowseTable dataQualityIssueTable() {
        return DbBrowseTable.builder()
                .key("data_quality_issue")
                .schemaName("app")
                .tableName("mrr_data_quality_issue")
                .label("数据质量缺陷")
                .description("数据质量检查发现的缺陷明细，按病案号定位；详情超长会在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(true)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("run_id", "检查批次", true, false),
                        DbBrowseColumn.text("check_code", "检查项代码", true, false),
                        DbBrowseColumn.text("check_name", "检查项名称", true, false),
                        DbBrowseColumn.text("severity", "严重级别", true, false),
                        DbBrowseColumn.text("entity_type", "实体类型", true, false),
                        DbBrowseColumn.text("entity_id", "实体编号", false, false),
                        DbBrowseColumn.masked("bah", "病案号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.masked("sjh", "上架号", MaskStrategy.CODE, true, false, CODE_OPS),
                        DbBrowseColumn.text("detail", "缺陷详情", false, false, 500),
                        DbBrowseColumn.dateTime("detected_at", "发现时间", true)
                ))
                .build();
    }
}
