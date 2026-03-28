package com.zjcxph.imgapi.monitoring;

public record PressureTestSample(
        int index,
        int statusCode,
        boolean success,
        long latencyMillis,
        String errorMessage
) {
}
