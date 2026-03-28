package com.zjcxph.imgapi.monitoring;

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

    public int getIndex() {
        return index;
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean success() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }

    public long latencyMillis() {
        return latencyMillis;
    }

    public void setLatencyMillis(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
