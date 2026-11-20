package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import java.util.*;

/** 深入诊断只读、受权限控制；每次探测最多51条，超时表示未知，不能冒充无数据。 */
@Service
public class QueryDiagnosticsService {
    private final JdbcTemplate jdbc;
    private final CacheManager cacheManager;
    public QueryDiagnosticsService(JdbcTemplate jdbc, CacheManager cacheManager) { this.jdbc = jdbc; this.cacheManager = cacheManager; }

    public Map<String, Object> diagnose(String bah, String sjh) { return diagnose(bah, sjh, null); }

    public Map<String, Object> diagnose(String bah, String sjh, String requestId) {
        String rawBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String rawSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (rawBah.length() > 64 || rawSjh.length() > 64 || rawBah.isEmpty() && rawSjh.isEmpty()) throw new BusinessException(400, "请输入有效的病案号或上架号");
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean useSjh = rawBah.isEmpty() || MedicalRecordCodeUtils.requiresSjhForBah(rawBah);
        if (useSjh && rawSjh.isEmpty()) {
            steps.add(step("INPUT", "NEEDS_SJH", "该病案号需要上架号才能唯一定位", Map.of(), 0));
            return Map.of("steps", steps, "readOnly", true);
        }
        String code = useSjh ? rawSjh : rawBah;
        String key = MedicalRecordCodeUtils.toSearchTerm(code);
        String column = useSjh ? "sjh" : "bah";
        steps.add(step("INPUT", "OK", useSjh ? "按上架号查询；原编号未改写" : "按病案号查询；原编号未改写", Map.of(), 0));
        var user = com.zjcxph.imgapi.utils.AuthContext.getCurrentUser();
        if (user != null && user.hasPermission("log:read") && requestId != null && requestId.matches("[a-fA-F0-9]{16}")) {
            probe(steps, "REQUEST_RECEIVED", "已匹配原请求访问日志", "SELECT access_time,response_status,execute_time FROM app.access_log WHERE request_id=? LIMIT 2", List.of(requestId));
        } else {
            steps.add(step("REQUEST_RECEIVED", "NOT_CHECKED", "需要日志查看权限及有效追踪编号；可先核对浏览器网络请求", Map.of(), 0));
        }
        probe(steps, "DATABASE", "数据库连接及默认 schema", "SELECT current_schema()='app' AS expected_schema", List.of());
        probe(steps, "ARCHIVE_EXACT", "主档原值精确匹配", "SELECT id FROM app.mr_archive WHERE " + column + "=? LIMIT 2", List.of(code));
        probe(steps, "ARCHIVE_COMPAT", "主档比较键匹配；精确匹配优先", "SELECT id FROM app.mr_archive WHERE app.numeric_code_key(" + column + ")=? LIMIT 2", List.of(key));
        probe(steps, "SCAN_EXACT", "扫描原值记录；0 为无效，NULL 状态需核实", "SELECT id,archive_id,uploadflag FROM app.mr_scan WHERE " + column + "=? ORDER BY id DESC LIMIT 51", List.of(code));
        probe(steps, "SCAN_COMPAT", "兼容扫描候选；无索引或超时时结果为未知", "SELECT id,archive_id,uploadflag FROM app.mr_scan WHERE app.numeric_code_key(" + column + ")=? LIMIT 51", List.of(key));
        String cacheKey = useSjh ? "::" + rawSjh + ":" + key : rawBah + ":" + key + "::";
        var cache = cacheManager.getCache("scanLookupByCode");
        steps.add(step("CACHE", "INFO", "存在缓存不代表数据库当前状态；以直接探测为准", Map.of("hit", cache != null && cache.get(cacheKey) != null), 0));
        steps.add(step("CLIENT", "CLIENT_CONFIRMATION_REQUIRED", "核对原请求响应结构、当前影像分类和最新搜索结果；文件来源请使用影像来源诊断", Map.of(), 0));
        return Map.of("steps", steps, "readOnly", true, "scope", "每个探测步骤最多取 51 条样本。");
    }

    private void probe(List<Map<String, Object>> steps, String code, String message, String sql, List<Object> args) {
        long started = System.nanoTime();
        try {
            List<Map<String, Object>> rows = jdbc.query(sql, statement -> {
                statement.setQueryTimeout(2);
                for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
            }, (rs, n) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                var metadata = rs.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i), rs.getObject(i));
                return row;
            });
            String status = "OK";
            String summary = message;
            if (rows.isEmpty()) {
                status = "NO_SAMPLE";
                summary = "REQUEST_RECEIVED".equals(code)
                        ? "当前未查到访问日志。请核对浏览器或 Nginx 请求记录，并考虑日志写入延迟。"
                        : "本次探测未返回匹配样本";
            } else if ("DATABASE".equals(code) && !Boolean.TRUE.equals(rows.getFirst().get("expected_schema"))) {
                status = "MISMATCH";
                summary = "当前默认 Schema 与预期不一致，请检查连接和搜索路径";
            } else if (code.startsWith("ARCHIVE_") && rows.size() >= 2) {
                status = "AMBIGUOUS";
                summary = "ARCHIVE_COMPAT".equals(code)
                        ? "比较键有多个兼容候选。精确匹配优先，需要兼容匹配时应核对原始编号。"
                        : "存在多个精确候选，请核对原始上架号与主档";
            }
            int sampleLimit = code.startsWith("ARCHIVE_") || "REQUEST_RECEIVED".equals(code) ? 2 : 51;
            steps.add(step(code, status, summary, Map.of(
                    "samples", rows, "sampledCount", rows.size(), "truncated", rows.size() >= sampleLimit
            ), (System.nanoTime() - started) / 1000000));
        } catch (org.springframework.dao.DataAccessException e) {
            steps.add(step(code, "UNKNOWN", "探测失败或超时；不能据此判定记录不存在", Map.of(), (System.nanoTime() - started) / 1000000));
        }
    }
    private static Map<String, Object> step(String code, String status, String message, Object details, long duration) {
        return Map.of("code", code, "status", status, "message", message, "details", details, "durationMs", duration);
    }
}
