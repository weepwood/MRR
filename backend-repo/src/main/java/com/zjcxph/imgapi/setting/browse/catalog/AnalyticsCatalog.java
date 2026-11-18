package com.zjcxph.imgapi.setting.browse.catalog;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.setting.browse.ColumnType;
import com.zjcxph.imgapi.setting.browse.DbBrowseColumn;
import com.zjcxph.imgapi.setting.browse.DbBrowseTable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 病案智能处理域：OCR、影像智能分类、数据质量。
 *
 * <p>这一组表里没有患者姓名或证件号，但都能通过 {@code scan_id} / {@code archive_id}
 * 定位到具体病案，所以统一要求 {@link Permissions#RECORD_READ}，与已登记的
 * {@code data_quality_issue} 保持同一门槛。</p>
 *
 * <p>刻意不登记的两张表：{@code mr_scan_ocr} 与 {@code mr_scan_ocr_history} 含
 * {@code ocr_text}（OCR 识别出的病历原文），直接可查等于再开一个患者信息出口，
 * 需要先定「整列剪掉 / 截断多少字」再进清单。</p>
 */
public final class AnalyticsCatalog {

    private AnalyticsCatalog() {
    }

    public static List<Supplier<DbBrowseTable>> entries() {
        return List.of(
                AnalyticsCatalog::ocrLexiconSetTable,
                AnalyticsCatalog::ocrLexiconEntryTable,
                AnalyticsCatalog::ocrBatchTable,
                AnalyticsCatalog::classificationRuleSetTable,
                AnalyticsCatalog::classificationRuleEntryTable,
                AnalyticsCatalog::imageTypeAuditTable,
                AnalyticsCatalog::imageClassificationSuggestionTable,
                AnalyticsCatalog::smartClassificationJobTable,
                AnalyticsCatalog::smartClassificationJobItemTable,
                AnalyticsCatalog::dataQualityRunTable,
                AnalyticsCatalog::dataQualityCheckResultTable
        );
    }

    private static DbBrowseTable ocrLexiconSetTable() {
        return DbBrowseTable.builder()
                .key("ocr_lexicon_set")
                .schemaName("app")
                .tableName("mr_ocr_lexicon_set")
                .label("OCR 词典集")
                .description("OCR 纠错词典的版本集：状态、校验和与发布信息；词条见「OCR 词典词条」")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("name", "词典集名称", true, false),
                        DbBrowseColumn.text("version", "版本", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("checksum", "校验和", false, false, 64),
                        DbBrowseColumn.text("created_by", "创建人", true, false),
                        DbBrowseColumn.text("published_by", "发布人", true, false),
                        DbBrowseColumn.number("created_by_id", "创建人编号", false, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("published_at", "发布时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable ocrLexiconEntryTable() {
        return DbBrowseTable.builder()
                .key("ocr_lexicon_entry")
                .schemaName("app")
                .tableName("mr_ocr_lexicon_entry")
                .label("OCR 词典词条")
                .description("纠错词条：术语、别名与常见 OCR 误识别；用于核对某次识别为什么被纠正成这个词")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("lexicon_set_id", "词典集编号", true, false),
                        DbBrowseColumn.text("term", "术语", true, false, 256),
                        DbBrowseColumn.text("aliases", "别名(JSON)", false, false, 500),
                        DbBrowseColumn.text("common_ocr_errors", "常见误识别(JSON)", false, false, 500),
                        DbBrowseColumn.number("target_type", "目标类型", true, false),
                        DbBrowseColumn.bool("enabled", "是否启用"),
                        DbBrowseColumn.text("remark", "备注", false, false, 500),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable ocrBatchTable() {
        return DbBrowseTable.builder()
                .key("ocr_batch")
                .schemaName("app")
                .tableName("mr_ocr_batch")
                .label("OCR 批处理任务")
                .description("按影像区间批量识别的进度与失败数；配置快照在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("id", "任务编号", true, true),
                        DbBrowseColumn.number("owner_user_id", "发起人编号", false, false),
                        DbBrowseColumn.text("created_by", "发起人", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.number("after_id", "起始影像编号", false, false),
                        DbBrowseColumn.number("upper_id", "截止影像编号", false, false),
                        DbBrowseColumn.number("cursor_id", "游标位置", false, false),
                        DbBrowseColumn.number("max_pages", "最大页数", false, false),
                        DbBrowseColumn.number("dispatched_count", "已派发", true, false),
                        DbBrowseColumn.number("processed_count", "已处理", true, false),
                        DbBrowseColumn.number("success_count", "成功数", true, false),
                        DbBrowseColumn.number("failed_count", "失败数", true, false),
                        DbBrowseColumn.number("auto_applied_count", "自动应用数", false, false),
                        DbBrowseColumn.number("review_required_count", "待复核数", false, false),
                        DbBrowseColumn.text("config_snapshot", "配置快照", false, false, 300),
                        DbBrowseColumn.text("rule_set_version", "规则集版本", true, false),
                        DbBrowseColumn.text("lexicon_version", "词典版本", true, false),
                        DbBrowseColumn.bool("auto_apply", "是否自动应用"),
                        DbBrowseColumn.bool("discovery_done", "发现阶段完成"),
                        DbBrowseColumn.text("reason_code", "原因代码", true, false),
                        DbBrowseColumn.text("policy_checksum", "策略校验和", false, false, 64),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable classificationRuleSetTable() {
        return DbBrowseTable.builder()
                .key("classification_rule_set")
                .schemaName("app")
                .tableName("mr_classification_rule_set")
                .label("分类规则集")
                .description("影像智能分类的规则集版本：状态、校验和与发布信息；规则明细见「分类规则条目」")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("name", "规则集名称", true, false),
                        DbBrowseColumn.text("version", "版本", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("checksum", "校验和", false, false, 64),
                        DbBrowseColumn.text("created_by", "创建人", true, false),
                        DbBrowseColumn.text("published_by", "发布人", true, false),
                        DbBrowseColumn.number("created_by_id", "创建人编号", false, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("published_at", "发布时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable classificationRuleEntryTable() {
        return DbBrowseTable.builder()
                .key("classification_rule_entry")
                .schemaName("app")
                .tableName("mr_classification_rule_entry")
                .label("分类规则条目")
                .description("单条分类规则：关键词、匹配范围/方式、权重与是否反向计分")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("rule_set_id", "规则集编号", true, false),
                        DbBrowseColumn.number("target_type", "目标类型", true, false),
                        DbBrowseColumn.text("keyword", "关键词", true, false, 200),
                        DbBrowseColumn.text("match_scope", "匹配范围", true, false),
                        DbBrowseColumn.text("match_mode", "匹配方式", true, false),
                        DbBrowseColumn.number("weight", "权重", false, false),
                        DbBrowseColumn.bool("negative", "是否反向计分"),
                        DbBrowseColumn.bool("required", "是否必需"),
                        DbBrowseColumn.bool("enabled", "是否启用"),
                        DbBrowseColumn.number("priority", "优先级", true, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable imageTypeAuditTable() {
        return DbBrowseTable.builder()
                .key("image_type_audit")
                .schemaName("app")
                .tableName("mr_image_type_audit")
                .label("影像类型变更审计")
                .description("影像类型被改动或回滚的审计明细：原类型、新类型、执行方式与操作人")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("scan_id", "影像编号", true, false),
                        DbBrowseColumn.number("archive_id", "档案编号", true, false),
                        DbBrowseColumn.number("old_type", "原类型", true, false),
                        DbBrowseColumn.number("suggested_type", "建议类型", true, false),
                        DbBrowseColumn.number("new_type", "新类型", true, false),
                        DbBrowseColumn.text("operation", "操作", true, false),
                        DbBrowseColumn.text("execution_mode", "执行方式", true, false),
                        DbBrowseColumn.text("job_id", "任务编号", true, false),
                        DbBrowseColumn.text("batch_id", "批次编号", true, false),
                        DbBrowseColumn.text("rule_set_version", "规则集版本", true, false),
                        DbBrowseColumn.number("rule_score", "规则得分", false, false),
                        DbBrowseColumn.number("ocr_confidence", "OCR 置信度", false, false),
                        DbBrowseColumn.number("operator_user_id", "操作人编号", false, false),
                        DbBrowseColumn.text("operator_username", "操作账号", true, false),
                        DbBrowseColumn.text("request_id", "请求编号", true, false),
                        DbBrowseColumn.number("type_version", "类型版本", false, false),
                        DbBrowseColumn.text("ocr_result_version", "OCR 结果版本", true, false),
                        DbBrowseColumn.text("policy_checksum", "策略校验和", false, false, 64),
                        DbBrowseColumn.number("reverts_audit_id", "回滚的审计编号", true, false),
                        DbBrowseColumn.dateTime("created_at", "操作时间", true)
                ))
                .build();
    }

    private static DbBrowseTable imageClassificationSuggestionTable() {
        return DbBrowseTable.builder()
                .key("image_classification_suggestion")
                .schemaName("app")
                .tableName("mr_image_classification_suggestion")
                .label("影像分类建议")
                .description("智能分类给出的影像类型建议与复核结果；命中的规则明细在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("scan_id", "影像编号", true, false),
                        DbBrowseColumn.text("job_id", "任务编号", true, false),
                        DbBrowseColumn.number("original_type", "原类型", true, false),
                        DbBrowseColumn.number("suggested_type", "建议类型", true, false),
                        DbBrowseColumn.number("final_type", "最终类型", true, false),
                        DbBrowseColumn.number("rule_score", "规则得分", false, false),
                        DbBrowseColumn.number("ocr_confidence", "OCR 置信度", false, false),
                        DbBrowseColumn.text("matched_rules", "命中规则(JSON)", false, false, 300),
                        DbBrowseColumn.text("excluded_rules", "排除规则(JSON)", false, false, 300),
                        DbBrowseColumn.text("review_status", "复核状态", true, false),
                        DbBrowseColumn.text("reviewed_by", "复核账号", true, false),
                        DbBrowseColumn.dateTime("reviewed_at", "复核时间", true),
                        DbBrowseColumn.text("rule_set_version", "规则集版本", true, false),
                        DbBrowseColumn.text("lexicon_version", "词典版本", true, false),
                        DbBrowseColumn.text("ocr_content_hash", "OCR 内容指纹", false, false, 64),
                        DbBrowseColumn.text("ocr_result_version", "OCR 结果版本", true, false),
                        DbBrowseColumn.number("original_type_version", "原类型版本", false, false),
                        DbBrowseColumn.bool("auto_eligible", "可自动应用"),
                        DbBrowseColumn.text("auto_status", "自动应用状态", true, false),
                        DbBrowseColumn.text("reason_code", "原因代码", true, false),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable smartClassificationJobTable() {
        return DbBrowseTable.builder()
                .key("smart_classification_job")
                .schemaName("app")
                .tableName("mr_smart_classification_job")
                .label("智能分类任务")
                .description("影像智能分类任务的进度与失败数；租约令牌整列不返回，配置快照在服务端截断")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("id", "任务编号", true, true),
                        DbBrowseColumn.number("archive_id", "档案编号", true, false),
                        DbBrowseColumn.text("scope", "任务范围", true, false),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.number("planned_count", "计划数", true, false),
                        DbBrowseColumn.number("processed_count", "已处理", true, false),
                        DbBrowseColumn.number("ocr_success_count", "OCR 成功数", false, false),
                        DbBrowseColumn.number("suggested_count", "给出建议数", true, false),
                        DbBrowseColumn.number("low_confidence_count", "低置信数", false, false),
                        DbBrowseColumn.number("no_match_count", "未命中数", false, false),
                        DbBrowseColumn.number("failed_count", "失败数", true, false),
                        DbBrowseColumn.text("rule_set_version", "规则集版本", true, false),
                        DbBrowseColumn.text("lexicon_version", "词典版本", true, false),
                        DbBrowseColumn.text("created_by", "发起人", true, false),
                        DbBrowseColumn.number("owner_user_id", "发起人编号", false, false),
                        DbBrowseColumn.text("batch_id", "批次编号", true, false),
                        DbBrowseColumn.number("selected_scan_id", "指定影像编号", false, false),
                        DbBrowseColumn.text("config_snapshot", "配置快照", false, false, 300),
                        DbBrowseColumn.text("idempotency_key", "幂等键", false, false, 128),
                        DbBrowseColumn.hidden("lease_token", "任务租约令牌", ColumnType.TEXT),
                        DbBrowseColumn.dateTime("lease_until", "租约到期", true),
                        DbBrowseColumn.number("cursor_id", "游标位置", false, false),
                        DbBrowseColumn.bool("cancel_requested", "请求取消"),
                        DbBrowseColumn.text("error_message", "错误信息", false, false, 500),
                        DbBrowseColumn.dateTime("started_at", "开始时间", true),
                        DbBrowseColumn.dateTime("finished_at", "完成时间", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable smartClassificationJobItemTable() {
        return DbBrowseTable.builder()
                .key("smart_classification_job_item")
                .schemaName("app")
                .tableName("mr_smart_classification_job_item")
                .label("智能分类任务明细")
                .description("任务内单个影像的处理结果；复合主键（任务编号 + 影像编号），游标按两列排序")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("job_id", "scan_id"))
                .orderableColumns(Set.of("job_id", "scan_id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.text("job_id", "任务编号", true, true),
                        DbBrowseColumn.number("scan_id", "影像编号", true, true),
                        DbBrowseColumn.number("original_type", "原类型", true, false),
                        DbBrowseColumn.text("content_hash", "内容指纹", false, false, 64),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("ocr_status", "OCR 状态", true, false),
                        DbBrowseColumn.number("suggested_type", "建议类型", true, false),
                        DbBrowseColumn.number("rule_score", "规则得分", false, false),
                        DbBrowseColumn.number("ocr_confidence", "OCR 置信度", false, false),
                        DbBrowseColumn.text("failure_code", "失败代码", true, false),
                        DbBrowseColumn.text("failure_message", "失败原因", false, false, 500),
                        DbBrowseColumn.number("attempts", "尝试次数", true, false),
                        DbBrowseColumn.text("ocr_result_version", "OCR 结果版本", true, false),
                        DbBrowseColumn.number("original_type_version", "原类型版本", false, false),
                        DbBrowseColumn.dateTime("processed_at", "处理时间", true),
                        DbBrowseColumn.dateTime("next_attempt_at", "下次重试时间", true),
                        DbBrowseColumn.dateTime("created_at", "创建时间", true),
                        DbBrowseColumn.dateTime("updated_at", "更新时间", true)
                ))
                .build();
    }

    private static DbBrowseTable dataQualityRunTable() {
        return DbBrowseTable.builder()
                .key("data_quality_run")
                .schemaName("app")
                .tableName("mrr_data_quality_run")
                .label("数据质量检查运行")
                .description("每轮数据质量检查的结果汇总；缺陷明细见「数据质量缺陷」")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.text("status", "状态", true, false),
                        DbBrowseColumn.text("triggered_by", "触发方式", true, false),
                        DbBrowseColumn.number("check_count", "检查项数", true, false),
                        DbBrowseColumn.number("total_issues", "问题总数", true, false),
                        DbBrowseColumn.number("critical_count", "严重数", true, false),
                        DbBrowseColumn.number("warning_count", "警告数", true, false),
                        DbBrowseColumn.text("error_message", "错误信息", false, false, 500),
                        DbBrowseColumn.dateTime("started_at", "开始时间", true),
                        DbBrowseColumn.dateTime("completed_at", "完成时间", true)
                ))
                .build();
    }

    private static DbBrowseTable dataQualityCheckResultTable() {
        return DbBrowseTable.builder()
                .key("data_quality_check")
                .schemaName("app")
                .tableName("mrr_data_quality_check_result")
                .label("数据质量检查项结果")
                .description("单次运行中每个检查项的抽样数与问题数；用于定位是哪条规则出了多少问题")
                .enabled(true)
                .requiredPermissions(List.of(Permissions.RECORD_READ))
                .keyColumns(List.of("id"))
                .orderableColumns(Set.of("id"))
                .maxPageSize(100)
                .requireFilter(false)
                .estimateRowCount(true)
                .personalData(false)
                .columns(List.of(
                        DbBrowseColumn.number("id", "主键", false, true),
                        DbBrowseColumn.number("run_id", "运行编号", true, false),
                        DbBrowseColumn.text("check_code", "检查项代码", true, false),
                        DbBrowseColumn.text("check_name", "检查项名称", true, false, 160),
                        DbBrowseColumn.text("severity", "严重级别", true, false),
                        DbBrowseColumn.number("issue_count", "问题数", true, false),
                        DbBrowseColumn.number("sampled_count", "抽样数", false, false),
                        DbBrowseColumn.dateTime("checked_at", "检查时间", true)
                ))
                .build();
    }
}
