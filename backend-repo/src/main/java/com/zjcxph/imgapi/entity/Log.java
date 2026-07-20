package com.zjcxph.imgapi.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Log {
    private Long id;
    private String requestId;
    private String username;
    private String clientIp;
    private String requestUri;
    private String endpointTemplate;
    private String method;
    private String userAgent;
    private Date accessTime;
    private String queryString;
    private String requestBody;
    private String responseStatus;
    private Long executeTime;
    private String referer;
    private String errorMessage;
    private String auditAction;
    private String auditTarget;
    private String auditDescription;
    /** 审计查询派生字段，不写入 access_log。 */
    private String bah;
    private String sjh;
    private String patientId;

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
                ", requestId='" + requestId + '\'' +
                ", username='" + username + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", requestUri='" + requestUri + '\'' +
                ", endpointTemplate='" + endpointTemplate + '\'' +
                ", method='" + method + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", accessTime=" + accessTime +
                ", queryString='" + queryString + '\'' +
                ", requestBody='" + requestBody + '\'' +
                ", responseStatus='" + responseStatus + '\'' +
                ", executeTime=" + executeTime +
                ", referer='" + referer + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", auditAction='" + auditAction + '\'' +
                ", auditTarget='" + auditTarget + '\'' +
                ", auditDescription='" + auditDescription + '\'' +
                ", bah='" + bah + '\'' +
                ", sjh='" + sjh + '\'' +
                ", patientId='" + patientId + '\'' +
                '}';
    }
}
