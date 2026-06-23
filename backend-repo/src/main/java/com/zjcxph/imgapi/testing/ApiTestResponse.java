package com.zjcxph.imgapi.testing;

import java.util.Map;

public class ApiTestResponse {
    private int statusCode;
    private Map<String, String> responseHeaders;
    private String body;
    private long latencyMs;
    private String error;

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, String> responseHeaders) { this.responseHeaders = responseHeaders; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
