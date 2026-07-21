package com.zjcxph.imgapi.service.impl;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class DataRelationComparisonUtils {

    private DataRelationComparisonUtils() {
    }

    static Map<String, Object> compareCode(
            String field,
            Object canonicalValue,
            String source,
            Object sourceValue
    ) {
        String canonical = normalizeText(canonicalValue);
        String compared = normalizeText(sourceValue);
        String status;

        if (compared == null) {
            status = "MISSING";
        } else if (canonical == null) {
            status = "NO_CANONICAL";
        } else if (canonical.equals(compared)) {
            status = "EXACT";
        } else if (numericEquivalent(canonical, compared)) {
            status = "FORMAT_ONLY";
        } else {
            status = "CONFLICT";
        }

        return comparison(field, canonical, source, compared, status);
    }

    static Map<String, Object> compareText(
            String field,
            Object canonicalValue,
            String source,
            Object sourceValue
    ) {
        String canonical = normalizeText(canonicalValue);
        String compared = normalizeText(sourceValue);
        String status;

        if (compared == null) {
            status = "MISSING";
        } else if (canonical == null) {
            status = "NO_CANONICAL";
        } else if (canonical.equals(compared)) {
            status = "EXACT";
        } else if (canonical.equalsIgnoreCase(compared)) {
            status = "FORMAT_ONLY";
        } else {
            status = "CONFLICT";
        }

        return comparison(field, canonical, source, compared, status);
    }

    static Map<String, Object> compareNumber(
            String field,
            Object canonicalValue,
            String source,
            Object sourceValue
    ) {
        Long canonical = toLong(canonicalValue);
        Long compared = toLong(sourceValue);
        String status;

        if (compared == null) {
            status = "MISSING";
        } else if (canonical == null) {
            status = "NO_CANONICAL";
        } else if (canonical.equals(compared)) {
            status = "EXACT";
        } else {
            status = "CONFLICT";
        }

        return comparison(field, canonical, source, compared, status);
    }

    static boolean numericEquivalent(String left, String right) {
        if (!isDigits(left) || !isDigits(right)) {
            return false;
        }
        return stripLeadingZeroes(left).equals(stripLeadingZeroes(right));
    }

    static String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    static String normalizeSearchType(String type) {
        String normalized = normalizeText(type);
        return normalized == null ? "BAH" : normalized.toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> comparison(
            String field,
            Object canonical,
            String source,
            Object compared,
            String status
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("canonicalValue", canonical);
        result.put("source", source);
        result.put("sourceValue", compared);
        result.put("status", status);
        return result;
    }

    private static boolean isDigits(String value) {
        return value != null && value.matches("^[0-9]+$");
    }

    private static String stripLeadingZeroes(String value) {
        String stripped = value.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
