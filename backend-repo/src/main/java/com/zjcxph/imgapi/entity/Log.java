package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Log {
    private Long id;
    private String username;
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

    @Override
    public String toString() {
        return "Log{" +
                "id=" + id +
                ", username='" + username + '\'' +
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