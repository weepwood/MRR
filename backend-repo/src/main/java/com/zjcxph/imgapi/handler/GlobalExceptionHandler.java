package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.AppErrorCode;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_CODE_HEADER = "X-Error-Code";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException exception,
                                                                 HttpServletResponse response) {
        response.setHeader(ERROR_CODE_HEADER, exception.getErrorCode());
        withErrorCode(exception.getErrorCode(), () -> logger.warn(
                "Business exception: errorCode={}, httpStatus={}, message={}",
                exception.getErrorCode(), exception.getHttpStatus().value(), exception.getMessage()));
        Result<Void> result = Result.<Void>fail(exception.getCode(), exception.getMessage())
                .errorCode(exception.getErrorCode());
        return ResponseEntity.status(exception.getHttpStatus()).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletResponse response) {
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "校验失败",
                        (left, right) -> left));
        AppErrorCode error = AppErrorCode.VALIDATION_FAILED;
        response.setHeader(ERROR_CODE_HEADER, error.getCode());
        Result<Map<String, String>> result = Result.<Map<String, String>>fail(error)
                .data(errors);
        return ResponseEntity.status(error.getHttpStatus()).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException exception,
                                                                       HttpServletResponse response) {
        AppErrorCode error = AppErrorCode.BAD_REQUEST;
        response.setHeader(ERROR_CODE_HEADER, error.getCode());
        withErrorCode(error.getCode(), () -> logger.warn("Invalid request: {}", exception.getMessage()));
        return ResponseEntity.status(error.getHttpStatus())
                .body(Result.fail(error, safeClientMessage(exception.getMessage(), error.getDefaultMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception,
                                                        HttpServletResponse response) {
        AppErrorCode error = AppErrorCode.INTERNAL_ERROR;
        response.setHeader(ERROR_CODE_HEADER, error.getCode());
        withErrorCode(error.getCode(), () -> logger.error("Unhandled exception", exception));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(error));
    }

    private String safeClientMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        String lower = message.toLowerCase();
        if (lower.contains("sql") || lower.contains("jdbc") || lower.contains("postgres")
                || lower.contains("java.") || lower.contains("\\") || lower.contains("/var/")) {
            return fallback;
        }
        return message.length() > 300 ? fallback : message;
    }

    private void withErrorCode(String errorCode, Runnable action) {
        String previous = MDC.get("errorCode");
        try {
            MDC.put("errorCode", errorCode);
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove("errorCode");
            } else {
                MDC.put("errorCode", previous);
            }
        }
    }
}
