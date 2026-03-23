package com.zjcxph.imgapi.pojo;

import java.util.Date;

public class Log {
    private Long id;
    private String clientIp;
    private String requestUri;
    private String method;
    private String userAgent;
    private Date accessTime;
    private String queryString;
    private String requestBody;
    private String responseStatus;
    private Long executeTime;
    private String referer;

    public Log() {
    }

    public Log(String clientIp, String requestUri, String method, String userAgent, Date accessTime, String queryString, String requestBody) {
        this.clientIp = clientIp;
        this.requestUri = requestUri;
        this.method = method;
        this.userAgent = userAgent;
        this.accessTime = accessTime;
        this.queryString = queryString;
        this.requestBody = requestBody;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Date getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(Date accessTime) {
        this.accessTime = accessTime;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public Long getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(Long executeTime) {
        this.executeTime = executeTime;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    @Override
    public String toString() {
        return "Log{" +
                "id=" + id +
                ", clientIp='" + clientIp + '\'' +
                ", requestUri='" + requestUri + '\'' +
                ", method='" + method + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", accessTime=" + accessTime +
                ", queryString='" + queryString + '\'' +
                ", requestBody='" + requestBody + '\'' +
                ", responseStatus='" + responseStatus + '\'' +
                ", executeTime=" + executeTime +
                ", referer='" + referer + '\'' +
                '}';
    }
}