package com.zjcxph.imgapi.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class ErrorReference {
    public static final String REQUEST_ATTRIBUTE = "mrr.errorId";
    public static final String RESPONSE_HEADER = "X-Error-Id";

    private ErrorReference() {
    }

    public static String ensure(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing != null && !existing.toString().isBlank()) {
            return existing.toString();
        }
        String errorId = RuntimeErrorSanitizer.newErrorId();
        request.setAttribute(REQUEST_ATTRIBUTE, errorId);
        return errorId;
    }
}
