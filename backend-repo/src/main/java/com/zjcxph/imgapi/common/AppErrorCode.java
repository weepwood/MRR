package com.zjcxph.imgapi.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes. HTTP status and business error code are deliberately separated.
 */
@Getter
public enum AppErrorCode {
    VALIDATION_FAILED("MRR-COMMON-4000", HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    BAD_REQUEST("MRR-COMMON-4001", HttpStatus.BAD_REQUEST, "请求参数错误"),
    UNAUTHORIZED("MRR-AUTH-1001", HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN("MRR-PERMISSION-2001", HttpStatus.FORBIDDEN, "权限不足"),
    RESOURCE_NOT_FOUND("MRR-COMMON-4004", HttpStatus.NOT_FOUND, "资源不存在"),
    RATE_LIMITED("MRR-COMMON-4029", HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    EXTERNAL_INTEGRATION_DISABLED("MRR-INTEGRATION-4001", HttpStatus.SERVICE_UNAVAILABLE, "外部系统集成未启用"),
    ARCHIVE_NOT_FOUND("MRR-ARCHIVE-3001", HttpStatus.NOT_FOUND, "未找到对应病案"),
    STORAGE_FAILURE("MRR-STORAGE-6001", HttpStatus.INTERNAL_SERVER_ERROR, "影像存储访问失败"),
    DATABASE_FAILURE("MRR-DATA-5001", HttpStatus.SERVICE_UNAVAILABLE, "数据库服务暂时不可用"),
    AUDIT_UNAVAILABLE("MRR-AUDIT-7001", HttpStatus.SERVICE_UNAVAILABLE, "审计服务暂不可用，请稍后重试"),
    INTERNAL_ERROR("MRR-SYSTEM-9000", HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误，请联系管理员");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    AppErrorCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public static AppErrorCode fromLegacyCode(Integer legacyCode) {
        if (legacyCode == null) {
            return INTERNAL_ERROR;
        }
        return switch (legacyCode) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 429 -> RATE_LIMITED;
            case 503 -> DATABASE_FAILURE;
            case 1001 -> RESOURCE_NOT_FOUND;
            case 1002 -> FORBIDDEN;
            case 1003 -> UNAUTHORIZED;
            case 2001, 2002 -> STORAGE_FAILURE;
            default -> legacyCode >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
        };
    }
}
