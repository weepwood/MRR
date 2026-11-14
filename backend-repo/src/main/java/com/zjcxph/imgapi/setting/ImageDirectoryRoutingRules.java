package com.zjcxph.imgapi.setting;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.exception.BusinessException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 有界、不可变的目录路由值；不持有配置、缓存或网络客户端。 */
public record ImageDirectoryRoutingRules(List<Rule> rules, boolean inherited) {
    public static final String KEY = "imageDirectoryRoutingRules";
    public static final int MAX_RULES = 200;
    public static final int MAX_LENGTH = 32768;
    private static final Set<String> NODES = Set.of("fallback", "default", "ba01", "ba02", "ba03");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<Rule> LEGACY = legacyRules();

    public record Rule(String type, String value, String node) { }

    public ImageDirectoryRoutingRules {
        rules = List.copyOf(rules);
    }

    public static ImageDirectoryRoutingRules parse(String raw) {
        if (raw != null && raw.length() > MAX_LENGTH) {
            throw invalid("长度不得超过 32768 个字符");
        }
        if (raw == null || raw.isBlank()) {
            return new ImageDirectoryRoutingRules(LEGACY, true);
        }
        try {
            JsonNode root = JSON.readTree(raw);
            if (root == null || !root.isArray() || root.size() > MAX_RULES) {
                throw invalid("必须是最多 200 条的规则数组");
            }
            List<Rule> rules = new ArrayList<>();
            Set<String> conditions = new HashSet<>();
            for (JsonNode item : root) {
                if (!item.isObject() || item.size() != 3
                        || !item.path("type").isTextual() || !item.path("value").isTextual()
                        || !item.path("node").isTextual()) {
                    throw invalid("每条规则只能包含 type、value、node 三个文本字段");
                }
                Rule rule = new Rule(item.get("type").asText(), item.get("value").asText(), item.get("node").asText());
                validate(rule);
                if (!conditions.add(rule.type() + ":" + rule.value())) {
                    throw invalid("存在重复匹配条件");
                }
                rules.add(rule);
            }
            return new ImageDirectoryRoutingRules(rules, false);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("格式无效，请检查规则内容");
        }
    }

    public String nodeFor(String folder) {
        for (Rule rule : rules) {
            if ("exact".equals(rule.type()) && rule.value().equals(folder)) {
                return rule.node();
            }
        }
        String[] parts = folder.split("\\.");
        String month = parts.length < 2 ? "" : parts[0] + "." + parts[1];
        return rules.stream().filter(rule -> "month".equals(rule.type()) && rule.value().equals(month))
                .map(Rule::node).findFirst().orElse("fallback");
    }

    public static String defaultJson() {
        try {
            return JSON.writeValueAsString(LEGACY);
        } catch (Exception exception) {
            throw new IllegalStateException("内置目录路由无法序列化");
        }
    }

    private static void validate(Rule rule) {
        boolean exact = "exact".equals(rule.type());
        if ((!exact && !"month".equals(rule.type())) || !NODES.contains(rule.node())) {
            throw invalid("匹配方式或目标节点无效");
        }
        String pattern = exact ? "(?:[0-9]{2}|[0-9]{4})\\.[0-9]{2}\\.[0-9]{2}"
                : "(?:[0-9]{2}|[0-9]{4})\\.[0-9]{2}";
        if (!rule.value().matches(pattern)) {
            throw invalid("目录必须保持原始两位或四位年份，使用点分隔的日期或年月");
        }
        String[] parts = rule.value().split("\\.");
        int year = Integer.parseInt(parts[0]);
        // 只用于日历校验，不转换匹配值或实际路径。
        if (parts[0].length() == 2) { year += 2000; }
        try {
            if (year == 0) { throw new DateTimeException("year zero"); }
            LocalDate.of(year, Integer.parseInt(parts[1]), exact ? Integer.parseInt(parts[2]) : 1);
        } catch (DateTimeException exception) {
            throw invalid("日期或年月不存在");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(400, "目录路由规则" + message);
    }

    private static List<Rule> legacyRules() {
        List<Rule> rules = new ArrayList<>();
        for (String date : List.of("2026.06.05", "2026.06.08", "2026.06.09")) {
            rules.add(new Rule("exact", date, "ba03"));
        }
        for (String month : List.of("2025.08", "2025.09", "2025.10", "2025.11", "2025.12",
                "2026.01", "2026.02", "2026.03", "2026.04", "2026.05", "2026.06")) {
            rules.add(new Rule("month", month, "ba02"));
        }
        for (String month : List.of("24.04", "24.05", "24.06", "24.07", "24.08", "24.09",
                "24.10", "24.11", "25.07", "25.08")) {
            rules.add(new Rule("month", month, "ba01"));
        }
        return List.copyOf(rules);
    }
}
