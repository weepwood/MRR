package com.zjcxph.imgapi.ocr;

/** Only stable safe codes may leave the engine boundary. Never attach process output/paths. */
public final class OcrFailure extends RuntimeException {
    private final String code;
    public OcrFailure(String code) { super(code); this.code = code; }
    public String code() { return code; }
    public boolean retryable() { return java.util.Set.of("ENGINE_EXIT", "TIMEOUT", "SOURCE_UNAVAILABLE").contains(code); }
}
