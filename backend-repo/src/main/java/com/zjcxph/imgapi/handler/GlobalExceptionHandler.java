package com.zjcxph.imgapi.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.utils.ErrorReference;
import jakarta.servlet.http.HttpServletRequest;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(ResultCode.resolveHttpStatus(e.getCode()))
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
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        String errorId = ErrorReference.ensure(request);
        MDC.put("errorId", errorId);
        try {
            // 完整异常只进入服务端文件日志和受权限保护的运行错误中心。
            logger.error("未处理异常: errorId={}", errorId, e);
        } finally {
            MDC.remove("errorId");
        }
        String message = "服务器内部错误，请联系管理员（错误编号：" + errorId + "）";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(ErrorReference.RESPONSE_HEADER, errorId)
                .body(Result.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), message));
    }
}
