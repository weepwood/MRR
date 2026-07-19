package com.zjcxph.imgapi.exception;

import com.zjcxph.imgapi.common.AppErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    /**
     * Legacy numeric response code retained for frontend compatibility.
     */
    private final Integer code;
    private final String errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(String message) {
        this(AppErrorCode.BAD_REQUEST, message);
    }

    public BusinessException(Integer code, String message) {
        this(code, AppErrorCode.fromLegacyCode(code), message);
    }

    public BusinessException(AppErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(AppErrorCode errorCode, String message) {
        this(errorCode.getHttpStatus().value(), errorCode, message);
    }

    private BusinessException(Integer code, AppErrorCode errorCode, String message) {
        super(message);
        this.code = code;
        this.errorCode = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }
}
