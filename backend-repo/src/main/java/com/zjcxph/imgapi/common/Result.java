package com.zjcxph.imgapi.common;

import lombok.Data;
import org.slf4j.MDC;

import java.time.LocalDateTime;

/**
 * Unified API response. Numeric code is retained for compatibility while errorCode is the stable machine-readable code.
 */
@Data
public class Result<T> {
    private Integer code;
    private String errorCode;
    private String message;
    private T data;
    private String requestId;
    private String traceId;
    private final LocalDateTime timestamp;

    public Result() {
        this.timestamp = LocalDateTime.now();
        applyCorrelation();
    }

    public Result(Integer code, String message, T data) {
        this(code, null, message, data);
    }

    public Result(Integer code, String errorCode, String message, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
        applyCorrelation();
    }

    private void applyCorrelation() {
        this.requestId = firstNonBlank(MDC.get("requestId"), MDC.get("traceId"));
        this.traceId = firstNonBlank(MDC.get("traceId"), this.requestId);
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> successWithData(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> fail() {
        return fail(AppErrorCode.BAD_REQUEST, AppErrorCode.BAD_REQUEST.getDefaultMessage());
    }

    public static <T> Result<T> fail(String message) {
        return fail(AppErrorCode.BAD_REQUEST, message);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        AppErrorCode error = AppErrorCode.fromLegacyCode(code);
        return new Result<>(code, error.getCode(), message, null);
    }

    public static <T> Result<T> fail(AppErrorCode error) {
        return fail(error, error.getDefaultMessage());
    }

    public static <T> Result<T> fail(AppErrorCode error, String message) {
        return new Result<>(error.getHttpStatus().value(), error.getCode(), message, null);
    }

    public static <T> Result<T> unauthorized(String message) {
        return fail(AppErrorCode.UNAUTHORIZED, message);
    }

    public static <T> Result<T> forbidden(String message) {
        return fail(AppErrorCode.FORBIDDEN, message);
    }

    public static <T> Result<T> notFound(String message) {
        return fail(AppErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static <T> Result<T> error(String message) {
        return fail(AppErrorCode.INTERNAL_ERROR, message);
    }

    public Result<T> code(Integer code) {
        this.code = code;
        return this;
    }

    public Result<T> errorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public Result<T> message(String message) {
        this.message = message;
        return this;
    }

    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode() == (this.code != null ? this.code : -1);
    }

    public boolean isFail() {
        return !isSuccess();
    }
}
