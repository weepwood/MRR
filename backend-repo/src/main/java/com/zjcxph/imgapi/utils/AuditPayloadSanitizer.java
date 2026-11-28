package com.zjcxph.imgapi.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;

/** 审计只允许已知普通参数明文留存；未知字段及解析失败不得退回原文。 */
public final class AuditPayloadSanitizer {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> SAFE = Set.of("page", "size", "limit", "offset", "sortby", "sortorder",
            "type", "btype", "status", "format", "scope", "startdate", "enddate", "force", "enabled");
    private static final Set<String> CODES = Set.of("bah", "sjh", "brxh", "archiveid", "imageid", "scanid");
    private static volatile String configuredKey;
    public static void configureKey(String key) { configuredKey = key; }
    public static boolean hmacConfigured() { String key = activeKey(); return key != null && key.length() >= 32; }
    private static String activeKey() { return configuredKey == null ? System.getenv("MRR_AUDIT_HMAC_KEY") : configuredKey; }
    private static boolean knownName(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_\\[\\]]", "");
        return SAFE.contains(key) || CODES.contains(key) || Set.of("patient", "patients", "data", "items", "records", "name", "idcard", "token", "password", "secret", "authorization").contains(key);
    }
    private AuditPayloadSanitizer() { }

    public static String searchKey(String value) {
        if (value == null || !value.trim().matches("[0-9]{1,18}")) return null;
        String protectedValue = protectedValue(value.trim());
        return protectedValue.startsWith("hmac:") ? protectedValue : null;
    }

    public static String protectedTarget(String target) {
        if (target == null) return null;
        if (isProtected(target)) return target;
        if (target.contains(";")) {
            String[] tokens = target.split(";", -1);
            if (tokens.length == 2 && isProtected(tokens[0]) && isProtected(tokens[1])) return target;
        }
        if (target.startsWith("sjh:")) return protectedValue(target.substring(4));
        String[] parts = target.split(":", 2);
        if (parts.length == 2) {
            String first = protectedValue(parts[0]), second = protectedValue(parts[1]);
            return first.startsWith("hmac:") || second.startsWith("hmac:") ? first + ";" + second : "[REDACTED]";
        }
        return protectedValue(target);
    }

    private static boolean isProtected(String value) {
        return value != null && (value.equals("[REDACTED]") || value.matches("hmac:v1:[0-9a-f]{64}"));
    }

    private static String protectedCode(String value) {
        return isProtected(value) ? value : protectedValue(value);
    }

    /** Parameter names are not proof that their values are non-sensitive. */
    private static boolean safeValue(String name, String value) {
        if (value == null) return false;
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        return switch (name) {
            case "page", "size", "limit", "offset" -> value.matches("[0-9]{1,7}");
            case "force", "enabled" -> Set.of("TRUE", "FALSE").contains(upper);
            case "format" -> Set.of("ZIP", "PDF", "CSV", "XLSX", "JSON").contains(upper);
            case "sortorder" -> Set.of("ASC", "DESC", "ASCENDING", "DESCENDING").contains(upper);
            case "sortby" -> Set.of("ID", "CREATEDAT", "UPDATEDAT", "ACCESSTIME", "STARTEDAT",
                    "COMPLETEDAT", "BAH", "SJH", "PAGES", "STATUS", "CREATED_AT", "UPDATED_AT", "ACCESS_TIME").contains(upper);
            case "status" -> Set.of("ACTIVE", "DISABLED", "PENDING", "RUNNING", "SUCCESS", "FAILED",
                    "PROCESSING", "CANCELLED", "CANCEL_REQUESTED", "EXPIRED", "NORMAL", "DELETED",
                    "APPROVED", "REJECTED", "VERIFIED", "APPLIED", "PAUSED").contains(upper);
            case "scope" -> Set.of("ALL", "SELECTION", "SELECTED", "ARCHIVE", "PAGE", "CURRENT", "WHOLE_ARCHIVE", "SELECTED_IMAGES").contains(upper);
            case "startdate", "enddate" -> {
                try { yield value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") && java.time.LocalDate.parse(value) != null; }
                catch (java.time.DateTimeException invalid) { yield false; }
            }
            // Free-text type/btype values are not an established closed enumeration.
            default -> false;
        };
    }

    public static String protectedValue(String value) {
        String key = activeKey();
        if (value == null || value.isBlank() || key == null || key.length() < 32) return "[REDACTED]";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "hmac:v1:" + HexFormat.of().formatHex(mac.doFinal(MedicalRecordCodeUtils.toSearchTerm(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Audit HMAC unavailable", e);
        }
    }

    public static String json(String body) {
        if (body == null || body.isBlank()) return "";
        if (body.length() > 65536) return "[OVERSIZED PAYLOAD OMITTED]";
        try { return JSON.writeValueAsString(walk(JSON.readTree(body), "", 0)); }
        catch (Exception e) { return "[UNPARSEABLE PAYLOAD OMITTED]"; }
    }

    private static JsonNode walk(JsonNode node, String key, int depth) {
        if (depth > 16) return JSON.getNodeFactory().textNode("[DEPTH LIMIT]");
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            var fields = node.fields();
            int count = 0;
            while (fields.hasNext() && count++ < 100) {
                var field = fields.next();
                String fieldKey = knownName(field.getKey()) ? field.getKey() : "omittedField";
                result.set(fieldKey, walk(field.getValue(), field.getKey(), depth + 1));
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            for (int i = 0; i < Math.min(node.size(), 100); i++) result.add(walk(node.get(i), key, depth + 1));
            return result;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_\\[\\]]", "");
        if (SAFE.contains(normalized) && node.isValueNode() && safeValue(normalized, node.asText())) return node;
        return JSON.getNodeFactory().textNode(CODES.contains(normalized) ? protectedCode(node.asText()) : "[REDACTED]");
    }

    public static String query(String query) {
        if (query == null || query.isBlank()) return query;
        if (query.length() > 16384) return "[OVERSIZED QUERY OMITTED]";
        try {
            StringBuilder result = new StringBuilder();
            for (String pair : query.split("&", -1)) {
                String[] parts = pair.split("=", 2);
                String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                String key = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_\\[\\]]", "");
                String safeName = knownName(name) ? name : "omittedField";
                String safeValue = SAFE.contains(key) && safeValue(key, value)
                        ? value : CODES.contains(key) ? protectedCode(value) : "[REDACTED]";
                if (!result.isEmpty()) result.append('&');
                result.append(safeName).append('=').append(URLEncoder.encode(safeValue, StandardCharsets.UTF_8));
            }
            return result.toString();
        } catch (IllegalArgumentException e) { return "[UNPARSEABLE QUERY OMITTED]"; }
    }
}
