package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(mapToHttpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数、凭据等可预期的客户端输入错误统一返回 400，并保留可直接展示的业务消息。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "请求参数有误"
                : e.getMessage();
        logger.warn("客户端输入异常: msg={}", message);
        return ResponseEntity.badRequest()
                .body(Result.fail(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "校验失败",
                        (a, b) -> a));

        return ResponseEntity.badRequest()
                .body(Result.<Map<String, String>>fail("参数校验失败").data(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 完整异常仅写入服务端日志，响应不得回传数据库、JWT、路径或配置等内部细节。
        logger.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误，请联系管理员"));
    }

    /** 将业务错误码映射为 HTTP 状态码。 */
    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 410 -> HttpStatus.GONE;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
