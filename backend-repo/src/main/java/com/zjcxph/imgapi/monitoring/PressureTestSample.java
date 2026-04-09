package com.zjcxph.imgapi.monitoring;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PressureTestSample {

    private int index;
    private int statusCode;
    private boolean success;
    private long latencyMillis;
    private String errorMessage;

    public PressureTestSample() {
    }

    public PressureTestSample(int index, int statusCode, boolean success, long latencyMillis, String errorMessage) {
        this.index = index;
        this.statusCode = statusCode;
        this.success = success;
        this.latencyMillis = latencyMillis;
        this.errorMessage = errorMessage;
    }

    public int index() {
        return index;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean success() {
        return success;
    }

    public long latencyMillis() {
        return latencyMillis;
    }

    public String errorMessage() {
        return errorMessage;
    }

}
