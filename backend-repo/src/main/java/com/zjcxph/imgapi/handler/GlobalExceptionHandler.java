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
     *
     * <p>认证服务历史上会使用 {@link IllegalArgumentException} 表示用户名或密码错误。
     * 如果没有单独处理，该异常会落入通用 500 处理器，导致前端错误显示为服务器内部错误。</p>
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

    /**
     * 处理方法参数校验异常。
     * <p>
     * 当请求体中的参数校验失败时（如 @Valid 注解校验不通过），Spring 会抛出此异常。
     * 该方法会提取所有字段错误信息，构建成字段名到错误消息的映射返回给客户端。
     * </p>
     *
     * @param e 方法参数校验异常对象，包含校验失败的字段信息和错误消息
     * @return Result 中包含字段级错误信息
     */
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
    public ResponseEntity<Result<String>> handleException(Exception e) {
        logger.error("未处理异常", e);
        String message = e.getMessage() != null ? e.getMessage() : "服务器内部错误，请联系管理员";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.<String>fail(500, "服务器内部错误，请联系管理员").data(message));
    }

    /** 将业务错误码映射为 HTTP 状态码。 */
    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
