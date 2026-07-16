package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.entity.RecordTypeDefinition;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KeywordClassificationEngine {

    private static final int TITLE_LINE_LIMIT = 6;
    private static final int TITLE_CHAR_LIMIT = 360;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Decision classify(String ocrText, List<RecordTypeDefinition> definitions) {
        String normalized = normalize(ocrText);
        String title = extractTitle(ocrText);
        if (normalized.isBlank()) {
            return Decision.noMatch(title, json(Map.of("reason", "empty_ocr_text")));
        }

        String normalizedTitle = normalize(title);
        List<Candidate> candidates = new ArrayList<>();
        for (RecordTypeDefinition definition : definitions) {
            if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) {
                continue;
            }
            List<String> keywords = splitKeywords(definition.getKeywords());
            if (keywords.isEmpty()) {
                continue;
            }
            if (containsAny(normalized, splitKeywords(definition.getNegativeKeywords()))) {
                continue;
            }

            List<String> titleMatches = matchedKeywords(normalizedTitle, keywords);
            List<String> bodyMatches = matchedKeywords(normalized, keywords);
            if (bodyMatches.isEmpty()) {
                continue;
            }

            double confidence = titleMatches.isEmpty() ? 0.86D : 0.96D;
            confidence += Math.min(0.025D, Math.max(0, bodyMatches.size() - 1) * 0.005D);
            candidates.add(new Candidate(
                    definition,
                    Math.min(confidence, 0.995D),
                    titleMatches,
                    bodyMatches
            ));
        }

        if (candidates.isEmpty()) {
            return Decision.noMatch(title, json(Map.of("reason", "no_keyword_matched")));
        }

        candidates.sort(Comparator
                .comparingDouble(Candidate::confidence).reversed()
                .thenComparing(candidate -> candidate.definition().getSortOrder()));
        Candidate best = candidates.getFirst();
        if (candidates.size() > 1 && Math.abs(best.confidence() - candidates.get(1).confidence()) < 0.0001D) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("reason", "ambiguous_keyword_match");
            evidence.put("candidateTypes", candidates.stream()
                    .limit(3)
                    .map(candidate -> candidate.definition().getBtype())
                    .toList());
            return Decision.noMatch(title, json(evidence));
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("engine", "OCR_KEYWORD");
        evidence.put("typeCode", best.definition().getTypeCode());
        evidence.put("titleMatches", best.titleMatches());
        evidence.put("bodyMatches", best.bodyMatches());
        return new Decision(
                best.definition().getBtype(),
                BigDecimal.valueOf(best.confidence()).setScale(5, RoundingMode.HALF_UP),
                "SUGGESTED",
                title,
                json(evidence)
        );
    }

    private String extractTitle(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder title = new StringBuilder();
        int nonBlankLines = 0;
        for (String line : value.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(trimmed);
            nonBlankLines++;
            if (nonBlankLines >= TITLE_LINE_LIMIT || title.length() >= TITLE_CHAR_LIMIT) {
                break;
            }
        }
        return title.length() <= TITLE_CHAR_LIMIT
                ? title.toString()
                : title.substring(0, TITLE_CHAR_LIMIT);
    }

    private List<String> splitKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String item : value.split("\\|")) {
            String normalized = normalize(item);
            if (!normalized.isBlank()) {
                keywords.add(normalized);
            }
        }
        return keywords;
    }

    private List<String> matchedKeywords(String text, List<String> keywords) {
        if (text.isBlank() || keywords.isEmpty()) {
            return List.of();
        }
        return keywords.stream().filter(text::contains).distinct().toList();
    }

    private boolean containsAny(String text, List<String> keywords) {
        return !matchedKeywords(text, keywords).isEmpty();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public record Decision(
            Integer predictedBtype,
            BigDecimal confidence,
            String state,
            String title,
            String evidence
    ) {
        static Decision noMatch(String title, String evidence) {
            return new Decision(null, null, "NO_MATCH", title, evidence);
        }
    }

    private record Candidate(
            RecordTypeDefinition definition,
            double confidence,
            List<String> titleMatches,
            List<String> bodyMatches
    ) {
    }
}
