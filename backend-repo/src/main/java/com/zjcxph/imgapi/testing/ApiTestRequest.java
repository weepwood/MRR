package com.zjcxph.imgapi.testing;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class ApiTestRequest {
    @NotBlank
    private String url;
    private String method = "GET";
    private Map<String, String> headers;
    private String body;
    private int timeoutMillis = 15000;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }
}
