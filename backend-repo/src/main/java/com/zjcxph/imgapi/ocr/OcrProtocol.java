package com.zjcxph.imgapi.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class OcrProtocol {
    public static final ObjectMapper JSON = new ObjectMapper();
    public record Line(String text, double confidence, List<Double> box) {}
    public record Result(String id, int width, int height, List<Line> lines, String engineVersion) {
        public String text() { return String.join("\n", lines.stream().map(Line::text).toList()); }
        public double confidence() {
            long count = lines.stream().mapToLong(l -> Math.max(1, l.text().codePointCount(0, l.text().length()))).sum();
            return count == 0 ? 0 : lines.stream().mapToDouble(l -> l.confidence()
                    * Math.max(1, l.text().codePointCount(0, l.text().length()))).sum() / count;
        }
    }
    private OcrProtocol() {}

    public static String readLine(InputStream input, long limit) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (long n = 0; n <= limit; n++) {
                int b = input.read();
                if (b < 0) throw new OcrFailure("ENGINE_EXIT");
                if (b == '\n') return output.toString(StandardCharsets.UTF_8);
                if (n == limit) throw new OcrFailure("OUTPUT_LIMIT");
                output.write(b);
            }
        } catch (java.io.IOException e) { throw new OcrFailure("ENGINE_EXIT"); }
        throw new OcrFailure("OUTPUT_LIMIT");
    }

    public static Result parse(String json, String id) {
        try {
            var root = JSON.readTree(json);
            if (root.hasNonNull("error")) {
                String code = root.path("error").asText();
                throw new OcrFailure(java.util.Set.of("INVALID_IMAGE", "PIXEL_LIMIT", "MODEL_INVALID").contains(code)
                        ? code : "ENGINE_EXIT");
            }
            Result result = JSON.treeToValue(root, Result.class);
            if (!id.equals(result.id()) || result.width() < 1 || result.height() < 1
                    || (long) result.width() * result.height() > 40_000_000 || result.lines() == null
                    || result.lines().size() > 10000) throw new OcrFailure("INVALID_RESPONSE");
            for (Line line : result.lines()) {
                if (line.text() == null || line.text().length() > 10000 || !Double.isFinite(line.confidence())
                        || line.confidence() < 0 || line.confidence() > 1 || line.box() == null || line.box().size() != 4)
                    throw new OcrFailure("INVALID_RESPONSE");
                for (int i = 0; i < 4; i++) {
                    Double p = line.box().get(i);
                    if (p == null || !Double.isFinite(p) || p < 0 || p > (i % 2 == 0 ? result.width() : result.height()))
                        throw new OcrFailure("INVALID_RESPONSE");
                }
                if (line.box().get(0) > line.box().get(2) || line.box().get(1) > line.box().get(3))
                    throw new OcrFailure("INVALID_RESPONSE");
            }
            return result;
        } catch (OcrFailure e) { throw e; }
        catch (Exception e) { throw new OcrFailure("INVALID_RESPONSE"); }
    }
}
