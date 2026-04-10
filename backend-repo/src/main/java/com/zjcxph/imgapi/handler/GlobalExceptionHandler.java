package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.zjcxph.imgapi.exception.BusinessException;

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
     * 处理方法参数校验异常。
     * <p>
     * 当请求体中的参数校验失败时（如 @Valid 注解校验不通过），Spring 会抛出此异常。
     * 该方法会提取所有字段错误信息，构建成字段名到错误消息的映射返回给客户端。
     * </p>
     *
     * @param e 方法参数校验异常对象，包含校验失败的字段信息和错误消息
     * @return ResponseEntity<Result<Map<String, String>>> HTTP 400 响应，包含：
     *         - 状态码：400 (Bad Request)
     *         - 消息："参数校验失败"
     *         - 数据：Map 结构，key 为字段名，value 为对应的错误提示消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException e) {
        // 提取字段校验错误信息，构建字段名到错误消息的映射
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
        logger.error("未处理异常", e);
        // 生产环境不暴露内部错误信息
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务器内部错误，请联系管理员"));
    }

    /**
     * 将业务错误码映射为 HTTP 状态码。
     * <p>
     * 该方法用于将自定义的业务错误码转换为标准的 HTTP 状态码，以便在响应中使用。
     * 支持的映射关系：400->BAD_REQUEST, 401->UNAUTHORIZED, 403->FORBIDDEN,
     * 404->NOT_FOUND，其他所有代码均映射为 INTERNAL_SERVER_ERROR。
     * </p>
     *
     * @param code 业务错误码
     * @return HttpStatus 对应的 HTTP 状态码枚举值
     */
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